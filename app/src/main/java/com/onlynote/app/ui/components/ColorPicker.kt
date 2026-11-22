package com.onlynote.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.onlynote.app.R
import com.onlynote.app.data.NoteColor
import com.onlynote.app.ui.theme.skin

/** Высота плитки: вдвое выше полоски в списке — цвет видно крупно, пальцем попасть легко. */
private val TILE_HEIGHT = 56.dp

/** Зазор между плитками — с ту же полоску по ширине. */
private val TILE_GAP = 3.dp

/** Поля сетки — как поля текста в заметке. */
private val NOTE_SIDE = 6.dp

@Composable
fun ColorPickerContent(
    current: NoteColor,
    onPick: (NoteColor) -> Unit,
) {
    // Ровно восемь цветов: сетка 4×2 без пустых мест в последнем ряду.
    val colors = NoteColor.rainbowOrder
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Поля те же, что у текста заметки (6 dp): сетка стоит по его краям,
            // а не шире — так цвет читается как часть той же колонки.
            .padding(horizontal = NOTE_SIDE)
            // Снизу лист уходит в скругление экрана: поднимаем плитки над ним,
            // иначе нижний ряд подрезается дугой.
            .navigationBarsPadding()
            .padding(top = 14.dp, bottom = 26.dp),
    ) {
        colors.chunked(4).forEachIndexed { rowIndex, rowColors ->
            if (rowIndex > 0) Spacer(Modifier.height(TILE_GAP))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
            ) {
                rowColors.forEach { color ->
                    ColorSwatch(
                        color = color,
                        selected = color == current,
                        onClick = { onPick(color) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: NoteColor,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = color.skin
    val label = colorLabel(color)

    Box(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = label
            this.selected = selected
        },
        contentAlignment = Alignment.Center,
    ) {
        // Плитка на всю ширину шага сетки и высотой с полоску: углов в приложении
        // нет нигде, а цвет выбирают по тому, как он выглядит в строке списка.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TILE_HEIGHT)
                .background(skin.accent)
                .border(width = 1.dp, color = MaterialTheme.colorScheme.outline)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {}
    }
}

@Composable
fun colorLabel(color: NoteColor): String = stringResource(
    when (color) {
        NoteColor.DEFAULT -> R.string.color_default
        NoteColor.ROSE -> R.string.color_rose
        NoteColor.PEACH -> R.string.color_peach
        NoteColor.LEMON -> R.string.color_lemon
        NoteColor.SAGE -> R.string.color_sage
        NoteColor.BLUE -> R.string.color_blue
        NoteColor.LAVENDER -> R.string.color_lavender
        NoteColor.GRAY -> R.string.color_gray
    },
)
