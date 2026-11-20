package com.onlynote.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlynote.app.R
import com.onlynote.app.data.Note
import com.onlynote.app.data.NoteColor
import com.onlynote.app.data.noteHeading
import com.onlynote.app.ui.components.ColorPickerContent
import com.onlynote.app.ui.components.EditHistory
import com.onlynote.app.ui.components.WindowScrim
import com.onlynote.app.ui.folderTitle
import com.onlynote.app.ui.theme.SECONDARY_ALPHA
import com.onlynote.app.ui.theme.skin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/**
 * Заметка в двух режимах — поверх одного нативного поля.
 *
 * Весь текст живёт в настоящем `EditText` ([NoteField]): выделение, «выбрать всё»,
 * ручки выделения и прокрутка к курсору — родные, через границы блоков ничего
 * не рвётся. Режим просмотра — то же поле без правки, но с выделением и копированием.
 *
 * Заголовок, разделитель и поиск остаются в Compose: они не участвуют в наборе.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    note: Note,
    folderName: String,
    isNew: Boolean,
    onBack: () -> Unit,
    onSave: (body: String) -> Unit,
    onColorChange: (NoteColor) -> Unit,
    onDiscardBlank: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val limitText = stringResource(R.string.limit_reached)
    val untitled = stringResource(R.string.untitled_note)

    // 3.8 в редакторе не показывала один завершающий перевод строки: иначе внизу
    // появлялась пустая строка — чёрная полоса над клавиатурой. Держим так же:
    // в поле текст без последнего \n, а при сохранении возвращаем его обратно.
    val keepsTrailingNewline = remember(note.id) { note.body.endsWith("\n") }
    fun bodyOf(text: String): String =
        if (keepsTrailingNewline && !text.endsWith("\n")) text + "\n" else text
    val displayBody = remember(note.id) {
        if (note.body.endsWith("\n")) note.body.dropLast(1) else note.body
    }

    // Текст живёт вне состояния композиции: набор не должен пересобирать экран.
    // Заголовок обновляется отдельно и только когда первая строка правда изменилась.
    val docText = remember(note.id) { mutableStateOf(displayBody) }
    val heading = remember(note.id) { mutableStateOf(displayBody.noteHeading()) }

    val history = remember(note.id) { EditHistory(limit = 60) }
    val saved = remember(note.id) { SavedText(bodyOf(displayBody)) }
    val matches = remember(note.id) { MatchList() }
    val field = remember(note.id) { NoteFieldController(displayBody) }

    var editing by remember(note.id) { mutableStateOf(isNew) }
    var searching by remember(note.id) { mutableStateOf(false) }
    var query by remember(note.id) { mutableStateOf("") }
    var matchTotal by remember(note.id) { mutableIntStateOf(0) }
    var matchIndex by remember(note.id) { mutableIntStateOf(0) }
    var searchVersion by remember(note.id) { mutableIntStateOf(0) }
    // Номер просьбы поставить курсор: по нему поле получает фокус и клавиатуру.
    var editRequest by remember(note.id) { mutableIntStateOf(if (isNew) 1 else 0) }
    var editTarget by remember(note.id) { mutableIntStateOf(displayBody.length) }

    var color by remember(note.id) { mutableStateOf(note.color) }
    var colorSheetOpen by remember { mutableStateOf(false) }

    val searchFocus = remember { FocusRequester() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val skin = color.skin
    val typography = MaterialTheme.typography
    val textStyle = remember(typography, skin.onEditor) {
        typography.bodyLarge.copy(
            color = skin.onEditor,
            fontSize = NOTE_FONT_SIZE.sp,
            // 4.9: 130 % вместо 120 %. На 100 000 знаках строка дышит, глаз реже
            // перескакивает строкой выше; цена — две строки на экране.
            lineHeight = (NOTE_FONT_SIZE * 1.3f).sp,
        )
    }

    fun replaceText(text: String, caret: Int) {
        field.applyText(text, caret)
        docText.value = text
        heading.value = text.noteHeading()
    }

    fun beginEditing(at: Int) {
        if (searching) {
            searching = false
            query = ""
            matchTotal = 0
            field.clearHighlight()
        }
        editTarget = at
        editing = true
        editRequest++
    }

    fun stopEditing() {
        if (!editing) return
        editing = false
        focusManager.clearFocus()
        field.stopEditing()
    }

    // Курсор после отката ставится за изменившимся куском, а не остаётся
    // на месте: вернули слово — курсор после слова.
    fun undo() {
        val current = docText.value
        val previous = history.undo(current) ?: return
        replaceText(previous, caretAfterChange(current, previous))
    }

    fun redo() {
        val current = docText.value
        val next = history.redo(current) ?: return
        replaceText(next, caretAfterChange(current, next))
    }

    // Колбэки поля переназначаются на каждой пересборке: они читают свежие состояния.
    field.onTextChanged = { previous, next ->
        docText.value = next
        if (history.shouldRecord()) history.record(previous)
        val line = next.noteHeading()
        if (heading.value != line) heading.value = line
    }
    field.onTap = { offset -> beginEditing(offset) }
    field.onLimitReached = { field.showNotice(limitText) }
    field.onUndoKey = { undo() }
    field.onRedoKey = { redo() }

    val currentSave by rememberUpdatedState(onSave)

    // Автосохранение: пауза в наборе 400 мс. Чтение текста — потоком,
    // а не в композиции: иначе каждая буква пересобирала бы весь экран.
    LaunchedEffect(note.id) {
        snapshotFlow { docText.value }.collectLatest { text ->
            delay(400)
            val body = bodyOf(text)
            if (body != saved.body) {
                currentSave(body)
                saved.body = body
            }
        }
    }

    // Просьба поставить курсор: и для новой заметки, и для тапа/карандаша.
    LaunchedEffect(editRequest) {
        if (editRequest > 0 && editing) {
            field.focusAt(editTarget, showKeyboard = true)
        }
    }

    // Клавиатура спряталась — возвращаемся в просмотр. Видимость читаем потоком:
    // чтение в композиции пересобирало бы экран на каждый кадр её анимации.
    val imeInsets = WindowInsets.ime
    var imeVisible by remember { mutableStateOf(false) }
    var imeWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(density, imeInsets) {
        snapshotFlow { imeInsets.getBottom(density) > 0 }.collect { imeVisible = it }
    }
    LaunchedEffect(imeVisible, editing) {
        if (!editing) {
            imeWasVisible = false
            return@LaunchedEffect
        }
        if (imeVisible) {
            imeWasVisible = true
            return@LaunchedEffect
        }
        if (!imeWasVisible) return@LaunchedEffect
        // Клавиатура может пропасть на кадр-два при переходе между окнами:
        // без паузы правка закрывалась до того, как человек начинал печатать.
        // Пауза маленькая намеренно: «назад» при открытой клавиатуре система
        // отдаёт клавиатуре, до приложения оно не доходит, и стрелки отмены
        // висели бы в шапке ещё 300 мс после того, как клавиатура уехала.
        delay(IME_HIDE_GRACE)
        if (imeVisible) return@LaunchedEffect
        imeWasVisible = false
        stopEditing()
    }

    LaunchedEffect(searching) {
        if (searching) {
            matchIndex = 0
            delay(120)
            searchFocus.requestFocus()
        }
    }

    // Поиск считаем вне композиции: набор запроса не должен пересобирать заметку.
    LaunchedEffect(searching, query) {
        if (!searching || query.isBlank()) {
            matches.list = emptyList()
            if (matchTotal != 0) matchTotal = 0
            if (matchIndex != 0) matchIndex = 0
            field.clearHighlight()
            return@LaunchedEffect
        }
        val text = docText.value
        val found = withContext(Dispatchers.Default) { matchesOf(text, query) }
        matches.list = found
        matchTotal = found.size
        matchIndex = 0
        searchVersion++
    }

    // Подсветка совпадений и переход к текущему — прямо в поле, участками разметки.
    LaunchedEffect(searchVersion, matchIndex, searching, color) {
        if (!searching) {
            field.clearHighlight()
            return@LaunchedEffect
        }
        val list = matches.list
        if (list.isEmpty()) {
            field.clearHighlight()
            return@LaunchedEffect
        }
        val index = matchIndex.coerceIn(0, list.lastIndex)
        val accent = color.skin.accent
        field.highlight(
            list,
            index,
            accent.copy(alpha = 0.45f).toArgb(),
            accent.copy(alpha = 0.20f).toArgb(),
        )
        field.scrollToOffset(list[index].first)
    }

    // Сохранение при уходе с экрана. Пустую новую заметку не оставляем.
    val discardBlank by rememberUpdatedState(onDiscardBlank)
    val isNewNote by rememberUpdatedState(isNew)
    DisposableEffect(note.id) {
        onDispose {
            val body = bodyOf(docText.value)
            if (isNewNote && body.isBlank()) {
                discardBlank()
            } else if (body != saved.body) {
                currentSave(body)
            }
        }
    }

    BackHandler(enabled = editing || searching) {
        if (searching) {
            searching = false
            query = ""
            focusManager.clearFocus()
        } else {
            stopEditing()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .imePadding(),
        ) {
            EditorHeader(
                folderName = folderName,
                heading = heading,
                untitled = untitled,
                textStyle = textStyle,
                tint = skin.onEditor,
                editing = editing,
                onUndo = { undo() },
                onRedo = { redo() },
                onStartEdit = { beginEditing(field.length()) },
                onSearch = {
                    if (editing) stopEditing()
                    searching = true
                },
                onColor = { colorSheetOpen = true },
            )

            // Разделитель цвета самой заметки между шапкой и текстом.
            HorizontalDivider(color = skin.accent, thickness = 1.dp)

            // Строка поиска живёт в постоянном слоте: если вставить её в Column
            // условно, поле заметки ниже меняет позицию и Compose пересоздаёт
            // AndroidView — вместе с текстом и прокруткой. Слот держит соседей на месте.
            Box(modifier = Modifier.fillMaxWidth()) {
                if (searching) {
                    SearchBar(
                        query = query,
                        onQueryChange = {
                            query = it
                            matchIndex = 0
                        },
                        total = matchTotal,
                        current = matchIndex,
                        onPrev = {
                            if (matchTotal > 0) matchIndex = (matchIndex - 1 + matchTotal) % matchTotal
                        },
                        onNext = {
                            if (matchTotal > 0) matchIndex = (matchIndex + 1) % matchTotal
                        },
                        focusRequester = searchFocus,
                        accent = skin.accent,
                        textColor = skin.onEditor,
                    )
                }
            }

            NoteField(
                controller = field,
                editing = editing,
                bottomFade = editing || searching,
                textColor = skin.onEditor,
                cursorColor = skin.cursor,
                fontSize = NOTE_FONT_SIZE.sp,
                lineHeight = (NOTE_FONT_SIZE * 1.3f).sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }

        // Опыт 4.9: под листом цвета заметка уходит в ноль, как под окнами.
        WindowScrim(visible = colorSheetOpen)
    }

    if (colorSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { colorSheetOpen = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            // Прямые углы: скруглений в приложении нет нигде.
            shape = RectangleShape,
            // Без серой «ручки» сверху: на чёрном она единственное светлое пятно.
            dragHandle = null,
        ) {
            ColorPickerContent(
                current = color,
                onPick = { picked ->
                    color = picked
                    onColorChange(picked)
                    colorSheetOpen = false
                },
            )
        }
    }
}

/**
 * Шапка заметки: папка приглушена, дальше заголовок — одним кеглем с текстом.
 * Стрелки «назад» нет: из заметки выходят системным жестом.
 *
 * Значков в шапке нет вовсе. В просмотре тап открывает правку с конца, долгий
 * тап делит шапку пополам: слева цвет, справа поиск. В правке тап по левой
 * половине — отмена, по правой — повтор, а долгих нажатий там нет: два жеста
 * на одно место читались бы как одно и то же.
 */
@Composable
private fun EditorHeader(
    folderName: String,
    heading: androidx.compose.runtime.State<String>,
    untitled: String,
    textStyle: TextStyle,
    tint: Color,
    editing: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onStartEdit: () -> Unit,
    onSearch: () -> Unit,
    onColor: () -> Unit,
) {
    val title = remember(folderName, heading.value, untitled, tint) {
        buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = tint.copy(alpha = SECONDARY_ALPHA),
                    // 4.9: имя папки в шапке — SemiBold, как и в списке: это
                    // подпись группы, а не текст заметки.
                    fontWeight = FontWeight.SemiBold,
                ),
            ) {
                append(folderName.folderTitle())
            }
            append("   ")
            append(heading.value.ifBlank { untitled })
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            // Шапка низкая нарочно: имя папки и заголовок стоят вплотную к разделителю.
            .height(HEADER_HEIGHT)
            .pointerInput(editing) {
                detectTapGestures(
                    onTap = { offset ->
                        when {
                            !editing -> onStartEdit()
                            offset.x < size.width / 2f -> onUndo()
                            else -> onRedo()
                        }
                    },
                    onLongPress = { offset ->
                        if (!editing) {
                            if (offset.x < size.width / 2f) onColor() else onSearch()
                        }
                    },
                )
            }
            .padding(start = CONTENT_SIDE_PADDING, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = textStyle,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    total: Int,
    current: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    focusRequester: FocusRequester,
    accent: Color,
    textColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = TextFieldValue(query, TextRange(query.length)),
            onValueChange = { onQueryChange(it.text) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
            cursorBrush = SolidColor(accent),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onNext() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = textColor.copy(alpha = 0.4f),
                            ),
                        )
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (total == 0) "0" else "${current + 1}/$total",
            style = MaterialTheme.typography.labelMedium,
            color = textColor.copy(alpha = SECONDARY_ALPHA),
        )
        IconButton(onClick = onPrev, enabled = total > 0) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = stringResource(R.string.search_prev),
                tint = textColor.copy(alpha = if (total > 0) 1f else 0.3f),
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onNext, enabled = total > 0) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.search_next),
                tint = textColor.copy(alpha = if (total > 0) 1f else 0.3f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Куда поставить курсор после отмены и возврата: сразу за изменившимся куском.
 *
 * Общий префикс и общий хвост дают границы правки, курсор встаёт в её конец —
 * то есть после слова, которое вернули.
 */
private fun caretAfterChange(from: String, to: String): Int {
    val limit = minOf(from.length, to.length)
    var prefix = 0
    while (prefix < limit && from[prefix] == to[prefix]) prefix++
    var suffix = 0
    while (suffix < limit - prefix &&
        from[from.length - 1 - suffix] == to[to.length - 1 - suffix]
    ) {
        suffix++
    }
    return (to.length - suffix).coerceIn(0, to.length)
}

/** Что уже лежит в базе: меняется без пересборки композиции. */
private class SavedText(var body: String)

/** Найденные совпадения: живут вне состояния, чтобы поиск не пересобирал экран. */
private class MatchList {
    var list: List<IntRange> = emptyList()
}

/** Поля заметки: примерно 1 мм от края экрана. */
private val CONTENT_SIDE_PADDING = 6.dp

/** Высота шапки: имя папки и заголовок держатся у самого разделителя. */
private val HEADER_HEIGHT = 40.dp


/** Область нажатия карандаша: он один в шапке, ему можно отдать больше. */

/** Кегль текста заметки. */
private const val NOTE_FONT_SIZE = 16

/**
 * Сколько ждать, прежде чем считать уход клавиатуры окончательным.
 *
 * Между окнами она пропадает на кадр-два (это 16–32 мс), поэтому пауза нужна,
 * но короткая: «назад» при открытой клавиатуре система отдаёт клавиатуре,
 * приложение об этом не узнаёт, и лишние 300 мс стрелки отмены висели бы
 * в шапке уже после того, как клавиатура уехала.
 */
private const val IME_HIDE_GRACE = 80L

