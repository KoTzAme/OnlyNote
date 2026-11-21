package com.onlynote.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onlynote.app.data.AppSnapshot
import com.onlynote.app.data.BackupEntry
import com.onlynote.app.data.BackupStore
import com.onlynote.app.data.NoteColor
import com.onlynote.app.data.NoteStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class OnlyNoteViewModel(application: Application) : AndroidViewModel(application) {

    private val store = NoteStore(application)
    private val backups = BackupStore(application)

    val snapshot: StateFlow<AppSnapshot> = store.snapshot

    init {
        viewModelScope.launch {
            store.load()
            // Автокопия обновляется в начале каждого дня и всегда одна.
            backups.ensureDailyAutoBackup()
        }
    }

    /** Заметка создаётся на том экране, откуда её позвали: рабочий или архив. */
    fun createNote(archived: Boolean, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(store.createNote(archived)) }
    }

    fun saveNote(noteId: Long, body: String) {
        viewModelScope.launch { store.updateNote(noteId, body = body) }
    }

    fun setColor(noteId: Long, color: NoteColor) {
        viewModelScope.launch { store.updateNote(noteId, color = color) }
    }

    /** Перемещение между двумя главными экранами. */
    fun setArchived(noteId: Long, archived: Boolean) {
        viewModelScope.launch { store.updateNote(noteId, archived = archived) }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch { store.deleteNote(noteId) }
    }

    // ---------- резервные копии ----------

    fun exportBackup(onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(backups.export()) }
    }

    fun loadBackupEntries(onResult: (BackupEntry?, List<BackupEntry>) -> Unit) {
        viewModelScope.launch {
            onResult(backups.autoBackupEntry(), backups.documentsEntries())
        }
    }

    fun importBackup(entry: BackupEntry, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val data = backups.read(entry)
            if (data == null) {
                onResult(false)
                return@launch
            }
            // Восстановление идёт через свой store: он же держит снимок экрана.
            // Раньше импорт писал в базу через второй экземпляр хранилища, снимок
            // не обновлялся — на экране оставались старые заметки, а в базе уже
            // лежали новые. Это и выглядело как «импорт ничего не сделал».
            store.replaceAll(data)
            onResult(true)
        }
    }
}
