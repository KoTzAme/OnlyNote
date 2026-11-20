@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.onlynote.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlynote.app.R
import com.onlynote.app.data.AppSnapshot
import com.onlynote.app.data.Folder
import com.onlynote.app.data.Note
import com.onlynote.app.ui.components.ConfirmDialog
import com.onlynote.app.ui.components.WindowScrim
import com.onlynote.app.ui.folderTitle
import com.onlynote.app.ui.theme.SECONDARY_ALPHA
import com.onlynote.app.ui.theme.HOME_SCALE
import com.onlynote.app.ui.theme.skin

/**
 * Один шаг вертикального ритма.
 *
 * Это же расстояние отделяет цветную полоску от названия заметки, поэтому оно
 * задаёт и весь список: папка → своя заметка — два шага, заметка → любая папка
 * (своя снизу и чужая) — тоже два, OnlyNote → первая папка — три.
 */
private val STEP = 6.dp

private const val ONLY = "Only"
private const val NOTE = "Note"
private val GUTTER = 20.dp

/**
 * Поправка названия заметки по вертикали.
 *
 * Подобрана по краске: середина прописных должна совпасть с серединой полоски.
 * Без поправки буквы висят на пиксель выше, 1,5 dp — на три ниже; половина шага
 * ставит их ровно.
 */
private val TITLE_NUDGE = 0.5.dp

/** Высота шапки — как у шапки заметки: надпись стоит на уровне названий. */
private val HEADER_HEIGHT = 40.dp

/**
 * Воздух между шапкой и первой папкой.
 *
 * Три шага от надписи до имени папки складываются из этого отступа и отступа
 * самой папки; часть воздуха уже даёт шапка, поэтому здесь остаются не два
 * полных шага, а остаток — ровно до 21 dp по краске.
 */
private val HEADER_TO_LIST = 2.dp

/**
 * Единственный «рабочий» экран: папки и заметки живут вместе.
 * Нажатие на папку разворачивает её заметки, долгое нажатие — переименование.
 * Нажатие на «OnlyNote» открывает резервные копии.
 *
 * Плюса на экране нет: новая заметка начинается долгим нажатием по пустому
 * месту. Кнопка занимала угол и спорила с текстом, а жест на пустоте — ровно
 * там, где ничего не нажимается.
 *
 * Иерархия держится не рамками, а типографикой: имя папки — тихая подпись,
 * текст заметки — крупнее и плотнее. Ни ряби под пальцем, ни свечения за краем.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    snapshot: AppSnapshot,
    expandedFolderIds: Set<Long>,
    onToggleFolder: (Long) -> Unit,
    onOpenNote: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onRenameFolder: (Folder) -> Unit,
    onOpenBackup: () -> Unit,
    onNewNote: () -> Unit,
) {
    // Заметка, которую собрались удалить: подтверждение живёт на этом экране,
    // потому что удаление начинается здесь, долгим нажатием.
    var pendingDelete by remember { mutableStateOf<Note?>(null) }
    val newNoteHint = stringResource(R.string.action_new_note)

    // Весь экран крупнее на 20 %: живёт в своей плотности, поэтому растёт всё
    // сразу — кегли, полоски, шаг списка, отступы, — а ритм из одного шага
    // остаётся целым. Окна (удаление) в этот масштаб не входят: они общие для
    // приложения и должны совпадать с окнами других экранов.
    val baseDensity = LocalDensity.current

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = baseDensity.density * HOME_SCALE,
            fontScale = baseDensity.fontScale,
        ),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                // Пустое место отвечает долгим нажатием: так «+» не нужен.
                // Нажатия по строкам сюда не доходят — строки забирают жест себе.
                // Когда поверх открыта заметка, дом не перехватывает касания вовсе:
                // иначе его жест забирал бы свайпы у текста заметки.
                .semantics { contentDescription = newNoteHint }
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = onNewNote,
                ),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                HomeHeader(onOpenBackup = onOpenBackup)

                Spacer(Modifier.height(HEADER_TO_LIST))

                // Заметки раскладываются по папкам один раз: фильтр на каждую папку
                // был бы O(папки × заметки) на каждую пересборку списка.
                val notesByFolder = remember(snapshot.notes) {
                    snapshot.notes.groupBy { it.folderId }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    snapshot.folders.forEach { folder ->
                        val notes = notesByFolder[folder.id].orEmpty()
                        val expanded = folder.id in expandedFolderIds

                        item(key = "folder-${folder.id}") {
                            FolderRow(
                                folder = folder,
                                expanded = expanded,
                                hasNotes = notes.isNotEmpty(),
                                onToggle = { onToggleFolder(folder.id) },
                                onRename = { onRenameFolder(folder) },
                                modifier = Modifier.animateItem(),
                            )
                        }

                        if (expanded) {
                            items(notes, key = { note -> "note-${note.id}" }) { note ->
                                NoteRow(
                                    note = note,
                                    onClick = { onOpenNote(note) },
                                    onLongClick = { pendingDelete = note },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Опыт 4.9: под окном удаления список гаснет до нуля, а не до 40 %.
    WindowScrim(visible = pendingDelete != null)

    // Удаление подтверждается окном с названием заметки: без него непонятно,
    // что именно уйдёт, — заметки под затемнением видно, но не сосчитать.
    // Заголовок — само название: слово «Удалить» в нём дублировало кнопку,
    // а «Отмена» рядом с ней была второй кнопкой на одно действие.
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

@Composable
private fun HomeHeader(onOpenBackup: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            // Та же высота, что у шапки заметки: надпись встаёт на одну линию
            // с названиями, а не висит над ними.
            .height(HEADER_HEIGHT)
            .padding(start = GUTTER, end = GUTTER),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Надпись не реагирует на нажатие вовсе: ни волны, ни просадки яркости.
        // Она не кнопка, а название приложения — подсветка делала из неё элемент,
        // на который надо попасть, хотя за ней просто окно копий.
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    ),
                ) {
                    append(ONLY)
                }
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(NOTE)
                }
            },
            style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = (-0.4).sp),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenBackup,
                    onLongClick = onOpenBackup,
                ),
        )
    }
}

@Composable
private fun FolderRow(
    folder: Folder,
    expanded: Boolean,
    hasNotes: Boolean,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                    onLongClick = onRename,
                )
                .padding(start = GUTTER, end = GUTTER),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = folder.name.folderTitle(),
                // Кегль как у заметки, но начертание жирное: папка — заголовок группы.
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.15).sp,
                ),
                color = if (expanded) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    // Шаг сверху и снизу всегда: именно нижний отступ папки даёт
                    // воздух до её первой заметки. В 4.9 этот воздух шёл через
                    // leading у строки заметки; после чистки он переехал сюда,
                    // и расстояние осталось прежним — два шага.
                    .padding(
                        top = STEP,
                        bottom = STEP,
                    ),
            )
        }
    }
}

@Composable
private fun NoteRow(
    note: Note,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = note.color.skin
    val heading = note.heading
    val title = if (heading.isBlank()) stringResource(R.string.untitled_note) else heading

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                // Долгое нажатие — удаление: из заметки этот пункт убран,
                // и место ему здесь, рядом с самой заметкой.
                onLongClick = onLongClick,
            )
            .padding(horizontal = GUTTER),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Цвет — маркер, а не украшение: узкая стойка на высоту строки текста.
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .background(skin.accent),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                letterSpacing = (-0.15).sp,
                fontWeight = FontWeight.Normal,
            ),
            color = MaterialTheme.colorScheme.onBackground.copy(
                alpha = if (heading.isBlank()) SECONDARY_ALPHA else 1f,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                // Отступы сверху и снизу одинаковые: строка симметрична.
                // Слева — один шаг: столько же, сколько между строками списка.
                .padding(start = STEP, top = STEP, bottom = STEP)
                // Строка и полоска центрируются по своим коробкам, но у
                // шрифта запас снизу (под выносными) больше, чем сверху,
                // поэтому прописные висят выше середины полоски. Сдвиг вниз
                // на 1,5 dp ставит их середину ровно на её середину.
                .offset(y = TITLE_NUDGE),
        )
    }
}
