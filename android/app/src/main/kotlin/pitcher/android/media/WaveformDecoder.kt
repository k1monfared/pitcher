package pitcher.android.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Decodes a media file's audio track to normalized waveform peaks without
 * holding the whole PCM stream in memory: peaks are accumulated per bucket
 * while decoding.
 */
object WaveformDecoder {

    fun decodePeaks(path: String, buckets: Int = 2000): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(path)
            val trackIndex = firstAudioTrack(extractor) ?: return FloatArray(0)
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return FloatArray(0)
            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }
            val sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                44100
            }

            codec = Codecs.decoder(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val estimatedFrames = if (durationUs > 0) {
                (durationUs / 1_000_000.0 * sampleRate).toLong()
            } else {
                0L
            }

            val accumulator = PeakAccumulator(buckets, estimatedFrames)

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var channels = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                2
            }

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
                            accumulator.feed(buffer, channels)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }
            accumulator.peaks()
        } catch (_: Exception) {
            FloatArray(0)
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

    private class PeakAccumulator(private val buckets: Int, estimatedFrames: Long) {
        private val framesPerBucket: Long =
            if (estimatedFrames > 0 && buckets > 0) {
                maxOf(1L, estimatedFrames / buckets)
            } else {
                2048L
            }
        private val peaks = ArrayList<Float>(buckets.coerceAtLeast(64))
        private var frameInBucket = 0L
        private var bucketMax = 0f
        private var globalMax = 1e-6f
        private var shorts: ShortArray = ShortArray(0)

        fun feed(buffer: ByteBuffer, channels: Int) {
            val ch = channels.coerceAtLeast(1)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            val shortCount = buffer.remaining() / 2
            if (shorts.size < shortCount) shorts = ShortArray(shortCount)
            for (i in 0 until shortCount) shorts[i] = buffer.getShort()
            var i = 0
            while (i < shortCount) {
                var sum = 0f
                for (c in 0 until ch) {
                    if (i + c < shortCount) sum += abs(shorts[i + c] / 32768f)
                }
                val v = sum / ch
                if (v > bucketMax) bucketMax = v
                frameInBucket++
                if (frameInBucket >= framesPerBucket) {
                    pushBucket()
                }
                i += ch
            }
        }

        private fun pushBucket() {
            peaks.add(bucketMax)
            if (bucketMax > globalMax) globalMax = bucketMax
            bucketMax = 0f
            frameInBucket = 0L
        }

        fun peaks(): FloatArray {
            if (frameInBucket > 0) pushBucket()
            if (peaks.isEmpty()) return FloatArray(0)
            val out = FloatArray(peaks.size)
            for (i in peaks.indices) out[i] = peaks[i] / globalMax
            return out
        }
    }
}
