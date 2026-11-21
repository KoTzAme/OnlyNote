package com.onlynote.app.data

import android.content.ContentValues
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Всё хранилище приложения: заметки и их принадлежность одному из двух экранов.
 *
 * Папок нет: главная категоризация — цвет. Экран (рабочий или архив) — это
 * один флаг у заметки, поэтому перемещение между экранами — обычная правка.
 */
class NoteStore(context: Context) {

    private val helper = NoteDatabase(context.applicationContext)

    private val _snapshot = MutableStateFlow(AppSnapshot())
    val snapshot: StateFlow<AppSnapshot> = _snapshot.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        refresh()
    }

    // ---------- заметки ----------

    /**
     * Новая заметка появляется на том экране, откуда её создали: [archived]
     * приходит из того экрана, на котором был долгий тап.
     */
    suspend fun createNote(archived: Boolean, color: NoteColor = NoteColor.DEFAULT): Long =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val values = ContentValues().apply {
                put("title", "")
                put("body", "")
                put("color", color.id)
                put("archived", if (archived) 1 else 0)
                put("created_at", now)
                put("updated_at", now)
            }
            val id = helper.writableDatabase.insert("notes", null, values)
            refresh()
            id
        }

    /**
     * Правка заметки. Это самый частый вызов (автосохранение при наборе),
     * поэтому после записи в базу снимок правится в памяти, без полного перечитывания.
     */
    suspend fun updateNote(
        noteId: Long,
        body: String? = null,
        color: NoteColor? = null,
        archived: Boolean? = null,
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            if (body != null) put("body", body)
            if (color != null) put("color", color.id)
            if (archived != null) put("archived", if (archived) 1 else 0)
            put("updated_at", now)
        }
        helper.writableDatabase.update("notes", values, "_id = ?", arrayOf(noteId.toString()))
        patchNote(noteId) { note ->
            note.copy(
                body = body ?: note.body,
                color = color ?: note.color,
                archived = archived ?: note.archived,
                updatedAt = now,
            )
        }
    }

    suspend fun deleteNote(noteId: Long) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("notes", "_id = ?", arrayOf(noteId.toString()))
        refresh()
    }

    // ---------- резервные копии ----------

    suspend fun readForBackup(): BackupData = withContext(Dispatchers.IO) {
        readAll().toBackupData()
    }

    suspend fun replaceAll(data: BackupData) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("notes", null, null)
            data.notes.forEach { note ->
                val values = ContentValues().apply {
                    put("title", "")
                    put("body", note.body)
                    put("color", NoteColor.fromId(note.color).id)
                    put("archived", if (note.archived) 1 else 0)
                    put("created_at", note.createdAt)
                    put("updated_at", note.updatedAt)
                }
                db.insert("notes", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refresh()
    }

    // ---------- чтение ----------

    /**
     * Заметка правится в памяти после записи в базу. Пересортировка нужна только
     * когда меняется то, по чему сортируем (первая строка или цвет): иначе каждое
     * автосохранение заметки на сто тысяч символов тянуло бы сортировку всего списка.
     */
    private fun patchNote(noteId: Long, transform: (Note) -> Note) {
        val current = _snapshot.value
        val index = current.notes.indexOfFirst { it.id == noteId }
        if (index < 0) {
            _snapshot.value = current.copy(notes = readAll().notes)
            return
        }
        val old = current.notes[index]
        val updated = transform(old)
        val orderChanged = updated.heading != old.heading || updated.color != old.color
        val notes = if (orderChanged) {
            current.notes.toMutableList()
                .also { it[index] = updated }
                .sortedWith(NoteOrdering.comparator)
        } else {
            current.notes.toMutableList().also { it[index] = updated }
        }
        _snapshot.value = current.copy(notes = notes)
    }

    private fun readAll(): AppSnapshot {
        val db = helper.readableDatabase
        val notes = mutableListOf<Note>()
        db.rawQuery(
            "SELECT _id, body, color, archived, created_at, updated_at FROM notes",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                notes += Note(
                    id = cursor.getLong(0),
                    body = cursor.getString(1),
                    color = NoteColor.fromId(cursor.getString(2)),
                    archived = cursor.getInt(3) != 0,
                    createdAt = cursor.getLong(4),
                    updatedAt = cursor.getLong(5),
                )
            }
        }
        notes.sortWith(NoteOrdering.comparator)
        return AppSnapshot(notes = notes)
    }

    private fun refresh() {
        _snapshot.value = readAll()
    }

    private fun AppSnapshot.toBackupData(): BackupData = BackupData(
        createdAt = System.currentTimeMillis(),
        notes = notes.map {
            BackupNote(
                body = it.body,
                color = it.color.id,
                archived = it.archived,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
            )
        },
    )
}
