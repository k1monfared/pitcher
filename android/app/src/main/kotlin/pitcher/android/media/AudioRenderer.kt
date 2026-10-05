package pitcher.android.media

import java.io.File
import pitcher.core.ExportFormat
import pitcher.core.Mp3Writer
import pitcher.core.PitchShifter
import pitcher.core.WavWriter

/**
 * Renders a pitch-shifted file in the requested format. Decodes mono PCM for
 * the given time range, shifts it with the core WSOLA shifter, then encodes.
 * A whole-track zero-cent request is a straight copy of the original.
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

        val pcm = PcmDecoder.decodeMono(sourcePath, startMs, endMs)
            ?: error("cannot decode audio")
        val shifted = PitchShifter.shift(pcm.samples, pcm.sampleRate, cents)
        val channels = arrayOf(shifted)

        when (format) {
            ExportFormat.Wav -> WavWriter.write(target, channels, pcm.sampleRate)
            ExportFormat.Mp3 -> Mp3Writer.write(target, channels, pcm.sampleRate)
            ExportFormat.M4a -> AacWriter.write(target, channels, pcm.sampleRate)
            ExportFormat.Opus -> OpusWriter.write(target, channels, pcm.sampleRate)
        }
    }
}
