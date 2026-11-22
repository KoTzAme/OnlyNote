package com.onlynote.app.ui.components

/**
 * История правок: отмена и возврат по словам, как в ColorNote.
 *
 * Шаг — слово, а не отрезок времени. Пока человек набирает или стирает внутри
 * слова, всё это один шаг: откат убирает слово целиком. Как только в правке
 * появляется разделитель (пробел, знак, перевод строки), шаг закрывается — и
 * следующий откат берёт уже следующее слово. Поэтому пробел после слова
 * откатывается вместе с ним, а не отдельным нажатием.
 *
 * Ещё одно условие склейки — правка должна продолжаться там же, где кончилась
 * прошлая: ткнул пальцем в другое место и напечатал букву, это уже новый шаг.
 *
 * Снимок — это весь текст, а заметки бывают по сто тысяч знаков. Поэтому история
 * ограничена дважды: числом шагов ([limit]) и общим объёмом текста ([limitChars]).
 * Без второго предела шестьдесят шагов в большой заметке — это больше десяти
 * мегабайт памяти на копии строк, и приложение растёт на глазах.
 */
class EditHistory(
    private val limit: Int = 60,
    private val limitChars: Int = 1_200_000,
) {
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var undoChars = 0
    private var redoChars = 0

    /** Где кончилась прошлая правка и был ли в ней разделитель. */
    private var lastEditEnd = -1
    private var lastWasSeparator = true

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /**
     * Учесть правку [previous] → [next].
     *
     * Снимок кладётся только когда начинается новое слово; правки внутри слова
     * склеиваются с уже положенным.
     */
    fun onChanged(previous: String, next: String) {
        val start = commonPrefix(previous, next)
        val tail = commonSuffix(previous, next, start)
        val inserted = next.substring(start, next.length - tail)
        val deleted = previous.substring(start, previous.length - tail)
        val continues = (start == lastEditEnd || start + deleted.length == lastEditEnd) &&
            !lastWasSeparator
        if (!continues) {
            push(undoStack, previous) { undoChars += it }
            undoChars = trim(undoStack, undoChars)
        }
        val edited = if (inserted.isNotEmpty()) inserted else deleted
        lastWasSeparator = edited.isEmpty() || edited.any { !it.isWordChar() }
        lastEditEnd = start + inserted.length
        redoStack.clear()
        redoChars = 0
    }

    /** [current] откладывается, чтобы «вернуть» было куда вернуться. */
    fun undo(current: String): String? {
        val previous = undoStack.removeLastOrNull() ?: return null
        undoChars -= previous.length
        push(redoStack, current) { redoChars += it }
        redoChars = trim(redoStack, redoChars)
        newWord()
        return previous
    }

    fun redo(current: String): String? {
        val next = redoStack.removeLastOrNull() ?: return null
        redoChars -= next.length
        push(undoStack, current) { undoChars += it }
        undoChars = trim(undoStack, undoChars)
        newWord()
        return next
    }

    /** После отката следующая буква начинает новый шаг. */
    private fun newWord() {
        lastEditEnd = -1
        lastWasSeparator = true
    }

    private fun commonPrefix(a: String, b: String): Int {
        val edge = minOf(a.length, b.length)
        var i = 0
        while (i < edge && a[i] == b[i]) i++
        return i
    }

    private fun commonSuffix(a: String, b: String, from: Int): Int {
        val edge = minOf(a.length, b.length) - from
        var i = 0
        while (i < edge && a[a.length - 1 - i] == b[b.length - 1 - i]) i++
        return i
    }

    private fun Char.isWordChar(): Boolean = isLetterOrDigit() || this == '_'

    private inline fun push(stack: ArrayDeque<String>, text: String, onAdd: (Int) -> Unit) {
        stack.addLast(text)
        onAdd(text.length)
    }

    /** Держит и число шагов, и объём: самый старый шаг уходит первым. */
    private fun trim(stack: ArrayDeque<String>, chars: Int): Int {
        var total = chars
        while (stack.size > limit || (total > limitChars && stack.size > 1)) {
            total -= stack.removeFirst().length
        }
        return total
    }
}
