package com.onlynote.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onlynote.app.R
import com.onlynote.app.ui.theme.APP_FONT_SCALE
import com.onlynote.app.ui.theme.HOME_SCALE

/** Сколько клавиатура должна простоять, чтобы её уход считался уходом. */
private const val IME_SETTLE_MS = 400L

/** Ширина окон: строка ввода и заголовок заметки помещаются с запасом. */
private val DIALOG_WIDTH = 300.dp

/**
 * Поле окна. Нижнее меньше верхнего на высоту, которую кнопка занимает внутри
 * себя: так от последней подписи до края столько же, сколько от края до заголовка.
 */
private val DIALOG_PADDING = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)

/**
 * Оболочка окна, собранная вручную.
 *
 * AlertDialog всегда держит полосу под кнопками — даже когда кнопок нет. Из-за
 * этой невидимой полосы содержимое уезжало вверх, а внизу оставалась пустота.
 * Здесь карточка обнимает ровно то, что в ней лежит, отступы задаёт вызывающий.
 *
 * Фон чёрный, углы прямые: серые карточки и скругления выбивались из общего вида.
 * Границей служит затемнение вокруг — рамки нет.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DialogCard(
    width: Dp = DIALOG_WIDTH,
    onDismiss: () -> Unit,
    padding: PaddingValues = DIALOG_PADDING,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Карточка — единственный ребёнок окна: тогда «мимо окна» для системы это
    // действительно мимо карточки, и касание закрывает её сама платформа.
    // Оболочка на весь экран (была нужна для imePadding) делала «мимо» неотличимым
    // от «внутри», и окно не закрывалось.
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.width(width),
        ) {
            Column(modifier = Modifier.padding(padding), content = content)
        }
    }
}

/** Заголовок окна: единственное, что говорит, о чём оно и что случится. */
@Composable
private fun DialogTitle(text: String) {
    Text(
        text = text,
        style = windowTitleStyle(),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Кегль текста в окнах: тот же, что у названий заметок в списке (17 sp,
 * на главном — × 1,2). Одна величина на всё окно: заголовок, действие, строка
 * списка копий. Окна не должны жить в своём размере — иначе они выглядят как
 * чужой интерфейс поверх приложения.
 */
@Composable
internal fun windowTextStyle() = MaterialTheme.typography.bodyLarge.copy(
    fontSize = MaterialTheme.typography.bodyLarge.fontSize * HOME_SCALE * APP_FONT_SCALE,
)

/** Заголовок окна отличается от тела весом, а не кеглем. */
@Composable
internal fun windowTitleStyle() = windowTextStyle()
    .copy(fontWeight = FontWeight.SemiBold)


/** Два ответа внизу окна: делят ширину поровну и стоят симметрично. */
@Composable
private fun DialogAnswers(
    dismissLabel: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    destructive: Boolean = false,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
            Text(dismissLabel, style = windowTextStyle())
        }
        TextButton(onClick = onConfirm, modifier = Modifier.weight(1f)) {
            Text(
                text = confirmLabel,
                style = windowTextStyle(),
                color = if (destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

/**
 * Название папки — одна строка прямо над клавиатурой, и больше ничего.
 *
 * Это не окно: строка живёт поверх экрана и поднимается вместе с клавиатурой,
 * поэтому ведёт себя как обычная панель ввода, а не как карточка. «Ввод» на
 * клавиатуре принимает, системный «назад» (или касание мимо) отменяет.
 */
@Composable
fun FolderNameBar(
    initialValue: String = "",
    existingNames: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    // Курсор сразу за названием: переименование обычно дописывают, а не
            // начинают заново.
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialValue, TextRange(initialValue.length)))
    }
    val focusRequester = remember { FocusRequester() }
    val trimmed = value.text.trim()
    val duplicate = trimmed.isNotEmpty() && existingNames.any { it.equals(trimmed, ignoreCase = true) }
    val valid = trimmed.isNotEmpty() && !duplicate
    val confirm = { if (valid) onConfirm(trimmed) }

    BackHandler { onDismiss() }

    // Окно листа папок закрывается с анимацией и на миг держит фокус окна:
    // первый запрос фокуса может уйти в никуда, и клавиатура не выезжает.
    // Поэтому просим фокус несколько раз подряд.
    LaunchedEffect(Unit) {
        repeat(3) { attempt ->
            if (attempt > 0) delay(120L)
            focusRequester.requestFocus()
        }
    }

    // Клавиатуру убрали — строка закрывается вместе с ней, а не следующим
    // «назад». Но уход клавиатуры сразу после открытия не в счёт: это лист папок
    // доигрывает анимацию и уносит клавиатуру с собой.
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    val openedAt = remember { SystemClock.uptimeMillis() }
    var imeSince by remember { mutableStateOf(0L) }
    LaunchedEffect(ime, density) {
        snapshotFlow { ime.getBottom(density) > 0 }.collect { visible ->
            val now = SystemClock.uptimeMillis()
            if (visible) {
                if (imeSince == 0L) imeSince = now
            } else if (imeSince != 0L) {
                val steady = now - openedAt > IME_SETTLE_MS && now - imeSince > IME_SETTLE_MS
                imeSince = 0L
                if (steady) onDismiss()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentAlignment = Alignment.BottomStart,
    ) {
        // Касание мимо строки — та же отмена, что «назад».
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )
        BasicTextField(
            value = value,
            onValueChange = { next -> if (next.text.length <= 60) value = next },
            textStyle = windowTextStyle().copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { confirm() }),
            modifier = Modifier
                .fillMaxWidth()
                // Свой фон обязателен: строка висит над списком, и без него
                // название папки просвечивало сквозь набираемый текст.
                .background(MaterialTheme.colorScheme.background)
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp)
                .focusRequester(focusRequester),
            decorationBox = { inner ->
                Box {
                    if (value.text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.hint_folder_name),
                            style = windowTextStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    confirmLabel: String,
    body: String = "",
    destructive: Boolean = false,
    showDismiss: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DialogCard(onDismiss = onDismiss) {
        DialogTitle(title)
        if (body.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = body,
                style = windowTextStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (!showDismiss) {
            // Одно действие — кнопка сразу под заголовком, по его левому краю.
            Spacer(Modifier.height(2.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                TextButton(
                    onClick = onConfirm,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = confirmLabel,
                        style = windowTextStyle(),
                        color = if (destructive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        } else {
            Spacer(Modifier.height(12.dp))
            DialogAnswers(
                dismissLabel = stringResource(R.string.action_cancel),
                confirmLabel = confirmLabel,
                onDismiss = onDismiss,
                onConfirm = onConfirm,
                destructive = destructive,
            )
        }
    }
}

/**
 * Затемнение под окном: при открытии уводит экран под ним в чёрный.
 *
 * Опыт 4.9: пока окно открыто, всё, что под ним, гаснет до нуля — окно
 * остаётся единственным на экране. Ход 320 мс в обе стороны: на 0,5× системного
 * масштаба это ~160 мс на экране, вдвое дольше первого варианта (160 мс в коде).
 * Само окно появляется как раньше, без своей анимации — едет только затемнение.
 * Слой не перехватывает касания: окно живёт в своём окне поверх и закрывается
 * платформой, как раньше.
 */
@Composable
internal fun WindowScrim(visible: Boolean) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "window-scrim",
    )
    if (alpha > 0.001f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = alpha)),
        )
    }
}
