package com.onlynote.app.data

import android.content.ContentValues
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/**
 * Всё хранилище приложения: папки и заметки.
 *
 * Папка без заметок не имеет смысла, поэтому такие папки подчищаются:
 * удалили последнюю заметку — папка исчезла вместе с ней.
 */
class NoteStore(context: Context) {

    private val helper = NoteDatabase(context.applicationContext)
    private val collator = Collator.getInstance(Locale("ru", "RU"))

    private val _snapshot = MutableStateFlow(AppSnapshot())
    val snapshot: StateFlow<AppSnapshot> = _snapshot.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        purgeEmptyFolders()
        _snapshot.value = readAll()
    }

    // ---------- папки ----------

    suspend fun createFolder(name: String): Long = withContext(Dispatchers.IO) {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "folder name cannot be blank" }
        val values = ContentValues().apply {
            put("name", clean)
            put("created_at", System.currentTimeMillis())
        }
        val id = helper.writableDatabase.insert("folders", null, values)
        refresh()
        id
    }

    suspend fun renameFolder(folderId: Long, name: String) = withContext(Dispatchers.IO) {
        val clean = name.trim()
        if (clean.isEmpty()) return@withContext
        val values = ContentValues().apply { put("name", clean) }
        helper.writableDatabase.update("folders", values, "_id = ?", arrayOf(folderId.toString()))
        refresh()
    }

    // ---------- заметки ----------

    suspend fun createNote(folderId: Long, color: NoteColor = NoteColor.DEFAULT): Long =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val values = ContentValues().apply {
                put("folder_id", folderId)
                put("title", "")
                put("body", "")
                put("color", color.id)
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
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            if (body != null) put("body", body)
            if (color != null) put("color", color.id)
            put("updated_at", now)
        }
        helper.writableDatabase.update("notes", values, "_id = ?", arrayOf(noteId.toString()))
        patchNote(noteId) { note ->
            note.copy(
                body = body ?: note.body,
                color = color ?: note.color,
                updatedAt = now,
            )
        }
    }

    suspend fun deleteNote(noteId: Long) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("notes", "_id = ?", arrayOf(noteId.toString()))
        purgeEmptyFolders()
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
            db.delete("folders", null, null)
            val folderIds = LongArray(data.folders.size)
            data.folders.forEachIndexed { index, folder ->
                val values = ContentValues().apply {
                    put("name", folder.name)
                    put("created_at", folder.createdAt)
                }
                folderIds[index] = db.insert("folders", null, values)
            }
            data.notes.forEach { note ->
                if (note.folderIndex in folderIds.indices) {
                    val values = ContentValues().apply {
                        put("folder_id", folderIds[note.folderIndex])
                        put("title", "")
                        put("body", note.body)
                        put("color", NoteColor.fromId(note.color).id)
                        put("created_at", note.createdAt)
                        put("updated_at", note.updatedAt)
                    }
                    db.insert("notes", null, values)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        purgeEmptyFolders()
        refresh()
    }

    // ---------- чтение ----------

    private fun purgeEmptyFolders() {
        helper.writableDatabase.execSQL(
            "DELETE FROM folders WHERE _id NOT IN (SELECT folder_id FROM notes)",
        )
    }

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

        val folders = mutableListOf<Folder>()
        db.rawQuery("SELECT _id, name, created_at FROM folders", null).use { cursor ->
            while (cursor.moveToNext()) {
                folders += Folder(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    createdAt = cursor.getLong(2),
                )
            }
        }
        folders.sortWith { a, b -> collator.compare(a.name, b.name) }

        val notes = mutableListOf<Note>()
        db.rawQuery(
            "SELECT _id, folder_id, body, color, created_at, updated_at FROM notes",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                notes += Note(
                    id = cursor.getLong(0),
                    folderId = cursor.getLong(1),
                    body = cursor.getString(2),
                    color = NoteColor.fromId(cursor.getString(3)),
                    createdAt = cursor.getLong(4),
                    updatedAt = cursor.getLong(5),
                )
            }
        }
        notes.sortWith(NoteOrdering.comparator)

        return AppSnapshot(folders = folders, notes = notes)
    }

    private fun refresh() {
        _snapshot.value = readAll()
    }

    private fun AppSnapshot.toBackupData(): BackupData {
        val indexById = folders.withIndex().associate { (index, folder) -> folder.id to index }
        return BackupData(
            createdAt = System.currentTimeMillis(),
            folders = folders.map { BackupFolder(name = it.name, createdAt = it.createdAt) },
            notes = notes
                .filter { indexById.containsKey(it.folderId) }
                .map {
                    BackupNote(
                        folderIndex = indexById.getValue(it.folderId),
                        body = it.body,
                        color = it.color.id,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt,
                    )
                },
        )
    }
}
