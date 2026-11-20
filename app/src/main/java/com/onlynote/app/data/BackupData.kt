package com.onlynote.app.data

import org.json.JSONArray
import org.json.JSONObject

data class BackupFolder(val name: String, val createdAt: Long)

data class BackupNote(
    val folderIndex: Int,
    val body: String,
    val color: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Всё содержимое приложения в переносимом виде. */
data class BackupData(
    val createdAt: Long,
    val folders: List<BackupFolder>,
    val notes: List<BackupNote>,
) {
    fun toJson(): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("createdAt", createdAt)
        put(
            "folders",
            JSONArray().apply {
                folders.forEach { folder ->
                    put(
                        JSONObject().apply {
                            put("name", folder.name)
                            put("createdAt", folder.createdAt)
                        },
                    )
                }
            },
        )
        put(
            "notes",
            JSONArray().apply {
                notes.forEach { note ->
                    put(
                        JSONObject().apply {
                            put("folder", note.folderIndex)
                            put("body", note.body)
                            put("color", note.color)
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
        private const val VERSION = 1

        /** @return null, если файл не похож на резервную копию OnlyNote. */
        fun fromJson(raw: String): BackupData? {
            val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
            if (root.optString("format") != FORMAT) return null

            val foldersJson = root.optJSONArray("folders") ?: JSONArray()
            val folders = buildList {
                for (index in 0 until foldersJson.length()) {
                    val item = foldersJson.optJSONObject(index) ?: continue
                    val name = item.optString("name").trim()
                    if (name.isEmpty()) continue
                    add(BackupFolder(name = name, createdAt = item.optLong("createdAt")))
                }
            }

            val notesJson = root.optJSONArray("notes") ?: JSONArray()
            val notes = buildList {
                for (index in 0 until notesJson.length()) {
                    val item = notesJson.optJSONObject(index) ?: continue
                    val folderIndex = item.optInt("folder", -1)
                    if (folderIndex !in folders.indices) continue
                    add(
                        BackupNote(
                            folderIndex = folderIndex,
                            body = item.optString("body"),
                            color = item.optString("color", "default"),
                            createdAt = item.optLong("createdAt"),
                            updatedAt = item.optLong("updatedAt"),
                        ),
                    )
                }
            }

            return BackupData(
                createdAt = root.optLong("createdAt"),
                folders = folders,
                notes = notes,
            )
        }
    }
}
