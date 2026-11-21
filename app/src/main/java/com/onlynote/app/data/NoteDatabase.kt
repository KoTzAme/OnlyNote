package com.onlynote.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Локальная база: одна таблица, без синхронизации.
 *
 * Папок больше нет: категоризация держится на цвете, а два главных экрана —
 * это флаг [archived] у самой заметки, а не вложенность.
 */
internal class NoteDatabase(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE notes (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL DEFAULT '',
                body TEXT NOT NULL DEFAULT '',
                color TEXT NOT NULL DEFAULT 'default',
                archived INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
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
        if (oldVersion < 3) {
            // Папки убраны: заметки из них переезжают на рабочий экран.
            // Таблица пересобирается целиком, потому что из неё уходит
            // внешний ключ на folders и добавляется флаг архива.
            db.execSQL(
                """
                CREATE TABLE notes_v3 (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL DEFAULT '',
                    body TEXT NOT NULL DEFAULT '',
                    color TEXT NOT NULL DEFAULT 'default',
                    archived INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO notes_v3 (_id, title, body, color, archived, created_at, updated_at)
                SELECT _id, title, body, color, 0, created_at, updated_at FROM notes
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE notes")
            db.execSQL("DROP TABLE IF EXISTS folders")
            db.execSQL("ALTER TABLE notes_v3 RENAME TO notes")
        }
    }

    companion object {
        const val NAME = "onlynote.db"
        const val VERSION = 3
    }
}
