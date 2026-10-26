package pitcher.android.data

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * A song package is a zip holding `song.json` and the original audio under
 * `audio/`. It carries the song's pitches, loops and bookmarks with their
 * names, so another phone can rebuild the same song. Rendered files and the
 * save folder stay behind: the receiver renders again and saves where they like.
 */
object SongPackage {
    const val EXTENSION = "pitcher"
    const val MIME = "application/zip"
    private const val FORMAT = "pitcher-song"
    private const val VERSION = 1
    private const val MANIFEST = "song.json"
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    fun fileName(track: Track): String {
        val base = track.title.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifEmpty { "song" }
        return "$base.$EXTENSION"
    }

    fun looksLikePackage(head: ByteArray): Boolean =
        head.size >= ZIP_MAGIC.size && ZIP_MAGIC.indices.all { head[it] == ZIP_MAGIC[it] }

    fun export(repo: ShelfRepository, trackId: Long, out: OutputStream) {
        val track = repo.getTrack(trackId) ?: throw IllegalArgumentException("no song $trackId")
        val source = File(track.sourcePath)
        val audioEntry = "audio/" + source.name
        val manifest = JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("title", track.title)
            putOpt("artist", track.artist)
            put("durationS", track.durationS)
            putOpt("sampleRate", track.sampleRate)
            putOpt("tunerSource", track.tunerSource)
            putOpt("tunerTarget", track.tunerTarget)
            put("audio", audioEntry)
            put("pitches", JSONArray(repo.listVariants(trackId).map { pitchJson(it) }))
            put("loops", JSONArray(repo.listLoops(trackId).map { loopJson(it) }))
            put("bookmarks", JSONArray(repo.listBookmarks(trackId).map { bookmarkJson(it) }))
        }
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(audioEntry))
            source.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    /** Adds the packaged song as a new library entry and returns its id. */
    fun import(repo: ShelfRepository, input: InputStream, importsDir: File): Long {
        importsDir.mkdirs()
        var manifest: JSONObject? = null
        val audioFiles = HashMap<String, File>()
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    if (entry.name == MANIFEST) {
                        manifest = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                    } else if (entry.name !in audioFiles) {
                        val target = uniqueFile(importsDir, safeName(entry.name))
                        target.outputStream().use { zip.copyTo(it) }
                        audioFiles[entry.name] = target
                    }
                }
            }
            val m = manifest?.takeIf { it.optString("format") == FORMAT }
                ?: throw IllegalArgumentException("not a pitcher song package")
            if (m.optInt("version", 0) > VERSION) {
                throw IllegalArgumentException("this package needs a newer pitcher")
            }
            val audio = audioFiles.remove(m.getString("audio"))
                ?: throw IllegalArgumentException("the package has no audio")
            return addSong(repo, m, audio)
        } finally {
            audioFiles.values.forEach { it.delete() }
        }
    }

    private fun addSong(repo: ShelfRepository, m: JSONObject, audio: File): Long {
        val id = repo.addTrack(
            sourcePath = audio.absolutePath,
            sourceKind = "file",
            sourceUrl = null,
            title = m.getString("title"),
            artist = m.optStringOrNull("artist"),
            durationS = m.getDouble("durationS"),
            sampleRate = if (m.has("sampleRate")) m.getInt("sampleRate") else null,
        )
        repo.setTunerNotes(id, m.optStringOrNull("tunerSource"), m.optStringOrNull("tunerTarget"))
        m.optJSONArray("pitches")?.objects()?.forEach { p ->
            val start = p.optDoubleOrNull("sectionStart")
            val end = p.optDoubleOrNull("sectionEnd")
            val variant = repo.addVariantFull(
                id,
                VariantSpec(
                    cents = p.getInt("cents"),
                    formant = p.optBoolean("formant", false),
                    engine = p.optString("engine", "sonic"),
                    pitchQuality = p.optString("pitchQuality", "quality"),
                    section = if (start != null && end != null) start to end else null,
                    outputPath = "",
                    outputFormat = null,
                    srcNote = p.optStringOrNull("srcNote"),
                    srcHz = p.optDoubleOrNull("srcHz"),
                    targetNote = p.optStringOrNull("targetNote"),
                    targetHz = p.optDoubleOrNull("targetHz"),
                ),
            )
            p.optStringOrNull("name")?.let { repo.renameVariant(variant, it) }
            if (p.optBoolean("favorite", false)) repo.setFavorite(variant, true)
        }
        m.optJSONArray("loops")?.objects()?.forEach { l ->
            val loop = repo.addLoop(id, l.getLong("startMs"), l.getLong("endMs"), l.optStringOrNull("name"))
            if (!l.optBoolean("enabled", true)) repo.setLoopEnabled(loop, false)
        }
        m.optJSONArray("bookmarks")?.objects()?.forEach { b ->
            repo.addBookmark(id, b.getDouble("t"), b.optStringOrNull("name"))
        }
        return id
    }

    private fun pitchJson(v: Variant) = JSONObject().apply {
        putOpt("name", v.name)
        put("cents", v.cents)
        put("formant", v.formant)
        put("engine", v.engine)
        put("pitchQuality", v.pitchQuality)
        putOpt("sectionStart", v.sectionStart)
        putOpt("sectionEnd", v.sectionEnd)
        putOpt("srcNote", v.srcNote)
        putOpt("srcHz", v.srcHz)
        putOpt("targetNote", v.targetNote)
        putOpt("targetHz", v.targetHz)
        put("favorite", v.favorite)
    }

    private fun loopJson(l: LoopSection) = JSONObject().apply {
        putOpt("name", l.name)
        put("startMs", l.startMs)
        put("endMs", l.endMs)
        put("enabled", l.enabled)
    }

    private fun bookmarkJson(b: Bookmark) = JSONObject().apply {
        putOpt("name", b.name)
        put("t", b.t)
    }

    private fun safeName(entryName: String): String =
        entryName.substringAfterLast('/').substringAfterLast('\\')
            .trimStart('.').ifEmpty { "audio" }

    private fun uniqueFile(dir: File, name: String): File {
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var candidate = File(dir, name)
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($i)$ext")
            i++
        }
        return candidate
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) getDouble(key) else null
}
