package pitcher.android.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.cancellation.CancellationException

/**
 * Decodes a media file's audio track to float PCM, optionally limited to a
 * time range and a maximum channel count. Callers can ask for stereo and fall
 * back to mono if memory is tight.
 */
object PcmDecoder {

    fun decodeChannels(
        path: String,
        startMs: Long = 0L,
        endMs: Long = -1L,
        maxChannels: Int = 2,
        cancelled: () -> Boolean = { false },
    ): WindowedAudioChannels? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(path)
            val trackIndex = firstAudioTrack(extractor) ?: return null
            extractor.selectTrack(trackIndex)
            val startUs = startMs.coerceAtLeast(0) * 1000
            val endUs = if (endMs > startMs) endMs * 1000 else Long.MAX_VALUE
            if (startUs > 0) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                44100
            }
            val sourceChannels = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                2
            }
            val outChannels = sourceChannels.coerceIn(1, maxChannels.coerceAtLeast(1))

            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }
            val rangeEndUs = if (endUs == Long.MAX_VALUE) durationUs else endUs
            val estimate = if (durationUs > 0 && rangeEndUs > startUs) {
                ((rangeEndUs - startUs) / 1_000_000.0 * sampleRate).toInt() + sampleRate
            } else {
                0
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val builders = Array(outChannels) {
                FloatBuilder(estimate.coerceAtLeast(1 shl 12))
            }
            var channels = sourceChannels
            var shorts = ShortArray(0)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (cancelled()) throw CancellationException("cancelled")
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
                            buffer.order(ByteOrder.LITTLE_ENDIAN)
                            val ch = channels.coerceAtLeast(1)
                            val shortCount = buffer.remaining() / 2
                            if (shorts.size < shortCount) shorts = ShortArray(shortCount)
                            for (i in 0 until shortCount) shorts[i] = buffer.getShort()
                            val bufStartUs = info.presentationTimeUs
                            var frame = 0
                            var i = 0
                            while (i < shortCount) {
                                val frameUs = bufStartUs + frame * 1_000_000L / sampleRate
                                if (frameUs >= startUs && frameUs < endUs) {
                                    if (outChannels == 1) {
                                        var sum = 0f
                                        for (c in 0 until ch) {
                                            if (i + c < shortCount) sum += shorts[i + c] / 32768f
                                        }
                                        builders[0].add(sum / ch)
                                    } else {
                                        for (c in 0 until outChannels) {
                                            builders[c].add(
                                                if (i + c < shortCount) shorts[i + c] / 32768f else 0f,
                                            )
                                        }
                                    }
                                }
                                frame++
                                i += ch
                            }
                            if (bufStartUs + frame * 1_000_000L / sampleRate >= endUs) {
                                outputDone = true
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            val arrays = Array(outChannels) { builders[it].toArray() }
            if (arrays[0].isEmpty()) null else WindowedAudioChannels(arrays, sampleRate)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    fun decodeMono(path: String, startMs: Long = 0L, endMs: Long = -1L): WindowedAudio? {
        val decoded = decodeChannels(path, startMs, endMs, maxChannels = 1) ?: return null
        return WindowedAudio(decoded.channels[0], decoded.sampleRate)
    }

    private fun firstAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    private class FloatBuilder(capacity: Int = 1 shl 16) {
        private var data = FloatArray(capacity)
        private var size = 0

        fun add(value: Float) {
            if (size == data.size) {
                val grown = data.size + (data.size shr 1) + 4096
                data = data.copyOf(grown)
            }
            data[size++] = value
        }

        fun toArray(): FloatArray = data.copyOf(size)
    }
}

data class WindowedAudioChannels(
    val channels: Array<FloatArray>,
    val sampleRate: Int,
)
