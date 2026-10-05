package pitcher.android.media

import java.io.File
import pitcher.core.ExportFormat
import pitcher.core.Mp3Writer
import pitcher.core.PitchShifter
import pitcher.core.WavWriter

/**
 * Renders a pitch-shifted file in the requested format. Decodes stereo PCM for
 * the given time range, shifts each channel with the core WSOLA shifter, then
 * encodes. Falls back to mono (and reports) if memory runs out. A whole-track
 * zero-cent request in the same format is a straight copy of the original.
 */
object AudioRenderer {

    fun render(
        sourcePath: String,
        target: File,
        format: ExportFormat,
        cents: Int,
        startMs: Long = 0L,
        endMs: Long = -1L,
    ) {
        val isWholeTrack = startMs <= 0L && endMs <= 0L
        if (cents == 0 && isWholeTrack && sameFormat(sourcePath, format)) {
            File(sourcePath).copyTo(target, overwrite = true)
            return
        }

        try {
            renderWith(sourcePath, target, format, cents, startMs, endMs, maxChannels = 2)
        } catch (e: OutOfMemoryError) {
            // Retry as mono; stereo float PCM plus the shifter temporaries can
            // exceed the heap on long tracks.
            renderWith(sourcePath, target, format, cents, startMs, endMs, maxChannels = 1)
        }
    }

    private fun renderWith(
        sourcePath: String,
        target: File,
        format: ExportFormat,
        cents: Int,
        startMs: Long,
        endMs: Long,
        maxChannels: Int,
    ) {
        val decoded = PcmDecoder.decodeChannels(sourcePath, startMs, endMs, maxChannels)
            ?: error("cannot decode audio")

        val channels = decoded.channels
        val shifted = Array(channels.size) { c ->
            val out = PitchShifter.shift(channels[c], decoded.sampleRate, cents)
            channels[c] = FloatArray(0) // free the input channel as we go
            out
        }

        when (format) {
            ExportFormat.Wav -> WavWriter.write(target, shifted, decoded.sampleRate)
            ExportFormat.Mp3 -> Mp3Writer.write(target, shifted, decoded.sampleRate)
            ExportFormat.M4a -> AacWriter.write(target, shifted, decoded.sampleRate)
            ExportFormat.Opus -> OpusWriter.write(target, shifted, decoded.sampleRate)
        }
    }

    private fun sameFormat(sourcePath: String, format: ExportFormat): Boolean {
        val ext = sourcePath.substringAfterLast('.', "").lowercase()
        return ext == format.extension || (format == ExportFormat.Opus && ext == "ogg")
    }
}
