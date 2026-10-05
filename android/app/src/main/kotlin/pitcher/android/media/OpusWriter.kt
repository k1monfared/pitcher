package pitcher.android.media

import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * Encodes float PCM to Opus in an OGG container using MediaCodec + MediaMuxer
 * (both Opus encoding and the OGG muxer are available from API 29). Fails with
 * a clear message on devices lacking an Opus encoder.
 */
object OpusWriter {

    fun write(file: File, channels: Array<FloatArray>, sampleRate: Int, bitRate: Int = 160_000) {
        val numChannels = channels.size.coerceAtMost(2)
        val frames = channels[0].size
        val mime = MediaFormat.MIMETYPE_AUDIO_OPUS
        val format = MediaFormat.createAudioFormat(mime, sampleRate, numChannels).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        }
        AacWriter.encodeToMuxer(
            file = file,
            mime = mime,
            format = format,
            muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG,
            channels = channels,
            numChannels = numChannels,
            frames = frames,
            sampleRate = sampleRate,
        )
    }
}
