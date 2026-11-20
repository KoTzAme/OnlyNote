package com.onlynote.app.ui.components

/**
 * История правок «как в Word»: отмена и возврат.
 *
 * Подряд идущие правки без паузы склеиваются в один шаг, иначе откат пришлось бы
 * нажимать по букве.
 *
 * Снимок — это весь текст, а заметки бывают по сто тысяч знаков. Поэтому история
 * ограничена дважды: числом шагов ([limit]) и общим объёмом текста ([limitChars]).
 * Без второго предела шестьдесят шагов в большой заметке — это больше десяти
 * мегабайт памяти на копии строк, и приложение растёт на глазах.
 */
class EditHistory(
    private val limit: Int = 60,
    private val coalesceMs: Long = 900L,
    private val limitChars: Int = 1_200_000,
) {
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var undoChars = 0
    private var redoChars = 0
    private var lastRecordedAt = 0L

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Стоит ли вообще готовить снимок: между шагами должна пройти пауза. */
    fun shouldRecord(now: Long = System.currentTimeMillis()): Boolean =
        now - lastRecordedAt >= coalesceMs

    /** [previous] — весь текст до правки. */
    fun record(previous: String, now: Long = System.currentTimeMillis()) {
        if (now - lastRecordedAt < coalesceMs) {
            lastRecordedAt = now
            return
        }
        lastRecordedAt = now
        push(undoStack, previous) { undoChars += it }
        undoChars = trim(undoStack, undoChars)
        redoStack.clear()
        redoChars = 0
    }

    /** [current] откладывается, чтобы «вернуть» было куда вернуться. */
    fun undo(current: String, now: Long = System.currentTimeMillis()): String? {
        lastRecordedAt = now
        val previous = undoStack.removeLastOrNull() ?: return null
        undoChars -= previous.length
        push(redoStack, current) { redoChars += it }
        redoChars = trim(redoStack, redoChars)
        return previous
    }

    fun redo(current: String, now: Long = System.currentTimeMillis()): String? {
        lastRecordedAt = now
        val next = redoStack.removeLastOrNull() ?: return null
        redoChars -= next.length
        push(undoStack, current) { undoChars += it }
        undoChars = trim(undoStack, undoChars)
        return next
    }

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
