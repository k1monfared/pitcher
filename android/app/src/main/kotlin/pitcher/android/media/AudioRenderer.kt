package pitcher.android.media

import java.io.File
import pitcher.core.ExportFormat
import pitcher.core.Mp3Writer
import pitcher.core.PitchShifter
import pitcher.core.WavWriter

/**
 * Renders a pitch-shifted file in the requested format. Decodes stereo PCM for
 * the given time range (falling back to mono if memory is tight), shifts each
 * channel with the core WSOLA shifter, then encodes. A whole-track zero-cent
 * request is a straight copy of the original.
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
        if (cents == 0 && isWholeTrack) {
            File(sourcePath).copyTo(target, overwrite = true)
            return
        }

        val decoded = decodeWithFallback(sourcePath, startMs, endMs)
            ?: error("cannot decode audio")
        val shifted = Array(decoded.channels.size) { c ->
            PitchShifter.shift(decoded.channels[c], decoded.sampleRate, cents)
        }

        when (format) {
            ExportFormat.Wav -> WavWriter.write(target, shifted, decoded.sampleRate)
            ExportFormat.Mp3 -> Mp3Writer.write(target, shifted, decoded.sampleRate)
            ExportFormat.M4a -> AacWriter.write(target, shifted, decoded.sampleRate)
            ExportFormat.Opus -> OpusWriter.write(target, shifted, decoded.sampleRate)
        }
    }

    private fun decodeWithFallback(
        sourcePath: String,
        startMs: Long,
        endMs: Long,
    ): WindowedAudioChannels? {
        return try {
            PcmDecoder.decodeChannels(sourcePath, startMs, endMs, maxChannels = 2)
        } catch (_: OutOfMemoryError) {
            PcmDecoder.decodeChannels(sourcePath, startMs, endMs, maxChannels = 1)
        }
    }
}
