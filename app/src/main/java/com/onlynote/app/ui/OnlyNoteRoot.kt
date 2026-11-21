package com.onlynote.app.ui

import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onlynote.app.R
import com.onlynote.app.data.BackupEntry
import com.onlynote.app.ui.components.BackupDialog
import com.onlynote.app.ui.components.BackupFeedback
import com.onlynote.app.ui.components.ConfirmDialog
import com.onlynote.app.ui.components.WindowScrim
import com.onlynote.app.ui.screens.EditorScreen
import com.onlynote.app.ui.screens.HomeScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Переход: 220 мс на экране, одинаково для любой заметки и в обе стороны.
 * Той же длительностью ходит и страница главного экрана.
 *
 * Длительность хода не зависит от размера заметки: пауза на вёрстку поля —
 * это ожидание перед ходом, а не часть хода, иначе на большой заметке
 * переход был бы вдвое быстрее, чем на маленькой.
 *
 * Compose умножает длительность на системный масштаб анимаций (на телефоне
 * он 0,5×), поэтому длительность делится на этот масштаб — иначе половина
 * замысла теряется.
 */
private const val SLIDE_MS = 220

/** Потолок паузы перед ходом: длиннее ждать нельзя ни на входе, ни на выходе. */
private const val MAX_ENTRY_PAUSE_MS = 400L

private sealed interface Destination {
    data object Home : Destination
    data class Editor(val noteId: Long, val isNew: Boolean) : Destination
}

@Composable
fun OnlyNoteRoot(viewModel: OnlyNoteViewModel = viewModel()) {
    val snapshot by viewModel.snapshot.collectAsState()
    val stack = remember { mutableStateListOf<Destination>(Destination.Home) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Позиции списков и текущий экран живут здесь, а не в HomeScreen: на время
    // перехода дом собирается заново, и без этого возврат показывал бы начало
    // списка и первый экран.
    val workListState = rememberLazyListState()
    val archiveListState = rememberLazyListState()
    // Текущая страница живёт здесь: пока открыта заметка, дом выпадает
    // из композиции, и без этого возврат из архива показывал бы рабочий экран.
    val homePager = rememberPagerState(initialPage = 0) { 2 }

    // 0 — на экране список, 1 — заметка. Пока идёт переход, видны оба слоя:
    // список уезжает влево, заметка входит справа.
    var slideProgress by remember { mutableFloatStateOf(1f) }
    var sliding by remember { mutableStateOf(false) }
    // Поле заметки на 100 000 знаках раскладывается ~150 мс — это больше
    // половины перехода. Ждём, пока оно разместится, и только потом едем:
    // иначе весь ход съедают два первых кадра.
    var editorPlaced by remember { mutableStateOf(false) }
    // Сколько вход простоял до начала хода. Выход ждёт столько же, иначе он
    // выходит короче входа, хотя сам ход в обоих одинаковый.
    var entryPause by remember { mutableLongStateOf(0L) }
    var openStartedAt by remember { mutableLongStateOf(0L) }
    // Системный масштаб анимаций: ход ему подчиняется, поэтому делим на него.
    val animScale = remember(context) {
        Settings.Global
            .getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            .coerceIn(0.05f, 10f)
    }

    var backupOpen by remember { mutableStateOf(false) }
    var autoBackup by remember { mutableStateOf<BackupEntry?>(null) }
    var documentBackups by remember { mutableStateOf<List<BackupEntry>>(emptyList()) }
    var backupFeedback by remember { mutableStateOf(BackupFeedback.None) }
    // Копия, выбранная для импорта: подтверждение живёт отдельно от окна копий,
    // иначе окно остаётся на фоне и вопрос читается как часть списка.
    var importCandidate by remember { mutableStateOf<BackupEntry?>(null) }

    fun reloadBackups() {
        viewModel.loadBackupEntries { auto, documents ->
            autoBackup = auto
            documentBackups = documents
        }
    }

    /** Гонит прогресс к цели; по окончании закрывает за собой. */
    fun slideTo(target: Float, durationMs: Int = SLIDE_MS, onFinished: () -> Unit = {}) {
        scope.launch {
            try {
                animate(
                    initialValue = slideProgress,
                    targetValue = target,
                    animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing),
                ) { value, _ -> slideProgress = value }
            } finally {
                slideProgress = target
                onFinished()
                sliding = false
            }
        }
    }

    /** Ход такой длины, чтобы на экране он занял ровно эти миллисекунды. */
    fun motionFor(visibleMs: Long): Int = (visibleMs / animScale).toInt()

    /**
     * Вход в заметку: список уезжает влево на ширину экрана, заметка входит
     * справа. Сам ход начинается не здесь, а когда поле заметки разместится:
     * раскладка 100 000 знаков идёт ~150 мс и съела бы весь переход.
     */
    fun openEditor(destination: Destination.Editor) {
        if (sliding) return
        slideProgress = 0f
        editorPlaced = false
        entryPause = 0L
        openStartedAt = SystemClock.uptimeMillis()
        stack.add(destination)
        sliding = true
    }

    /**
     * Выход: та же пауза и тот же ход назад, и только потом стек снимает заметку.
     */
    fun closeEditor() {
        if (sliding || stack.size <= 1) return
        sliding = true
        scope.launch {
            if (entryPause > 0) delay(entryPause)
            slideTo(0f, motionFor(SLIDE_MS.toLong())) {
                if (stack.size > 1) stack.removeAt(stack.lastIndex)
            }
        }
    }

    // Страховка: если поле почему-то не разместилось, переход не должен
    // залипнуть и оставить человека с заблокированным экраном.
    LaunchedEffect(sliding, editorPlaced) {
        if (sliding && !editorPlaced && stack.size > 1) {
            delay(600)
            if (sliding && !editorPlaced) {
                editorPlaced = true
                slideTo(1f, motionFor(SLIDE_MS.toLong()))
            }
        }
    }

    BackHandler(enabled = stack.size > 1 && !sliding) { closeEditor() }

    // Дом рисуется всегда, заметка — поверх него. Так экран заметки не
    // пересоздаётся при каждом возврате, и любой будущий переход между ними
    // будет двигать готовые слои, а не собирать второй экран с нуля.
    val editor = stack.lastOrNull() as? Destination.Editor

    // Пустую новую заметку при выходе удаляют, и на миг экран остаётся без
    // неё: слой заметки уже нечего рисовать, а дом ещё скрыт перекрытием —
    // получался чёрный экран. Снимаем такую вершину сразу, как только заметки
    // не стало, не дожидаясь хода.
    LaunchedEffect(editor?.noteId, snapshot.notes.size) {
        val current = stack.lastOrNull() as? Destination.Editor ?: return@LaunchedEffect
        if (snapshot.notes.none { it.id == current.noteId } && !sliding) {
            stack.removeAt(stack.lastIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Дом компонуется, пока заметка закрыта, и ещё на время перехода:
        // без второго слоя двигать нечего. Постоянно держать его под заметкой
        // нельзя — его жесты перехватывают вертикальные свайпы, и поле
        // получало нажатие вместо прокрутки.
        if (editor == null || sliding) HomeScreen(
            modifier = Modifier.graphicsLayer {
                if (editor != null) translationX = -size.width * slideProgress
            },
            workListState = workListState,
            archiveListState = archiveListState,
            pagerState = homePager,
            snapshot = snapshot,
            onOpenNote = { note ->
                openEditor(Destination.Editor(noteId = note.id, isNew = false))
            },
            onDeleteNote = { note -> viewModel.deleteNote(note.id) },
            // Полоска — единственная категория: она и перекладывает заметку
            // на другой главный экран.
            onMoveNote = { note -> viewModel.setArchived(note.id, !note.archived) },
            onOpenBackup = {
                backupFeedback = BackupFeedback.None
                backupOpen = true
                reloadBackups()
            },
            // Заметка появляется на том экране, где её создали.
            onNewNote = { archived ->
                viewModel.createNote(archived) { noteId ->
                    openEditor(Destination.Editor(noteId = noteId, isNew = true))
                }
            },
        )

        if (editor != null) {
            val note = snapshot.notes.firstOrNull { it.id == editor.noteId }
            if (note != null) Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Поле разместилось — можно ехать. До этого кадр занят
                    // раскладкой текста, и движение всё равно не покажется.
                    .onGloballyPositioned {
                        if (sliding && !editorPlaced) {
                            editorPlaced = true
                            // Сколько вход простоял до хода — столько же будет
                            // стоять выход: длительности должны совпадать.
                            entryPause = (SystemClock.uptimeMillis() - openStartedAt)
                                .coerceIn(0L, MAX_ENTRY_PAUSE_MS)
                            slideTo(1f, motionFor(SLIDE_MS.toLong()))
                        }
                    }
                    .graphicsLayer {
                        if (sliding) translationX = size.width * (1f - slideProgress)
                    },
            ) {
                EditorScreen(
                    note = note,
                    isNew = editor.isNew,
                    onBack = { closeEditor() },
                    onSave = { body -> viewModel.saveNote(note.id, body) },
                    onColorChange = { color -> viewModel.setColor(note.id, color) },
                    onDiscardBlank = { viewModel.deleteNote(note.id) },
                )
            }
        }

        // Пока идёт переход, касания не доходят ни до списка, ни до поля:
        // иначе первый же свайп попадёт в уезжающий слой.
        if (sliding) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    },
            )
        }
        // Опыт 4.9: под окнами и меню экран гаснет в ноль.
        WindowScrim(visible = backupOpen || importCandidate != null)
    }

    if (backupOpen) {
        BackupDialog(
            autoEntry = autoBackup,
            documentEntries = documentBackups,
            feedback = backupFeedback,
            onDismiss = { backupOpen = false },
            onExport = {
                viewModel.exportBackup { name ->
                    backupFeedback = if (name != null) {
                        BackupFeedback.Exported
                    } else {
                        BackupFeedback.ExportFailed
                    }
                    reloadBackups()
                }
            },
            onPick = { entry ->
                // Окно копий закрываем до подтверждения: иначе оно висело бы
                // на фоне, и вопрос читался бы как часть списка.
                backupOpen = false
                importCandidate = entry
            },
        )
    }

    importCandidate?.let { entry ->
        // Как окно удаления: в заголовке — имя копии, под ним одно действие.
        ConfirmDialog(
            title = entry.title,
            confirmLabel = stringResource(R.string.action_import),
            destructive = true,
            showDismiss = false,
            onDismiss = { importCandidate = null },
            onConfirm = {
                importCandidate = null
                viewModel.importBackup(entry) { ok ->
                    if (ok) {
                        // Копия встала — выходим на главный экран.
                        backupOpen = false
                        backupFeedback = BackupFeedback.None
                    } else {
                        // Не вышло — возвращаем окно копий: сообщение об ошибке
                        // живёт в нём, а не в пустоте.
                        backupFeedback = BackupFeedback.ImportFailed
                        backupOpen = true
                    }
                    reloadBackups()
                }
            },
        )
    }
}
