package com.onlynote.app.data

import org.json.JSONArray
import org.json.JSONObject

data class BackupNote(
    val body: String,
    val color: String,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Всё содержимое приложения в переносимом виде.
 *
 * Копии версии 1 знали папки: у заметки там был `folder`, а рядом лежал массив
 * `folders`. Такие файлы читаются по-прежнему — структура просто игнорируется,
 * заметки встают на рабочий экран. Свои копии пишутся без папок.
 */
data class BackupData(
    val createdAt: Long,
    val notes: List<BackupNote>,
) {
    fun toJson(): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("createdAt", createdAt)
        put(
            "notes",
            JSONArray().apply {
                notes.forEach { note ->
                    put(
                        JSONObject().apply {
                            put("body", note.body)
                            put("color", note.color)
                            put("archived", note.archived)
                            put("createdAt", note.createdAt)
                            put("updatedAt", note.updatedAt)
                        },
                    )
                }
            },
        )
    }.toString(2)

    companion object {
        private const val FORMAT = "onlynote-backup"
        private const val VERSION = 2

        /** @return null, если файл не похож на резервную копию OnlyNote. */
        fun fromJson(raw: String): BackupData? {
            val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
            if (root.optString("format") != FORMAT) return null
            // Копия версии 1 без папок бессмысленна: заметок в ней нет.
            val hasNotes = root.has("notes")
            if (!hasNotes) return null

            val notesJson = root.optJSONArray("notes") ?: JSONArray()
            val notes = buildList {
                for (index in 0 until notesJson.length()) {
                    val item = notesJson.optJSONObject(index) ?: continue
                    add(
                        BackupNote(
                            body = item.optString("body"),
                            color = item.optString("color", "default"),
                            // В старых копиях архива не было — всё на рабочем экране.
                            archived = item.optBoolean("archived", false),
                            createdAt = item.optLong("createdAt"),
                            updatedAt = item.optLong("updatedAt"),
                        ),
                    )
                }
            }

            return BackupData(
                createdAt = root.optLong("createdAt"),
                notes = notes,
            )
        }
    }
}
