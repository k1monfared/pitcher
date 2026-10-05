package pitcher.android.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes a whole media file's audio track to mono float samples for offline
 * rendering. Mono keeps memory bounded on phones; stereo rendering is a
 * follow-up.
 */
object PcmDecoder {

    fun decodeMono(path: String, startMs: Long = 0L, endMs: Long = -1L): WindowedAudio? {
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
            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }
            val estimatedFrames = if (durationUs > 0) {
                (durationUs / 1_000_000.0 * sampleRate).toInt() + sampleRate
            } else {
                sampleRate * 60
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val out = ArrayList<Float>(estimatedFrames.coerceAtMost(60 * sampleRate))
            var channels = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                2
            }
            var shorts = ShortArray(0)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

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
                                    var sum = 0f
                                    for (c in 0 until ch) {
                                        if (i + c < shortCount) sum += shorts[i + c] / 32768f
                                    }
                                    out.add(sum / ch)
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

            if (out.isEmpty()) null else WindowedAudio(out.toFloatArray(), sampleRate)
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
