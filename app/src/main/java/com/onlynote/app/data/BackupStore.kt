package com.onlynote.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Одна резервная копия, доступная для импорта.
 *  Показываем только одну строку — без дат курсивом и размеров файла. */
data class BackupEntry(
    val title: String,
    val uri: Uri? = null,
    val file: File? = null,
)

/**
 * Резервные копии: файл JSON со всеми папками и заметками.
 *
 * Автоматическая копия — одна, лежит внутри приложения и перезаписывается
 * в начале каждого дня. Экспорт кладёт именованный файл в «Документы».
 */
class BackupStore(context: Context) {

    private val appContext = context.applicationContext
    private val store = NoteStore(appContext)

    private val autoFile: File
        get() = File(appContext.filesDir, "backup/onlynote-auto.json")

    // ---------- автокопия ----------

    /** Раз в календарный день: один и тот же файл, без роста папки. */
    suspend fun ensureDailyAutoBackup() = withContext(Dispatchers.IO) {
        runCatching {
            val file = autoFile
            // Пустой файл за сегодня не считаем готовой копией: он появился,
            // когда заметок ещё не было, и импорт из него стирает всё.
            val fresh = file.exists() &&
                file.length() > EMPTY_BACKUP_BYTES &&
                isSameDay(file.lastModified(), System.currentTimeMillis())
            if (fresh) return@runCatching
            val data = store.readForBackup()
            // Нечего сохранять — не пишем и пустой каркас.
            if (data.notes.isEmpty()) return@runCatching
            file.parentFile?.mkdirs()
            file.writeText(data.toJson())
        }
    }

    fun autoBackupEntry(): BackupEntry? {
        val file = autoFile
        // Пустую копию не предлагаем: восстанавливать из неё нечего,
        // а импорт стёр бы все заметки.
        if (!file.exists() || file.length() <= EMPTY_BACKUP_BYTES) return null
        return BackupEntry(
            title = "Автоматическая копия",
            file = file,
        )
    }

    // ---------- экспорт ----------

    /** @return имя созданного файла или null, если записать не удалось. */
    suspend fun export(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val data = store.readForBackup()
            val json = data.toJson()
            val name = "onlynote-" + SimpleDateFormat(FILE_STAMP, Locale.US).format(Date()) + ".json"
            val written = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeWithMediaStore(name, json)
            } else {
                writeToDocumentsDir(name, json)
            }
            if (written) {
                // Новая копия вытесняет самую старую: папка не растёт бесконечно.
                pruneOldExports()
                name
            } else {
                null
            }
        }.getOrNull()
    }

    /**
     * Держим не больше [MAX_EXPORTS] копий в «Документах».
     *
     * Имя файла кончается штампом времени, поэтому порядок по имени — это
     * порядок по времени: самая старая копия и уходит первой. Автоматическая
     * копия сюда не входит, она одна и живёт внутри приложения.
     */
    private fun pruneOldExports() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                pruneMediaStore()
            } else {
                pruneLegacyExports()
            }
        }
    }

    private fun pruneMediaStore() {
        val resolver = appContext.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = Environment.DIRECTORY_DOCUMENTS + "/" + FOLDER_NAME
        val found = mutableListOf<Pair<String, Long>>()
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf("$relativePath/"),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1) ?: continue
                if (!name.endsWith(".json", ignoreCase = true)) continue
                found += name to cursor.getLong(0)
            }
        }
        found.sortedByDescending { it.first }.drop(MAX_EXPORTS).forEach { (_, id) ->
            resolver.delete(
                android.content.ContentUris.withAppendedId(collection, id),
                null,
                null,
            )
        }
    }

    private fun pruneLegacyExports() {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            FOLDER_NAME,
        )
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".json", true) }
            ?: return
        files.sortedByDescending { it.name }.drop(MAX_EXPORTS).forEach { it.delete() }
    }

    // ---------- список для импорта ----------

    suspend fun documentsEntries(): List<BackupEntry> = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                queryMediaStore()
            } else {
                legacyDocumentsEntries()
            }
        }.getOrDefault(emptyList())
    }

    // ---------- чтение и восстановление ----------

    suspend fun read(entry: BackupEntry): BackupData? = withContext(Dispatchers.IO) {
        runCatching {
            val raw = entry.uri?.let { uri ->
                appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            } ?: entry.file?.takeIf { it.exists() }?.readText()
            raw?.let { BackupData.fromJson(it) }
        }.getOrNull()
    }

    // ---------- внутреннее ----------

    private fun writeWithMediaStore(name: String, json: String): Boolean {
        val resolver = appContext.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = Environment.DIRECTORY_DOCUMENTS + "/" + FOLDER_NAME

        // Такое же имя уже есть — перезаписываем, а не плодим копии.
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf("$relativePath/", name),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                resolver.delete(
                    android.content.ContentUris.withAppendedId(collection, id),
                    null,
                    null,
                )
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }
        val uri = resolver.insert(collection, values) ?: return false
        resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } ?: return false
        return true
    }

    private fun writeToDocumentsDir(name: String, json: String): Boolean {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            FOLDER_NAME,
        )
        if (!dir.exists() && !dir.mkdirs()) return false
        File(dir, name).writeText(json)
        return true
    }

    private fun queryMediaStore(): List<BackupEntry> {
        val resolver = appContext.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = Environment.DIRECTORY_DOCUMENTS + "/" + FOLDER_NAME
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )

        val entries = mutableListOf<BackupEntry>()
        resolver.query(
            collection,
            projection,
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf("$relativePath/"),
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val displayName = cursor.getString(1) ?: continue
                if (!displayName.endsWith(".json", ignoreCase = true)) continue
                val uri = android.content.ContentUris.withAppendedId(collection, id)

                // MediaStore не всегда сразу знает дату — добираем её из имени файла.
                val modified = (cursor.getLong(2) * 1000L).takeIf { it > 0 }
                    ?: dateFromName(displayName)

                entries += BackupEntry(
                    title = formatTime(modified),
                    uri = uri,
                )
            }
        }
        return entries
    }

    /** Имя файла несёт дату экспорта: onlynote-2026-11-13_15-06.json. */
    private fun dateFromName(displayName: String): Long {
        val raw = displayName.removeSuffix(".json").removePrefix("onlynote-")
        return runCatching {
            SimpleDateFormat(FILE_STAMP, Locale.US).parse(raw)?.time
        }.getOrNull() ?: 0L
    }

    private fun legacyDocumentsEntries(): List<BackupEntry> {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            FOLDER_NAME,
        )
        if (!dir.exists()) return emptyList()
        return dir.listFiles { file -> file.isFile && file.name.endsWith(".json", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map {
                BackupEntry(
                    title = formatTime(it.lastModified()),
                    file = it,
                )
            }
            .orEmpty()
    }

    private fun isSameDay(first: Long, second: Long): Boolean {
        val a = Calendar.getInstance().apply { timeInMillis = first }
        val b = Calendar.getInstance().apply { timeInMillis = second }
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    private fun formatTime(millis: Long): String =
        SimpleDateFormat("d MMM HH:mm", Locale("ru", "RU")).format(Date(millis))

    companion object {
        /** Папка для экспортированных копий: Documents/OnlyNote backup. */
        private const val FOLDER_NAME = "OnlyNote backup"

        /** Меньше этого файл автокопии — значит в нём нет ни одной заметки. */
        private const val EMPTY_BACKUP_BYTES = 200L

        /** Штамп времени в имени файла — он же разбирается обратно при чтении. */
        private const val FILE_STAMP = "yyyy-MM-dd_HH-mm"

        /** Сколько экспортированных копий держим в «Документах». */
        private const val MAX_EXPORTS = 4
    }
}
