package pitcher.core

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Saves a song's waveform peaks so opening it again skips decoding the whole
 * file. The cache records the source's size and modification time and is
 * ignored once either changes.
 */
object PeaksCache {

    private const val MAGIC = 0x504B5331 // "PKS1"

    fun read(cache: File, source: File): FloatArray? = runCatching {
        if (!cache.isFile) return null
        DataInputStream(cache.inputStream().buffered()).use { input ->
            if (input.readInt() != MAGIC) return null
            if (input.readLong() != source.length()) return null
            if (input.readLong() != source.lastModified()) return null
            val count = input.readInt()
            if (count <= 0 || count > 1_000_000) return null
            FloatArray(count) { input.readFloat() }
        }
    }.getOrNull()

    fun write(cache: File, source: File, peaks: FloatArray) {
        if (peaks.isEmpty()) return
        runCatching {
            cache.parentFile?.mkdirs()
            val part = File(cache.path + ".part")
            DataOutputStream(part.outputStream().buffered()).use { out ->
                out.writeInt(MAGIC)
                out.writeLong(source.length())
                out.writeLong(source.lastModified())
                out.writeInt(peaks.size)
                peaks.forEach { out.writeFloat(it) }
            }
            if (!part.renameTo(cache)) part.delete()
        }
    }
}
