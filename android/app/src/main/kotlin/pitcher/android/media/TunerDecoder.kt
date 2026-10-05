package pitcher.android.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class WindowedAudio(
    val samples: FloatArray,
    val sampleRate: Int,
)

/**
 * Decodes a short mono window of a media file's audio around a timestamp, for
 * single-note tuning. Decoding starts at the nearest sync frame before the
 * window and buffers only the samples that fall inside the window.
 */
object TunerDecoder {

    fun decodeWindow(
        path: String,
        centerMs: Long,
        windowMs: Long = 250,
        maxWindowMs: Long = 600,
    ): WindowedAudio? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(path)
            val trackIndex = firstAudioTrack(extractor) ?: return null
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                44100
            }

            val startUs = ((centerMs - maxWindowMs / 2).coerceAtLeast(0)) * 1000
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val winStartUs = (centerMs - windowMs / 2).coerceAtLeast(0) * 1000
            val winEndUs = (centerMs + windowMs / 2) * 1000
            val collected = ArrayList<Float>()
            var channels = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                2
            }

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var shorts = ShortArray(0)

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val fmt = codec.outputFormat
                        if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }
                    outIndex >= 0 -> {
                        val buffer = codec.getOutputBuffer(outIndex)!!
                        if (info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val bufStartUs = info.presentationTimeUs
                            val ch = channels.coerceAtLeast(1)
                            buffer.order(ByteOrder.LITTLE_ENDIAN)
                            val shortCount = buffer.remaining() / 2
                            if (shorts.size < shortCount) shorts = ShortArray(shortCount)
                            for (i in 0 until shortCount) shorts[i] = buffer.getShort()
                            var frame = 0
                            var i = 0
                            while (i < shortCount) {
                                val frameUs = bufStartUs + frame * 1_000_000L / sampleRate
                                if (frameUs in winStartUs until winEndUs) {
                                    var sum = 0f
                                    for (c in 0 until ch) {
                                        if (i + c < shortCount) sum += shorts[i + c] / 32768f
                                    }
                                    collected.add(sum / ch)
                                }
                                frame++
                                i += ch
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            if (collected.isEmpty()) null
            else WindowedAudio(collected.toFloatArray(), sampleRate)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun firstAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return null
    }
}
