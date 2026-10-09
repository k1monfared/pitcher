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
        createRenders(db)
    }

    /** Rendered files of a saved pitch: one row per saved name, format, and loop set. */
    private fun createRenders(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS renders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                variant_id INTEGER NOT NULL REFERENCES variants(id) ON DELETE CASCADE,
                uri TEXT NOT NULL,
                format TEXT NOT NULL,
                file_name TEXT NOT NULL,
                segments TEXT NOT NULL DEFAULT '',
                speed REAL NOT NULL DEFAULT 1.0,
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_renders_variant ON renders(variant_id)")
    }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean =
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val nameIdx = c.getColumnIndexOrThrow("name")
            generateSequence { if (c.moveToNext()) c.getString(nameIdx) else null }.any { it == column }
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
        if (oldVersion < 3) {
            createRenders(db)
            // Before v3 a pitch held at most one file in output_path. Its file
            // name was not stored, so the next save with any name renders anew.
            db.execSQL(
                """
                INSERT INTO renders (variant_id, uri, format, file_name, segments)
                SELECT id, output_path, IFNULL(output_format, ''), '',
                       CASE WHEN section_start IS NULL THEN ''
                            ELSE CAST(CAST(section_start * 1000 AS INTEGER) AS TEXT) || '-' ||
                                 CAST(CAST(section_end * 1000 AS INTEGER) AS TEXT) END
                FROM variants
                WHERE output_path LIKE 'content://%' OR output_path LIKE 'file://%'
                """.trimIndent(),
            )
        }
        // Version 3 renders had no speed; they all played at 1x.
        if (oldVersion < 4 && !hasColumn(db, "renders", "speed")) {
            db.execSQL("ALTER TABLE renders ADD COLUMN speed REAL NOT NULL DEFAULT 1.0")
        }
    }

    companion object {
        const val VERSION = 4
    }
}
