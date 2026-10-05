package pitcher.android.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class ShelfDb(
    context: Context,
    name: String = "shelf.db",
) : SQLiteOpenHelper(context, name, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE tracks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source_path TEXT NOT NULL,
                source_kind TEXT NOT NULL,
                source_url TEXT,
                title TEXT NOT NULL,
                artist TEXT,
                duration_s REAL NOT NULL,
                sample_rate INTEGER,
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE variants (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                track_id INTEGER NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
                name TEXT,
                cents INTEGER NOT NULL,
                formant INTEGER NOT NULL,
                engine TEXT NOT NULL,
                pitch_quality TEXT NOT NULL,
                section_start REAL,
                section_end REAL,
                output_path TEXT NOT NULL,
                output_format TEXT,
                src_note TEXT,
                src_hz REAL,
                target_note TEXT,
                target_hz REAL,
                favorite INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE bookmarks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                track_id INTEGER NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
                t REAL NOT NULL,
                name TEXT,
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_variants_track ON variants(track_id)")
        db.execSQL("CREATE INDEX idx_bookmarks_track ON bookmarks(track_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 is the first schema; future versions migrate here.
    }

    companion object {
        const val VERSION = 1
    }
}
