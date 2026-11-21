package com.onlynote.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onlynote.app.R
import com.onlynote.app.ui.theme.APP_FONT_SCALE
import com.onlynote.app.ui.theme.HOME_SCALE
import androidx.compose.runtime.getValue

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
