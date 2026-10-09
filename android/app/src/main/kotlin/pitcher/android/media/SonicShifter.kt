package pitcher.android.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pitch and speed for saved files, through Media3's Sonic: the same engine
 * ExoPlayer uses for live playback, so a saved file sounds like the preview.
 */
object SonicShifter {

    private const val CHUNK_FRAMES = 8192

    /**
     * Streams interleaved 16-bit audio through Sonic. Each output chunk is
     * handed to the callback as (samples, count); the array is reused.
     */
    @OptIn(UnstableApi::class)
    class Stream(sampleRate: Int, private val channels: Int, cents: Int, speed: Double) {
        private val processor = SonicAudioProcessor()
        private val active: Boolean
        private var input = ByteBuffer.allocateDirect(0)
        private var scratch = ShortArray(0)

        init {
            processor.setPitch(Math.pow(2.0, cents / 1200.0).toFloat())
            processor.setSpeed(speed.toFloat())
            processor.configure(AudioProcessor.AudioFormat(sampleRate, channels, C.ENCODING_PCM_16BIT))
            processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
            active = processor.isActive
        }

        fun feed(samples: ShortArray, count: Int, out: (ShortArray, Int) -> Unit) {
            if (count <= 0) return
            if (!active) {
                out(samples, count)
                return
            }
            if (input.capacity() < count * 2) {
                input = ByteBuffer.allocateDirect(count * 2).order(ByteOrder.nativeOrder())
            }
            input.clear()
            input.asShortBuffer().put(samples, 0, count)
            input.limit(count * 2)
            while (input.hasRemaining()) {
                processor.queueInput(input)
                drain(out)
            }
        }

        fun finish(out: (ShortArray, Int) -> Unit) {
            if (!active) return
            processor.queueEndOfStream()
            var guard = 0
            while (!processor.isEnded && guard++ < 100_000) drain(out)
            drain(out)
        }

        private fun drain(out: (ShortArray, Int) -> Unit) {
            val buffer = processor.output
            if (!buffer.hasRemaining()) return
            val n = buffer.remaining() / 2
            if (scratch.size < n) scratch = ShortArray(n)
            buffer.order(ByteOrder.nativeOrder()).asShortBuffer().get(scratch, 0, n)
            buffer.position(buffer.limit())
            out(scratch, n)
        }
    }

    /** Shifts whole channels at once, keeping them in step. */
    fun process(channels: Array<FloatArray>, sampleRate: Int, cents: Int, speed: Double): Array<FloatArray> {
        if (cents == 0 && speed == 1.0) return Array(channels.size) { channels[it].copyOf() }
        val ch = channels.size.coerceAtLeast(1)
        val frames = channels.firstOrNull()?.size ?: 0
        val stream = Stream(sampleRate, ch, cents, speed)
        val collected = ShortCollector(((frames / speed) * ch).toInt() + 4096)
        val chunk = ShortArray(CHUNK_FRAMES * ch)
        var pos = 0
        while (pos < frames) {
            val n = minOf(CHUNK_FRAMES, frames - pos)
            for (f in 0 until n) {
                for (c in 0 until ch) {
                    val v = channels[c][pos + f].coerceIn(-1f, 1f)
                    chunk[f * ch + c] = (v * 32767f).toInt().toShort()
                }
            }
            stream.feed(chunk, n * ch) { s, count -> collected.add(s, count) }
            pos += n
        }
        stream.finish { s, count -> collected.add(s, count) }
        val outFrames = collected.size / ch
        return Array(ch) { c -> FloatArray(outFrames) { f -> collected[f * ch + c] / 32768f } }
    }

    private class ShortCollector(capacity: Int) {
        private var data = ShortArray(capacity.coerceAtLeast(1024))
        var size = 0
            private set

        fun add(samples: ShortArray, count: Int) {
            if (size + count > data.size) data = data.copyOf(maxOf(size + count, data.size + (data.size shr 1)))
            System.arraycopy(samples, 0, data, size, count)
            size += count
        }

        operator fun get(i: Int): Short = data[i]
    }
}
