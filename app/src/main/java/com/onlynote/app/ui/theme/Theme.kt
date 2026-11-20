package com.onlynote.app.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val Black = Color(0xFF000000)

/**
 * Текст приглушён до 80 % яркости: на чистом чёрном это мягче для глаз
 * и не «звенит» белым в тёмной комнате.
 */
private val Text = Color(0xFFBEBEBE)
private val TextMuted = Color(0xFF727272)

/**
 * Тема одна — чёрная. На OLED-экране настоящий #000000 выключенные пиксели
 * и даёт максимальную экономию, поэтому светлой темы нет намеренно.
 */
private val OnlyNoteColors = darkColorScheme(
    primary = Text,
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF1A1A1A),
    onPrimaryContainer = Text,
    secondary = Color(0xFF7B8089),
    onSecondary = Color(0xFF101216),
    secondaryContainer = Color(0xFF1A1C21),
    onSecondaryContainer = Text,
    tertiary = Color(0xFF727C91),
    onTertiary = Color(0xFF0F1218),
    tertiaryContainer = Color(0xFF1A1F28),
    onTertiaryContainer = Text,
    background = Black,
    onBackground = Text,
    surface = Black,
    onSurface = Text,
    surfaceVariant = Color(0xFF101010),
    onSurfaceVariant = TextMuted,
    surfaceContainerLowest = Black,
    surfaceContainerLow = Color(0xFF070707),
    surfaceContainer = Color(0xFF0B0B0B),
    surfaceContainerHigh = Color(0xFF121212),
    surfaceContainerHighest = Color(0xFF171717),
    outline = Color(0xFF2C2C2C),
    outlineVariant = Color(0xFF1C1C1C),
    error = Color(0xFFCC5656),
    onError = Color(0xFF1A0505),
    errorContainer = Color(0xFF3A1010),
    onErrorContainer = Color(0xFFCCB0B0),
    inverseSurface = Text,
    inverseOnSurface = Color(0xFF141414),
    inversePrimary = Color(0xFF2A3FB0),
    scrim = Black,
)

/**
 * Масштаб текста интерфейса: 110 % от системного.
 *
 * Окна живут в своих окнах-композициях и этой плотности не получают,
 * поэтому им кегль домножается на ту же константу вручную (см. windowTextStyle).
 */
const val APP_FONT_SCALE = 1.1f

@OptIn(ExperimentalFoundationApi::class)
@Suppress("DEPRECATION")
@Composable
fun OnlyNoteTheme(content: @Composable () -> Unit) {
    // Масштаб интерфейса 110 %: весь текст чуть крупнее системного.
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density = density.density, fontScale = density.fontScale * APP_FONT_SCALE),
        // Свечение за краем списка — единственное «чужое» пятно на чистом чёрном.
        // Ответ на жест даёт результат, а не вспышка.
        LocalOverscrollConfiguration provides null,
    ) {
        MaterialTheme(
            colorScheme = OnlyNoteColors,
            typography = OnlyNoteTypography,
            content = content,
        )
    }
}
