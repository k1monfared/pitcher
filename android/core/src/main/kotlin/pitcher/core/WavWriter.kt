package pitcher.core

import java.io.BufferedOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal 16-bit PCM WAV writer for rendered pitch variants. */
object WavWriter {

    fun write(file: File, channels: Array<FloatArray>, sampleRate: Int) {
        val numChannels = channels.size.coerceAtLeast(1)
        val frames = if (channels.isEmpty()) 0 else channels[0].size
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        val dataSize = frames * blockAlign

        file.parentFile?.mkdirs()
        BufferedOutputStream(file.outputStream()).use { out ->
            out.write("RIFF".toByteArray(Charsets.US_ASCII))
            out.write(le32(36 + dataSize))
            out.write("WAVE".toByteArray(Charsets.US_ASCII))

            out.write("fmt ".toByteArray(Charsets.US_ASCII))
            out.write(le32(16))
            out.write(le16(1)) // PCM
            out.write(le16(numChannels))
            out.write(le32(sampleRate))
            out.write(le32(byteRate))
            out.write(le16(blockAlign))
            out.write(le16(bitsPerSample))

            out.write("data".toByteArray(Charsets.US_ASCII))
            out.write(le32(dataSize))

            val frame = ByteArray(blockAlign)
            for (i in 0 until frames) {
                var offset = 0
                for (c in 0 until numChannels) {
                    val v = channels[c].getOrElse(i) { 0f }.coerceIn(-1f, 1f)
                    val s = (v * 32767f).toInt()
                    frame[offset] = (s and 0xFF).toByte()
                    frame[offset + 1] = ((s shr 8) and 0xFF).toByte()
                    offset += 2
                }
                out.write(frame)
            }
        }
    }

    private fun le32(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    private fun le16(value: Int): ByteArray =
        ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array()
}
