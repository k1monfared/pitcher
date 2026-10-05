package pitcher.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WavWriterTest {

    @Test
    fun writesRiffHeaderAndDataSize() {
        val dir = File(System.getProperty("java.io.tmpdir"), "pitcher-wav-${System.nanoTime()}")
        dir.mkdirs()
        val file = File(dir, "out.wav")
        val channels = arrayOf(
            FloatArray(1000) { 0.5f },
            FloatArray(1000) { -0.5f },
        )
        WavWriter.write(file, channels, 44100)

        assertTrue(file.exists())
        assertEquals(44L + 1000L * 2 * 2, file.length())

        val bytes = file.readBytes()
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(bytes, 12, 4, Charsets.US_ASCII))
        assertEquals("data", String(bytes, 36, 4, Charsets.US_ASCII))
        file.delete()
        dir.delete()
    }

    @Test
    fun handlesEmptyChannels() {
        val dir = File(System.getProperty("java.io.tmpdir"), "pitcher-wav-${System.nanoTime()}")
        dir.mkdirs()
        val file = File(dir, "empty.wav")
        WavWriter.write(file, arrayOf(FloatArray(0)), 44100)
        assertEquals(44L, file.length())
        file.delete()
        dir.delete()
    }
}
