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
                tuner_source TEXT,
                tuner_target TEXT,
                save_folder TEXT,
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
        db.execSQL(
            """
            CREATE TABLE loops (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                track_id INTEGER NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
                start_ms INTEGER NOT NULL,
                end_ms INTEGER NOT NULL,
                name TEXT,
                enabled INTEGER NOT NULL DEFAULT 1,
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_variants_track ON variants(track_id)")
        db.execSQL("CREATE INDEX idx_bookmarks_track ON bookmarks(track_id)")
        db.execSQL("CREATE INDEX idx_loops_track ON loops(track_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE tracks ADD COLUMN tuner_source TEXT")
            db.execSQL("ALTER TABLE tracks ADD COLUMN tuner_target TEXT")
            db.execSQL("ALTER TABLE tracks ADD COLUMN save_folder TEXT")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS loops (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    track_id INTEGER NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
                    start_ms INTEGER NOT NULL,
                    end_ms INTEGER NOT NULL,
                    name TEXT,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT NOT NULL DEFAULT (datetime('now'))
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_loops_track ON loops(track_id)")
        }
    }

    companion object {
        const val VERSION = 2
    }
}
