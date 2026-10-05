package pitcher.core

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class Mp3WriterTest {

    private fun sine(hz: Double, sr: Int = 44100, secs: Double = 0.5): FloatArray {
        val n = (sr * secs).toInt()
        return FloatArray(n) { i -> (0.5 * sin(2.0 * Math.PI * hz * i / sr)).toFloat() }
    }

    @Test
    fun writesNonEmptyMp3() {
        val dir = File(System.getProperty("java.io.tmpdir"), "pitcher-mp3-${System.nanoTime()}")
        dir.mkdirs()
        val file = File(dir, "out.mp3")
        Mp3Writer.write(file, arrayOf(sine(440.0)), 44100)

        assertTrue(file.exists())
        assertTrue("size=${file.length()}", file.length() > 1000)
        val head = file.readBytes().take(3)
        val isId3 = head.size == 3 && head[0] == 'I'.code.toByte() &&
            head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()
        val isFrameSync = head.size >= 2 && (head[0].toInt() and 0xFF) == 0xFF &&
            (head[1].toInt() and 0xE0) == 0xE0
        assertTrue("not an mp3 header: $head", isId3 || isFrameSync)

        file.delete()
        dir.delete()
    }

    @Test
    fun writesStereoMp3() {
        val dir = File(System.getProperty("java.io.tmpdir"), "pitcher-mp3s-${System.nanoTime()}")
        dir.mkdirs()
        val file = File(dir, "stereo.mp3")
        Mp3Writer.write(file, arrayOf(sine(440.0), sine(660.0)), 44100)
        assertTrue(file.length() > 1000)
        file.delete()
        dir.delete()
    }
}
