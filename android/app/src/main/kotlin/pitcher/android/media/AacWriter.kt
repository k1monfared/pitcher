package pitcher.android.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * Encodes float PCM to AAC in an MP4 (.m4a) container using MediaCodec +
 * MediaMuxer. Device-only (no JVM test); errors surface to the caller.
 */
object AacWriter {

    fun write(file: File, channels: Array<FloatArray>, sampleRate: Int, bitRate: Int = 192_000) {
        val numChannels = channels.size.coerceAtMost(2)
        val frames = channels[0].size
        val mime = MediaFormat.MIMETYPE_AUDIO_AAC
        val format = MediaFormat.createAudioFormat(mime, sampleRate, numChannels).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC,
            )
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        encodeToMuxer(
            file = file,
            mime = mime,
            format = format,
            muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
            channels = channels,
            numChannels = numChannels,
            frames = frames,
            sampleRate = sampleRate,
        )
    }

    internal fun encodeToMuxer(
        file: File,
        mime: String,
        format: MediaFormat,
        muxerFormat: Int,
        channels: Array<FloatArray>,
        numChannels: Int,
        frames: Int,
        sampleRate: Int,
    ) {
        file.parentFile?.mkdirs()
        val codec = MediaCodec.createEncoderByType(mime)
        val muxer = MediaMuxer(file.absolutePath, muxerFormat)
        var trackIndex = -1
        var muxerStarted = false
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val info = MediaCodec.BufferInfo()
            val framesPerChunk = 4096
            var framePos = 0
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        buffer.clear()
                        val n = minOf(framesPerChunk, frames - framePos)
                        if (n <= 0) {
                            codec.queueInputBuffer(
                                inIndex,
                                0,
                                0,
                                framePos * 1_000_000L / sampleRate,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            for (i in 0 until n) {
                                for (c in 0 until numChannels) {
                                    val v = (channels[c][framePos + i].coerceIn(-1f, 1f) * 32767f)
                                        .toInt().toShort()
                                    buffer.putShort(v)
                                }
                            }
                            codec.queueInputBuffer(
                                inIndex,
                                0,
                                n * numChannels * 2,
                                framePos * 1_000_000L / sampleRate,
                                0,
                            )
                            framePos += n
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        val buffer = codec.getOutputBuffer(outIndex)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            info.size = 0
                        }
                        if (info.size > 0 && muxerStarted) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, buffer, info)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { if (muxerStarted) muxer.stop() }
            runCatching { muxer.release() }
        }
    }
}
