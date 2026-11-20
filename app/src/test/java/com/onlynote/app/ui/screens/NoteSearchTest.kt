package com.onlynote.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteSearchTest {

    @Test
    fun `пустой запрос ничего не ищет`() {
        assertEquals(emptyList<IntRange>(), matchesOf("заметка", ""))
    }

    @Test
    fun `поиск не зависит от регистра`() {
        assertEquals(
            listOf(0 until 5, 12 until 17),
            matchesOf("Заметка про заметку", "ЗАМЕТ"),
        )
    }

    @Test
    fun `вхождения не пересекаются`() {
        // «ааа» и запрос «аа»: после найденного куска поиск продолжается за ним.
        assertEquals(listOf(0 until 2), matchesOf("ааа", "аа"))
    }

    @Test
    fun `смещения считаются по всему тексту`() {
        assertEquals(
            listOf(4 until 7, 8 until 11),
            matchesOf("сто три три", "три"),
        )
    }

    @Test
    fun `запрос из одного знака находит все`() {
        assertEquals(5, matchesOf("ааааа", "а").size)
    }
}
