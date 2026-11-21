package com.onlynote.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteOrderingTest {

    private fun note(
        body: String,
        color: NoteColor,
        id: Long = body.hashCode().toLong(),
    ): Note = Note(
        id = id,
        body = body,
        color = color,
        archived = false,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun sorted(notes: List<Note>): List<String> =
        notes.sortedWith(NoteOrdering.comparator).map { it.heading }

    @Test
    fun `colors follow the rainbow, titles inside a color go alphabetically`() {
        val notes = listOf(
            note("Яблоко", NoteColor.DEFAULT),
            note("банан", NoteColor.LEMON),
            note("Арбуз", NoteColor.ROSE),
            note("абрикос", NoteColor.LEMON),
            note("Виноград", NoteColor.SAGE),
            note("Пчела", NoteColor.BLUE),
            note("Персик", NoteColor.PEACH),
            note("Серый", NoteColor.GRAY),
            note("Лаванда", NoteColor.LAVENDER),
        )

        assertEquals(
            listOf(
                "Арбуз",     // роза
                "Персик",    // персик
                "абрикос",   // лимон, по алфавиту
                "банан",     // лимон
                "Виноград",  // шалфей
                "Пчела",     // мята
                "Лаванда",   // лаванда
                "Серый",     // песок
                "Яблоко",    // без цвета
            ),
            sorted(notes),
        )
    }

    @Test
    fun `heading is the first non-blank line of the text`() {
        val note = note("\n\n  Первая строка\nвторая", NoteColor.LAVENDER)
        assertEquals("Первая строка", note.heading)
    }

    @Test
    fun `notes without any text go last inside their color`() {
        val notes = listOf(
            note("", NoteColor.LEMON),
            note("Айва", NoteColor.LEMON),
            note("   ", NoteColor.LEMON),
        )
        val result = notes.sortedWith(NoteOrdering.comparator).map { it.heading }
        assertEquals("Айва", result.first())
        assertEquals(listOf("", ""), result.drop(1))
    }

    @Test
    fun `color ranks keep rainbow order`() {
        val expected = listOf(
            NoteColor.ROSE,
            NoteColor.PEACH,
            NoteColor.LEMON,
            NoteColor.SAGE,
            NoteColor.BLUE,
            NoteColor.LAVENDER,
            NoteColor.GRAY,
            NoteColor.DEFAULT,
        )
        assertEquals(expected, NoteColor.rainbowOrder)
    }

    @Test
    fun `every color has its own id and rank`() {
        assertEquals(NoteColor.entries.size, NoteColor.entries.map { it.id }.toSet().size)
        assertEquals(NoteColor.entries.size, NoteColor.entries.map { it.rank }.toSet().size)
        assertEquals(NoteColor.DEFAULT, NoteColor.fromId("неизвестный"))
        assertEquals(NoteColor.DEFAULT, NoteColor.fromId("sky"))
        assertEquals(NoteColor.BLUE, NoteColor.fromId("mint"))
    }

    @Test
    fun `backup json survives a round trip`() {
        val data = BackupData(
            createdAt = 123L,
            notes = listOf(
                BackupNote(
                    body = "Сфинкс\nпервая строка",
                    color = "rose",
                    archived = true,
                    createdAt = 2L,
                    updatedAt = 3L,
                ),
            ),
        )
        val restored = BackupData.fromJson(data.toJson())
        assertEquals(data, restored)
    }

    @Test
    fun `backup of version 1 without archive is read as work notes`() {
        val legacy = """
            {
              "format": "onlynote-backup",
              "version": 1,
              "createdAt": 5,
              "folders": [{"name": "Кошки", "createdAt": 1}],
              "notes": [{"folder": 0, "body": "Сфинкс", "color": "rose", "createdAt": 2, "updatedAt": 3}]
            }
        """.trimIndent()
        val restored = BackupData.fromJson(legacy)
        assertEquals(1, restored?.notes?.size)
        assertEquals("Сфинкс", restored?.notes?.first()?.body)
        assertEquals(false, restored?.notes?.first()?.archived)
    }

    @Test
    fun `foreign json is rejected`() {
        assertEquals(null, BackupData.fromJson("{\"hello\":1}"))
        assertEquals(null, BackupData.fromJson("не json"))
    }
}
