package pitcher.android.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Opens a database written by the 2.5 schema (version 2) with the current code. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ShelfMigrationTest {

    private lateinit var context: Context
    private lateinit var dbName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "migrate-${System.nanoTime()}.db"
        val path = context.getDatabasePath(dbName).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE tracks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    source_path TEXT NOT NULL, source_kind TEXT NOT NULL, source_url TEXT,
                    title TEXT NOT NULL, artist TEXT, duration_s REAL NOT NULL,
                    sample_rate INTEGER, tuner_source TEXT, tuner_target TEXT, save_folder TEXT,
                    created_at TEXT NOT NULL DEFAULT (datetime('now'))
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE variants (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    track_id INTEGER NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
                    name TEXT, cents INTEGER NOT NULL, formant INTEGER NOT NULL,
                    engine TEXT NOT NULL, pitch_quality TEXT NOT NULL,
                    section_start REAL, section_end REAL,
                    output_path TEXT NOT NULL, output_format TEXT,
                    src_note TEXT, src_hz REAL, target_note TEXT, target_hz REAL,
                    favorite INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT (datetime('now'))
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE TABLE bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, track_id INTEGER NOT NULL, " +
                    "t REAL NOT NULL, name TEXT, created_at TEXT NOT NULL DEFAULT (datetime('now')))",
            )
            db.execSQL(
                "CREATE TABLE loops (id INTEGER PRIMARY KEY AUTOINCREMENT, track_id INTEGER NOT NULL, " +
                    "start_ms INTEGER NOT NULL, end_ms INTEGER NOT NULL, name TEXT, " +
                    "enabled INTEGER NOT NULL DEFAULT 1, created_at TEXT NOT NULL DEFAULT (datetime('now')))",
            )
            db.execSQL(
                "INSERT INTO tracks (id, source_path, source_kind, title, duration_s) " +
                    "VALUES (1, '/x.m4a', 'file', 'Song', 120)",
            )
            db.execSQL(
                "INSERT INTO variants (id, track_id, name, cents, formant, engine, pitch_quality, " +
                    "output_path, output_format) VALUES " +
                    "(1, 1, 'up', 300, 1, 'sonic', 'quality', 'content://media/1', 'm4a'), " +
                    "(2, 1, NULL, -100, 1, 'sonic', 'quality', '', 'live')",
            )
            db.version = 2
        }
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun existingFilesBecomeRendersAndPitchesSurvive() {
        val repo = ShelfRepository(context, dbName)
        try {
            val variants = repo.listVariants(1)
            assertEquals(listOf(-100, 300), variants.map { it.cents })
            assertEquals(listOf(0, 1), variants.map { it.renderCount })
            val render = repo.listRenders(1).single()
            assertEquals("content://media/1", render.uri)
            assertEquals("m4a", render.format)
            assertEquals("", render.segments)
            assertEquals("up", repo.getVariant(1)!!.name)
        } finally {
            repo.close()
        }
    }
}
