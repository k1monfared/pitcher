package pitcher.android.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

data class ImportedAudio(
    val file: File,
    val displayName: String,
    val durationS: Double,
    val sampleRate: Int?,
)

object MediaImporter {

    private val AUDIO_EXTENSIONS = setOf(
        "wav", "mp3", "flac", "ogg", "oga", "opus", "m4a", "aac", "mp4", "mkv", "webm", "3gp",
    )

    fun importsDir(context: Context): File =
        File(context.filesDir, "imports").apply { mkdirs() }

    fun copyIn(context: Context, uri: Uri): ImportedAudio {
        val name = queryDisplayName(context, uri) ?: "audio"
        val safe = sanitize(name)
        val dir = importsDir(context)
        val target = uniqueFile(dir, safe)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("cannot open $uri")

        val durationMs = probeDurationMs(target)
        val sampleRate = probeSampleRate(target)
        return ImportedAudio(
            file = target,
            displayName = name.substringBeforeLast('.'),
            durationS = durationMs / 1000.0,
            sampleRate = sampleRate,
        )
    }

    fun isProbablyAudioOrVideo(displayName: String): Boolean {
        val ext = displayName.substringAfterLast('.', "").lowercase()
        return ext.isEmpty() || ext in AUDIO_EXTENSIONS
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) return c.getString(0)
            }
        return uri.lastPathSegment
    }

    private fun probeDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun probeSampleRate(file: File): Int? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/") && format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    return format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun sanitize(name: String): String {
        val cleaned = name.map { if (it == '/' || it == '\\' || it == '\u0000') '_' else it }
            .joinToString("").trim()
        return cleaned.ifEmpty { "audio" }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (candidate.exists()) {
            val suffix = if (ext.isEmpty()) "" else ".$ext"
            candidate = File(dir, "$base ($i)$suffix")
            i++
        }
        return candidate
    }
}
