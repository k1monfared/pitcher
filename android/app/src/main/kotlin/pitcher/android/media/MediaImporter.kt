package pitcher.android.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.nio.ByteBuffer

data class ImportedAudio(
    val file: File,
    val displayName: String,
    val durationS: Double,
    val sampleRate: Int?,
)

/**
 * Import reduces any source to audio only. If the picked file carries a video
 * track (and its audio is AAC), the audio track is muxed into an audio-only
 * .m4a without re-encoding, so no video bytes are stored. Files that are
 * already audio-only are copied as-is.
 */
object MediaImporter {

    fun importsDir(context: Context): File =
        File(context.filesDir, "imports").apply { mkdirs() }

    fun copyIn(context: Context, uri: Uri): ImportedAudio {
        val name = queryDisplayName(context, uri) ?: "audio"
        val base = name.substringBeforeLast('.', name)
        val dir = importsDir(context)
        val resolver = context.contentResolver

        val pfd = resolver.openFileDescriptor(uri, "r") ?: error("cannot open $uri")
        var durationMs = 0L
        var sampleRate: Int? = null
        var extracted: File? = null

        pfd.use { descriptor ->
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(descriptor.fileDescriptor)
                val audioIndex = firstAudioTrack(extractor)
                val hasVideo = hasVideoTrack(extractor)
                val audioMime = audioIndex?.let {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)
                }
                if (audioIndex != null) {
                    val fmt = extractor.getTrackFormat(audioIndex)
                    if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    val durationUs =
                        if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
                    durationMs = if (durationUs > 0) durationUs / 1000 else 0L
                }
                if (durationMs <= 0) durationMs = probeDurationMs(uri, context)

                if (hasVideo && audioIndex != null && audioMime == "audio/mp4a-latm") {
                    val target = uniqueFile(dir, "$base.m4a")
                    extractAudioTrack(extractor, audioIndex, target)
                    extracted = target
                }
            } finally {
                runCatching { extractor.release() }
            }
        }

        val file = extracted ?: run {
            val target = uniqueFile(dir, name)
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("cannot read $uri")
            target
        }

        return ImportedAudio(
            file = file,
            displayName = base,
            durationS = durationMs / 1000.0,
            sampleRate = sampleRate,
        )
    }

    private fun extractAudioTrack(extractor: MediaExtractor, audioIndex: Int, target: File) {
        val muxer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            extractor.selectTrack(audioIndex)
            val dstTrack = muxer.addTrack(extractor.getTrackFormat(audioIndex))
            muxer.start()
            val buffer = ByteBuffer.allocate(1 shl 20)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = extractor.sampleTime
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(dstTrack, buffer, info)
                extractor.advance()
            }
        } finally {
            runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun probeDurationMs(uri: Uri, context: Context): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun firstAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    private fun hasVideoTrack(extractor: MediaExtractor): Boolean {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) return true
        }
        return false
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) return c.getString(0)
            }
        return uri.lastPathSegment
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
