@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.onlynote.app.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlynote.app.R
import com.onlynote.app.data.AppSnapshot
import com.onlynote.app.data.Note
import com.onlynote.app.ui.components.ConfirmDialog
import com.onlynote.app.ui.components.WindowScrim
import com.onlynote.app.ui.theme.HOME_SCALE
import com.onlynote.app.ui.theme.skin

/**
 * Один шаг ритма: расстояние от полоски до названия.
 */
private val STEP = 7.5.dp

/**
 * Вертикальный воздух строки.
 *
 * Расстояние между полосками — это высота строки минус сама полоска, и высоту
 * задаёт кегль, а не отступ: при шаге в 7,5 dp зазор выходил 2,9 dp. Владелец
 * попросил вдвое больше, а потом на четверть меньше — отсюда 8,3 dp на сторону
 * и зазор около 4,8 dp на экране.
 */
private val ROW_GAP = 8.3.dp

/**
 * Отступ от бокового края.
 *
 * Такой же, как у текста в самой заметке: список и текст стоят на одной
 * вертикали, и переход в заметку не сдвигает левый край.
 */
private val SIDE = 5.dp

private val STRIP_WIDTH = 3.75.dp
private val STRIP_HEIGHT = 35.dp

/**
 * Ширина зоны, по которой ловится долгое нажатие полоски.
 *
 * Сама полоска — 3,75 dp; попасть в неё пальцем почти невозможно, поэтому
 * зона нажатия шире неё в четырнадцать раз.
 */
private val STRIP_TOUCH_ZONE = 52.dp

/**
 * Кегль названия. 1.1: было 21,25 SemiBold — на экране оно сравнялось
 * с надписью приложения. 18 sp Regular: заметно тише надписи.
 */
private val TITLE_SIZE = 18.sp

private const val ONLY = "Only"
private const val NOTE = "Note"

/**
 * Поправка названия заметки по вертикали.
 *
 * Подобрана по краске: середина прописных должна совпасть с серединой полоски.
 */
private val TITLE_NUDGE = 0.6.dp

/** Высота шапки — как у шапки заметки: надпись стоит на уровне названий. */
private val HEADER_HEIGHT = 40.dp

/** Воздух между шапкой и первой заметкой. */
private val HEADER_TO_LIST = 2.dp

/**
 * Фейд под надписью — тот же приём, что в шапке заметки: чёрное, гасящее
 * в прозрачность. Но там под шапкой есть воздух в 10 dp, а здесь список
 * начинается сразу, поэтому высота меньше: 16 dp вместо 34 — иначе первая
 * заметка читается как выключенная.
 */
private val HEADER_FADE = 16.dp

/** Плотность на верхней кромке — как у фейда в заметке (`FADE_TOP_ALPHA`). */
private const val HEADER_FADE_ALPHA = 0.88f

/** Фейд у нижнего края — размеры как у нижнего фейда в заметке. */
private val BOTTOM_FADE = 19.dp
private const val BOTTOM_FADE_ALPHA = 0.80f

/**
 * Два главных экрана: рабочий и архив.
 *
 * Структуры больше нет: категория у заметки одна — цвет, он и держит порядок.
 * Долгое нажатие по пустому месту создаёт заметку на текущем экране, долгое
 * нажатие по цветной полоске переносит её на другой, долгое нажатие по самой
 * строке — удаляет.
 *
 * Экран приходит слева: справа по логике дизайна живёт заметка, и второй экран
 * оттуда читался бы как ещё одна заметка, а не как соседний лист.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    workListState: LazyListState,
    archiveListState: LazyListState,
    pagerState: PagerState,
    snapshot: AppSnapshot,
    onOpenNote: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onMoveNote: (Note) -> Unit,
    onOpenBackup: () -> Unit,
    onNewNote: (archived: Boolean) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Note?>(null) }
    val newNoteHint = stringResource(R.string.action_new_note)

    // Весь экран крупнее на 20 %: живёт в своей плотности, поэтому растёт всё
    // сразу — кегли, полоски, шаг списка, отступы. Окна (удаление) в этот
    // масштаб не входят: они общие для приложения.
    val baseDensity = LocalDensity.current

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = baseDensity.density * HOME_SCALE,
            fontScale = baseDensity.fontScale,
        ),
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            HomeHeader(onOpenBackup = onOpenBackup)

            Spacer(Modifier.height(HEADER_TO_LIST))

            Box(modifier = Modifier.fillMaxSize()) {
                // Свайп ведёт страницу за пальцем, а отпущенная — доводится
                // пружиной до целой страницы: так переход чувствуется как
                // перелистывание, а не как рывок на 220 мс.
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    // Экран приходит слева: справа по логике дизайна живёт
                    // заметка, и второй экран оттуда читался бы как ещё одна
                    // заметка, а не как соседний лист.
                    reverseLayout = true,
                ) { page ->
                    val archived = page == 1
                    NotesPage(
                        notes = snapshot.notes(archived),
                        listState = if (archived) archiveListState else workListState,
                        emptyHint = newNoteHint,
                        onOpenNote = onOpenNote,
                        onDeleteNote = { note -> pendingDelete = note },
                        onMoveNote = onMoveNote,
                        onEmptyLongPress = { onNewNote(archived) },
                    )
                }

                // Фейд у нижнего края: список уходит в черноту, а не
                // обрывается по границе экрана. Как у текста в заметке.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(BOTTOM_FADE)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(
                                        alpha = BOTTOM_FADE_ALPHA,
                                    ),
                                ),
                            ),
                        ),
                )

                // Фейд у верхнего края — под надписью.
                // Фейд под надписью: строка уходит в черноту, а не обрывается
                // по её нижней границе. Тот же приём, что в шапке заметки.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HEADER_FADE)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background.copy(
                                        alpha = HEADER_FADE_ALPHA,
                                    ),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
        }
    }

    // Опыт 4.9: под окном удаления список гаснет до нуля.
    WindowScrim(visible = pendingDelete != null)

    pendingDelete?.let { note ->
        val name = note.heading.ifBlank { stringResource(R.string.untitled_note) }
        ConfirmDialog(
            title = name,
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            showDismiss = false,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                onDeleteNote(note)
            },
        )
    }
}

/**
 * Одна страница: список заметок и пустая область под ним.
 *
 * Пустая область — не украшение: заметка начинается долгим нажатием по ней.
 * В конце списка она есть всегда: последний элемент — распорка в две пятых
 * экрана, и она же ловит нажатие. Показывается только при непустом списке:
 * пустой список держится за последний элемент, и без этой оговорки первый же
 * набор заметок открывался прокрученным в конец.
 */
@Composable
private fun NotesPage(
    notes: List<Note>,
    listState: LazyListState,
    emptyHint: String,
    onOpenNote: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onMoveNote: (Note) -> Unit,
    onEmptyLongPress: () -> Unit,
) {
    // Высота распорки считается от экрана, а не от вьюпорта списка.
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val density = LocalDensity.current
    val tailHeight = remember(screenHeight) {
        with(density) { (screenHeight * 0.4f).dp }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Пустое место отвечает долгим нажатием: так «+» не нужен.
            // Строки забирают жест себе, поэтому до фона он не доходит.
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onLongClick = onEmptyLongPress,
            )
            .semantics { contentDescription = emptyHint },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = STEP),
        ) {
            items(notes, key = { note -> note.id }) { note ->
                NoteRow(
                    note = note,
                    onClick = { onOpenNote(note) },
                    onLongClick = { onDeleteNote(note) },
                    onStripLongClick = { onMoveNote(note) },
                    modifier = Modifier.animateItem(),
                )
            }
            if (notes.isNotEmpty()) {
                item(key = "tail") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(tailHeight)
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                                onLongClick = onEmptyLongPress,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteRow(
    note: Note,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onStripLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = note.color.skin
    val heading = note.heading
    val title = if (heading.isBlank()) stringResource(R.string.untitled_note) else heading
    // Полоска узкая, но палец в неё не попадает: жест по ней отдельный —
    // долгое нажатие переносит заметку, на остальной строке удаляет. Вложенные
    // combinedClickable этот выбор не дают (верхний обработчик забирает жест),
    // поэтому детектор один, а решение принимается по точке нажатия.
    val stripZone = with(LocalDensity.current) { STRIP_TOUCH_ZONE.toPx() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(note.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { offset ->
                        if (offset.x <= stripZone) onStripLongClick() else onLongClick()
                    },
                )
            }
            .padding(horizontal = SIDE),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Цвет — маркер и единственная категория заметки.
        Box(
            modifier = Modifier
                .width(STRIP_WIDTH)
                .height(STRIP_HEIGHT)
                .background(skin.accent),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                letterSpacing = (-0.15).sp,
                // 1.1: SemiBold на крупном кегле оказался жирным — список
                // читался как оглавление. Regular держит вес строки, а цвет
                // полоски и так несёт категорию.
                fontWeight = FontWeight.Normal,
                fontSize = TITLE_SIZE,
            ),
            color = MaterialTheme.colorScheme.onBackground.copy(
                alpha = if (heading.isBlank()) 0.6f else 1f,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                // Воздух сверху и снизу одинаковый: строка симметрична.
                // Слева — шаг: столько же, сколько от полоски до названия.
                .padding(start = STEP, top = ROW_GAP, bottom = ROW_GAP)
                // Прописные висят выше середины полоски: сдвиг вниз ставит их
                // ровно на её середину.
                .offset(y = TITLE_NUDGE),
        )
    }
}

@Composable
private fun HomeHeader(onOpenBackup: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            // Та же высота, что у шапки заметки: надпись встаёт на одну линию
            // с названиями, а не висит над ними.
            .height(HEADER_HEIGHT)
            .padding(start = SIDE, end = SIDE),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Надпись одного цвета: раньше «Only» было тише «Note», и это читалось
        // как два разных элемента, хотя это одно название.
        Text(
            text = ONLY + NOTE,
            style = MaterialTheme.typography.headlineSmall.copy(
                letterSpacing = (-0.4).sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenBackup,
                onLongClick = onOpenBackup,
            ),
        )
    }
}
