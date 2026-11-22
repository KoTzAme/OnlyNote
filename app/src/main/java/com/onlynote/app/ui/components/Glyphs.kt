package com.onlynote.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Три фигуры, которые нужны интерфейсу, — рисуем сами.
 *
 * Библиотека иконок Material тянет в APK свои векторы и классы ради двух стрелок
 * и галочки; здесь те же фигуры в четыре строки канвы. Толщина штриха и размер
 * совпадают с прежними иконками 20 и 22 dp.
 */
@Composable
fun ChevronUp(color: Color, size: Dp = 20.dp, description: String? = null) {
    Chevron(color = color, size = size, description = description, up = true)
}

@Composable
fun ChevronDown(color: Color, size: Dp = 20.dp, description: String? = null) {
    Chevron(color = color, size = size, description = description, up = false)
}

@Composable
private fun Chevron(color: Color, size: Dp, description: String?, up: Boolean) {
    Canvas(
        modifier = Modifier
            .size(size)
            .then(
                if (description == null) {
                    Modifier
                } else {
                    Modifier.semantics { contentDescription = description }
                },
            ),
    ) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.1f
        val inset = w * 0.22f
        val path = Path().apply {
            if (up) {
                moveTo(inset, h * 0.62f)
                lineTo(w / 2f, h * 0.38f)
                lineTo(w - inset, h * 0.62f)
            } else {
                moveTo(inset, h * 0.38f)
                lineTo(w / 2f, h * 0.62f)
                lineTo(w - inset, h * 0.38f)
            }
        }
        drawPath(path = path, color = color, style = Stroke(width = stroke))
    }
}

/** Галочка: то же место и размер, что были у иконки Material. */
@Composable
fun Tick(color: Color, size: Dp = 22.dp, description: String? = null) {
    Canvas(
        modifier = Modifier
            .size(size)
            .then(
                if (description == null) {
                    Modifier
                } else {
                    Modifier.semantics { contentDescription = description }
                },
            ),
    ) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.24f, h * 0.52f)
            lineTo(w * 0.44f, h * 0.72f)
            lineTo(w * 0.78f, h * 0.3f)
        }
        drawPath(path = path, color = color, style = Stroke(width = w * 0.1f))
    }
}
