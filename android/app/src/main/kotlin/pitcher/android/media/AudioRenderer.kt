package pitcher.android.media

import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import pitcher.core.ExportFormat
import pitcher.core.Mp3Writer
import pitcher.core.PcmConcat
import pitcher.core.PitchShifter
import pitcher.core.WavWriter

/**
 * Renders a pitch-shifted file in the requested format. Decodes PCM for a time
 * range (or a list of loop ranges), shifts each channel with the core WSOLA
 * shifter, then encodes. Multiple ranges are concatenated with hard cuts at the
 * PCM level. Falls back to mono (and reports) if memory runs out. A whole-track
 * zero-cent request in the same format is a straight copy of the original.
 *
 * [cancelled] is polled during decoding and shifting so a long render can be
 * aborted; when it fires a [CancellationException] is thrown.
 */
object AudioRenderer {

    fun render(
        sourcePath: String,
        target: File,
        format: ExportFormat,
        cents: Int,
        startMs: Long = 0L,
        endMs: Long = -1L,
        segments: List<Pair<Long, Long>>? = null,
        speed: Double = 1.0,
        cancelled: () -> Boolean = { false },
    ) {
        if (segments == null || segments.size <= 1) {
            val s = segments?.firstOrNull()?.first ?: startMs
            val e = segments?.firstOrNull()?.second ?: endMs
            renderRange(sourcePath, target, format, cents, speed, s, e, cancelled)
            return
        }
        try {
            renderConcat(sourcePath, target, format, cents, speed, segments, maxChannels = 2, cancelled = cancelled)
        } catch (e: OutOfMemoryError) {
            renderConcat(sourcePath, target, format, cents, speed, segments, maxChannels = 1, cancelled = cancelled)
        }
    }

    private fun renderRange(
        sourcePath: String,
        target: File,
        format: ExportFormat,
        cents: Int,
        speed: Double,
        startMs: Long,
        endMs: Long,
        cancelled: () -> Boolean,
    ) {
        val isWholeTrack = startMs <= 0L && endMs <= 0L
        if (cents == 0 && speed == 1.0 && isWholeTrack && sameFormat(sourcePath, format)) {
            File(sourcePath).copyTo(target, overwrite = true)
            return
        }
        try {
            renderPcm(sourcePath, target, format, cents, speed, startMs, endMs, maxChannels = 2, cancelled = cancelled)
        } catch (e: OutOfMemoryError) {
            renderPcm(sourcePath, target, format, cents, speed, startMs, endMs, maxChannels = 1, cancelled = cancelled)
        }
    }

    private fun renderPcm(
        sourcePath: String,
        target: File,
        format: ExportFormat,
        cents: Int,
        speed: Double,
        startMs: Long,
        endMs: Long,
        maxChannels: Int,
        cancelled: () -> Boolean,
    ) {
        if (cancelled()) throw CancellationException("cancelled")
        val decoded = PcmDecoder.decodeChannels(sourcePath, startMs, endMs, maxChannels, cancelled)
            ?: error("cannot decode audio")
        val shifted = shiftChannels(decoded, cents, speed, cancelled)
        if (cancelled()) throw CancellationException("cancelled")
        writeFormat(target, format, shifted, decoded.sampleRate)
    }

    private fun renderConcat(
        sourcePath: String,
        target: File,
        format: ExportFormat,
        cents: Int,
        speed: Double,
        segments: List<Pair<Long, Long>>,
        maxChannels: Int,
        cancelled: () -> Boolean,
    ) {
        val parts = ArrayList<Array<FloatArray>>(segments.size)
        var sampleRate = 44100
        for ((startMs, endMs) in segments) {
            if (cancelled()) throw CancellationException("cancelled")
            val decoded = PcmDecoder.decodeChannels(sourcePath, startMs, endMs, maxChannels, cancelled)
                ?: error("cannot decode audio")
            sampleRate = decoded.sampleRate
            parts.add(shiftChannels(decoded, cents, speed, cancelled))
        }
        if (cancelled()) throw CancellationException("cancelled")
        writeFormat(target, format, PcmConcat.concat(parts), sampleRate)
    }

    private fun shiftChannels(
        decoded: WindowedAudioChannels,
        cents: Int,
        speed: Double,
        cancelled: () -> Boolean,
    ): Array<FloatArray> {
        val channels = decoded.channels
        return Array(channels.size) { c ->
            val out = PitchShifter.shift(channels[c], decoded.sampleRate, cents, speed, cancelled)
            channels[c] = FloatArray(0)
            out
        }
    }

    private fun writeFormat(target: File, format: ExportFormat, channels: Array<FloatArray>, sampleRate: Int) {
        when (format) {
            ExportFormat.Wav -> WavWriter.write(target, channels, sampleRate)
            ExportFormat.Mp3 -> Mp3Writer.write(target, channels, sampleRate)
            ExportFormat.M4a -> AacWriter.write(target, channels, sampleRate)
            ExportFormat.Opus -> OpusWriter.write(target, channels, sampleRate)
        }
    }

    private fun sameFormat(sourcePath: String, format: ExportFormat): Boolean {
        val ext = sourcePath.substringAfterLast('.', "").lowercase()
        return ext == format.extension || (format == ExportFormat.Opus && ext == "ogg")
    }
}
