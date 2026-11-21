package com.onlynote.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.onlynote.app.data.NoteColor

/** Текст заметки тоже приглушён на 20 % — как и весь остальной текст приложения. */
private fun Color.muted(): Color = Color(
    red = red * 0.8f,
    green = green * 0.8f,
    blue = blue * 0.8f,
    alpha = alpha,
)

/**
 * Как цвет заметки выглядит в чёрной теме.
 *
 * [accent] — цвет шапки заметки и полоски в списке.
 * Фон самой заметки всегда чёрный: на OLED это и красивее, и экономнее.
 * [onEditor] — цвет текста заметки.
 */
data class NoteSkin(
    val accent: Color,
    val onEditor: Color,
) {
    /**
     * Курсор в заметке красится цветом самой заметки — как в строке поиска.
     * У «без цвета» акцент почти чёрный, и курсор на нём пропал бы, поэтому
     * для неё берём цвет текста.
     */
    val cursor: Color get() = if (accent.luminance() > 0.12f) accent else onEditor
}

private val skins: Map<NoteColor, NoteSkin> = mapOf(
    NoteColor.DEFAULT to NoteSkin(
        accent = Color(0xFF3C3C3C),
        onEditor = Color(0xFFEDEDED).muted(),
    ),
    NoteColor.ROSE to NoteSkin(
        accent = Color(0xFFFF5C7A),
        onEditor = Color(0xFFFFE4E9).muted(),
    ),
    NoteColor.PEACH to NoteSkin(
        accent = Color(0xFFFF9B3D),
        onEditor = Color(0xFFFFE7D2).muted(),
    ),
    NoteColor.LEMON to NoteSkin(
        accent = Color(0xFFFFD23F),
        onEditor = Color(0xFFFFF3CB).muted(),
    ),
    // 1.1: насыщенность шалфея поднята на 25 % (HSL 0,48 → 0,60) —
    // на чёрном он читался как серо-зелёный.
    NoteColor.SAGE to NoteSkin(
        accent = Color(0xFF9BD75F),
        onEditor = Color(0xFFE9F6D9).muted(),
    ),
    NoteColor.BLUE to NoteSkin(
        accent = Color(0xFF4C8DFF),
        onEditor = Color(0xFFDDE9FF).muted(),
    ),
    NoteColor.LAVENDER to NoteSkin(
        accent = Color(0xFFA488FF),
        onEditor = Color(0xFFEAE3FF).muted(),
    ),
    NoteColor.GRAY to NoteSkin(
        accent = Color(0xFFC8C9CC),
        onEditor = Color(0xFFF0F0F0).muted(),
    ),
)

val NoteColor.skin: NoteSkin get() = skins.getValue(this)
