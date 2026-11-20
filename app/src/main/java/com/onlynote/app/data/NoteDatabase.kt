package com.onlynote.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Локальная база: две таблицы, без синхронизации.
 * Папка без заметок не имеет смысла — такие подчищаются в [NoteStore].
 */
internal class NoteDatabase(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE folders (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE notes (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                folder_id INTEGER NOT NULL REFERENCES folders(_id) ON DELETE CASCADE,
                title TEXT NOT NULL DEFAULT '',
                body TEXT NOT NULL DEFAULT '',
                color TEXT NOT NULL DEFAULT 'default',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_notes_folder ON notes(folder_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Заголовок стал первой строкой текста: склеиваем старые поля.
            db.execSQL(
                """
                UPDATE notes SET
                    body = CASE
                        WHEN title = '' THEN body
                        WHEN body = '' THEN title
                        ELSE title || char(10) || body
                    END,
                    title = ''
                """.trimIndent(),
            )
        }
    }

    companion object {
        const val NAME = "onlynote.db"
        const val VERSION = 2
    }
}
