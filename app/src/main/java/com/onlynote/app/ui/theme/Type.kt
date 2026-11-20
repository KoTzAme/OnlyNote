package com.onlynote.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.onlynote.app.R

/**
 * Rubik с кириллицей: 4.9 — два начертания, а не четыре.
 *
 * Regular — всё, что читают: текст заметки, названия заметок, строки окон.
 * SemiBold — только то, что группирует: имя папки, надпись OnlyNote, заголовок окна.
 * Medium и Bold убраны вместе с файлами шрифтов: две роли — два веса.
 */
val Rubik: FontFamily = FontFamily(
    Font(R.font.rubik_regular, FontWeight.Normal),
    Font(R.font.rubik_semibold, FontWeight.SemiBold),
)

/**
 * Межстрочный интервал — 120 % от кегля по умолчанию; у текста заметки 130 %
 * задаётся отдельно в EditorScreen.
 */
private fun rubik(
    size: Int,
    weight: FontWeight = FontWeight.Normal,
): TextStyle = TextStyle(
    fontFamily = Rubik,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * 1.2f).sp,
)

val OnlyNoteTypography: Typography = Typography(
    displayLarge = rubik(48, FontWeight.SemiBold),
    displayMedium = rubik(40, FontWeight.SemiBold),
    displaySmall = rubik(32, FontWeight.SemiBold),
    headlineLarge = rubik(28, FontWeight.SemiBold),
    headlineMedium = rubik(24, FontWeight.SemiBold),
    headlineSmall = rubik(22, FontWeight.SemiBold),
    titleLarge = rubik(22, FontWeight.SemiBold),
    titleMedium = rubik(17, FontWeight.SemiBold),
    titleSmall = rubik(15, FontWeight.SemiBold),
    bodyLarge = rubik(17),
    bodyMedium = rubik(15),
    bodySmall = rubik(13),
    labelLarge = rubik(14),
    labelMedium = rubik(12),
    labelSmall = rubik(11),
)
