package pitcher.core

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class WavStreamTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("wav").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun chunksAreWrittenInOrderAndTheHeaderIsPatched() {
        val file = File(dir, "out.wav")
        WavStream(file, sampleRate = 22050, channels = 2).use { w ->
            w.write(shortArrayOf(1, -1, 2, -2), count = 4)
            w.write(shortArrayOf(3, -3, 99, 99), count = 2)
        }
        val bytes = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(44 + 12, file.length().toInt())
        assertEquals(36 + 12, bytes.getInt(4))
        assertEquals(2, bytes.getShort(22).toInt())
        assertEquals(22050, bytes.getInt(24))
        assertEquals(12, bytes.getInt(40))
        val samples = (0 until 6).map { bytes.getShort(44 + it * 2).toInt() }
        assertEquals(listOf(1, -1, 2, -2, 3, -3), samples)
    }

    @Test
    fun anEmptyStreamIsAValidEmptyWav() {
        val file = File(dir, "empty.wav")
        WavStream(file, sampleRate = 44100, channels = 1).close()
        val bytes = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(44, file.length().toInt())
        assertEquals(0, bytes.getInt(40))
    }
}
