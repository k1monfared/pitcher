package pitcher.core

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes a 16-bit PCM WAV a chunk at a time, so a whole song never has to be
 * held in memory. The header's sizes are filled in on [close].
 */
class WavStream(
    private val file: File,
    sampleRate: Int,
    private val channels: Int,
) : Closeable {

    private val out: BufferedOutputStream
    private var dataBytes = 0L
    private var bytes = ByteArray(0)

    init {
        file.parentFile?.mkdirs()
        out = BufferedOutputStream(file.outputStream(), 1 shl 16)
        val blockAlign = channels * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(sampleRate * blockAlign)
            putShort(blockAlign.toShort())
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(0)
        }
        out.write(header.array())
    }

    /** Appends the first [count] interleaved samples of [samples]. */
    fun write(samples: ShortArray, count: Int) {
        if (count <= 0) return
        if (bytes.size < count * 2) bytes = ByteArray(count * 2)
        for (i in 0 until count) {
            val s = samples[i].toInt()
            bytes[i * 2] = (s and 0xFF).toByte()
            bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        out.write(bytes, 0, count * 2)
        dataBytes += count * 2L
    }

    override fun close() {
        out.close()
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(4)
            raf.write(le32((36 + dataBytes).toInt()))
            raf.seek(40)
            raf.write(le32(dataBytes.toInt()))
        }
    }

    private fun le32(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
}
