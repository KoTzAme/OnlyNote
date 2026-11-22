package com.onlynote.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Отмена идёт по словам, а не по времени: шаг — слово целиком вместе с тем
 * разделителем, который его закрыл.
 */
class EditHistoryTest {

    @Test
    fun `набор внутри слова — один шаг`() {
        val history = EditHistory()
        var text = ""
        for (symbol in "привет") {
            val next = text + symbol
            history.onChanged(text, next)
            text = next
        }
        // Шаг — всё слово: откат возвращает текст до него.
        assertEquals("", history.undo(text))
    }

    @Test
    fun `слова откатываются по одному, пробел уходит со своим словом`() {
        val history = EditHistory()
        var text = ""
        for (symbol in "привет мир") {
            val next = text + symbol
            history.onChanged(text, next)
            text = next
        }
        assertEquals("привет ", history.undo(text)?.also { text = it })
        assertEquals("", history.undo(text))
    }

    @Test
    fun `стирание тоже склеивается по слову`() {
        val history = EditHistory()
        var text = "привет"
        while (text.isNotEmpty()) {
            val next = text.dropLast(1)
            history.onChanged(text, next)
            text = next
        }
        // Стирали по букве, но слово ушло одним шагом: откат возвращает его.
        assertEquals("привет", history.undo(""))
    }

    @Test
    fun `правка в другом месте начинает новый шаг`() {
        val history = EditHistory()
        history.onChanged("привет мир", "привет мир!")
        // Палец перескочил в начало и там напечатана буква.
        history.onChanged("привет мир!", "Хпривет мир!")
        assertEquals("привет мир!", history.undo("Хпривет мир!"))
        assertEquals("привет мир", history.undo("привет мир!"))
    }

    @Test
    fun `после отката следующая буква начинает новый шаг`() {
        val history = EditHistory()
        history.onChanged("", "слово")
        val restored = history.undo("слово")
        assertEquals("", restored)
        assertNull(history.undo(""))
        history.onChanged("", "новое")
        assertEquals("", history.undo("новое"))
    }

    @Test
    fun `возврат идёт теми же шагами`() {
        val history = EditHistory()
        var text = ""
        for (symbol in "раз два") {
            val next = text + symbol
            history.onChanged(text, next)
            text = next
        }
        val before = history.undo(text)!!
        val back = history.redo(before)!!
        assertEquals("раз два", back)
    }
}
