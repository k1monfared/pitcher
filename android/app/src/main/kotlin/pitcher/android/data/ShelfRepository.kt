package pitcher.android.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

class ShelfRepository(
    context: Context,
    dbName: String = "shelf.db",
) {
    private val helper = ShelfDb(context.applicationContext, dbName)

    private fun db(): SQLiteDatabase = helper.writableDatabase

    fun addTrack(
        sourcePath: String,
        sourceKind: String,
        sourceUrl: String?,
        title: String,
        artist: String?,
        durationS: Double,
        sampleRate: Int?,
    ): Long {
        val values = ContentValues().apply {
            put("source_path", sourcePath)
            put("source_kind", sourceKind)
            put("source_url", sourceUrl)
            put("title", title)
            put("artist", artist?.takeIf { it.isNotBlank() })
            put("duration_s", durationS)
            put("sample_rate", sampleRate)
        }
        return db().insert("tracks", null, values)
    }

    fun listTracks(): List<Track> {
        val sql =
            """
            SELECT t.id, t.source_path, t.source_kind, t.source_url, t.title, t.artist,
                   t.duration_s, t.sample_rate, t.created_at,
                   (SELECT COUNT(*) FROM variants v WHERE v.track_id = t.id) AS variant_count
            FROM tracks t ORDER BY t.created_at DESC, t.id DESC
            """.trimIndent()
        return db().rawQuery(sql, null).use { c ->
            c.mapAll { readTrack(it) }
        }
    }

    fun getTrack(id: Long): Track? {
        val sql =
            """
            SELECT t.id, t.source_path, t.source_kind, t.source_url, t.title, t.artist,
                   t.duration_s, t.sample_rate, t.created_at,
                   (SELECT COUNT(*) FROM variants v WHERE v.track_id = t.id) AS variant_count
            FROM tracks t WHERE t.id = ?
            """.trimIndent()
        return db().rawQuery(sql, arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) readTrack(c) else null
        }
    }

    fun findTrackBySource(sourcePath: String): Long? {
        return db()
            .rawQuery("SELECT id FROM tracks WHERE source_path = ? LIMIT 1", arrayOf(sourcePath))
            .use { c -> if (c.moveToFirst()) c.getLong(0) else null }
    }

    fun renameTrack(id: Long, title: String?, artist: String?) {
        val values = ContentValues()
        title?.let { values.put("title", it) }
        artist?.let { values.put("artist", it.takeIf { a -> a.isNotBlank() }) }
        if (values.size() == 0) return
        db().update("tracks", values, "id = ?", arrayOf(id.toString()))
    }

    fun deleteTrack(id: Long) {
        db().delete("tracks", "id = ?", arrayOf(id.toString()))
    }

    fun deleteTrackWithFiles(id: Long, dataDir: File): Boolean {
        val track = getTrack(id) ?: return false
        val variants = listVariants(id)
        deleteTrack(id)
        variants.forEach { runCatching { File(it.outputPath).delete() } }
        val source = File(track.sourcePath)
        if (source.absolutePath.startsWith(dataDir.absolutePath + File.separator)) {
            runCatching { source.delete() }
        }
        return true
    }

    fun addVariantFull(trackId: Long, spec: VariantSpec): Long {
        val values = ContentValues().apply {
            put("track_id", trackId)
            put("cents", spec.cents)
            put("formant", if (spec.formant) 1 else 0)
            put("engine", spec.engine)
            put("pitch_quality", spec.pitchQuality)
            put("section_start", spec.section?.first)
            put("section_end", spec.section?.second)
            put("output_path", spec.outputPath)
            put("output_format", spec.outputFormat)
            put("src_note", spec.srcNote)
            put("src_hz", spec.srcHz)
            put("target_note", spec.targetNote)
            put("target_hz", spec.targetHz)
        }
        return db().insert("variants", null, values)
    }

    fun findVariant(
        trackId: Long,
        cents: Int,
        formant: Boolean,
        section: Pair<Double, Double>?,
        format: String?,
    ): Variant? {
        val sql =
            """
            SELECT $VARIANT_COLUMNS FROM variants
            WHERE track_id = ? AND cents = ? AND formant = ?
              AND IFNULL(section_start, -1.0) = CAST(? AS REAL)
              AND IFNULL(section_end, -1.0) = CAST(? AS REAL)
              AND IFNULL(output_format, '') = ?
            ORDER BY id LIMIT 1
            """.trimIndent()
        // Android's bindAllArgsAsStrings rejects nulls, so send the same
        // sentinels the SQL uses for "absent" (section -1, format empty).
        val args = arrayOf(
            trackId.toString(),
            cents.toString(),
            if (formant) "1" else "0",
            (section?.first ?: -1.0).toString(),
            (section?.second ?: -1.0).toString(),
            format ?: "",
        )
        return db().rawQuery(sql, args).use { c ->
            if (c.moveToFirst()) readVariant(c) else null
        }
    }

    fun listVariants(trackId: Long): List<Variant> {
        val sql = "SELECT $VARIANT_COLUMNS FROM variants WHERE track_id = ? ORDER BY cents, id"
        return db().rawQuery(sql, arrayOf(trackId.toString())).use { c ->
            c.mapAll { readVariant(it) }
        }
    }

    fun getVariant(id: Long): Variant? {
        val sql = "SELECT $VARIANT_COLUMNS FROM variants WHERE id = ?"
        return db().rawQuery(sql, arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) readVariant(c) else null
        }
    }

    fun renameVariant(id: Long, name: String) {
        val values = ContentValues().apply {
            put("name", name.trim().takeIf { it.isNotEmpty() })
        }
        db().update("variants", values, "id = ?", arrayOf(id.toString()))
    }

    fun setFavorite(id: Long, favorite: Boolean) {
        val values = ContentValues().apply { put("favorite", if (favorite) 1 else 0) }
        db().update("variants", values, "id = ?", arrayOf(id.toString()))
    }

    fun deleteVariant(id: Long) {
        db().delete("variants", "id = ?", arrayOf(id.toString()))
    }

    fun addBookmark(trackId: Long, t: Double, name: String?): Long {
        val values = ContentValues().apply {
            put("track_id", trackId)
            put("t", t)
            put("name", name?.trim()?.takeIf { it.isNotEmpty() })
        }
        return db().insert("bookmarks", null, values)
    }

    fun listBookmarks(trackId: Long): List<Bookmark> {
        val sql =
            "SELECT id, track_id, t, name, created_at FROM bookmarks WHERE track_id = ? ORDER BY t, id"
        return db().rawQuery(sql, arrayOf(trackId.toString())).use { c ->
            c.mapAll { readBookmark(it) }
        }
    }

    fun renameBookmark(id: Long, name: String) {
        val values = ContentValues().apply {
            put("name", name.trim().takeIf { it.isNotEmpty() })
        }
        db().update("bookmarks", values, "id = ?", arrayOf(id.toString()))
    }

    fun deleteBookmark(id: Long) {
        db().delete("bookmarks", "id = ?", arrayOf(id.toString()))
    }

    fun close() {
        helper.close()
    }

    private fun readTrack(c: Cursor) = Track(
        id = c.getLong(0),
        sourcePath = c.getString(1),
        sourceKind = c.getString(2),
        sourceUrl = c.getStringOrNull(3),
        title = c.getString(4),
        artist = c.getStringOrNull(5),
        durationS = c.getDouble(6),
        sampleRate = if (c.isNull(7)) null else c.getInt(7),
        createdAt = c.getString(8),
        variantCount = c.getInt(9),
    )

    private fun readVariant(c: Cursor) = Variant(
        id = c.getLong(0),
        trackId = c.getLong(1),
        name = c.getStringOrNull(2),
        cents = c.getInt(3),
        formant = c.getInt(4) != 0,
        engine = c.getString(5),
        pitchQuality = c.getString(6),
        sectionStart = c.getDoubleOrNull(7),
        sectionEnd = c.getDoubleOrNull(8),
        outputPath = c.getString(9),
        outputFormat = c.getStringOrNull(10),
        srcNote = c.getStringOrNull(11),
        srcHz = c.getDoubleOrNull(12),
        targetNote = c.getStringOrNull(13),
        targetHz = c.getDoubleOrNull(14),
        favorite = c.getInt(15) != 0,
        createdAt = c.getString(16),
    )

    private fun readBookmark(c: Cursor) = Bookmark(
        id = c.getLong(0),
        trackId = c.getLong(1),
        t = c.getDouble(2),
        name = c.getStringOrNull(3),
        createdAt = c.getString(4),
    )

    private companion object {
        const val VARIANT_COLUMNS =
            "id, track_id, name, cents, formant, engine, pitch_quality, section_start, " +
                "section_end, output_path, output_format, src_note, src_hz, " +
                "target_note, target_hz, favorite, created_at"
    }
}

private inline fun <T> Cursor.mapAll(read: (Cursor) -> T): List<T> {
    val out = ArrayList<T>(count)
    while (moveToNext()) out.add(read(this))
    return out
}

private fun Cursor.getStringOrNull(index: Int): String? =
    if (isNull(index)) null else getString(index)

private fun Cursor.getDoubleOrNull(index: Int): Double? =
    if (isNull(index)) null else getDouble(index)
