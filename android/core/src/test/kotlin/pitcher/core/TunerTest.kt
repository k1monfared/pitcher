package pitcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TunerTest {

    private fun sine(hz: Double, sr: Int = 44100, secs: Double = 0.5): FloatArray {
        val n = (sr * secs).toInt()
        return FloatArray(n) { i ->
            val t = i.toDouble() / sr
            (0.6 * kotlin.math.sin(2.0 * Math.PI * hz * t)).toFloat()
        }
    }

    @Test
    fun detectsA4() {
        val r = Tuner.detect(sine(440.0), 44100)!!
        assertTrue("hz=${r.hz}", kotlin.math.abs(r.hz - 440.0) < 2.0)
        assertEquals("A4", r.note.name)
        assertTrue(kotlin.math.abs(r.note.centsOff) < 5.0)
        assertTrue(r.confidence > 0.5)
    }

    @Test
    fun detectsCSharp4() {
        val hz = 277.1826309768721
        val r = Tuner.detect(sine(hz), 44100)!!
        assertTrue("hz=${r.hz}", kotlin.math.abs(r.hz - hz) < 2.0)
        assertEquals("C#4", r.note.name)
        assertTrue(kotlin.math.abs(r.note.centsOff) < 5.0)
    }

    @Test
    fun detectsLowA2() {
        val r = Tuner.detect(sine(110.0), 44100)!!
        assertEquals("A2", r.note.name)
        assertTrue(kotlin.math.abs(r.hz - 110.0) < 1.5)
    }

    @Test
    fun detectsC5() {
        val r = Tuner.detect(sine(523.2511306011972), 44100)!!
        assertEquals("C5", r.note.name)
    }

    @Test
    fun silenceReturnsNoPitch() {
        val r = Tuner.detect(FloatArray(22050), 44100)
        assertTrue(r == null || r.hz <= 0.0 || r.confidence < 0.2)
    }

    @Test
    fun tooFewSamplesReturnsNull() {
        assertTrue(Tuner.detect(FloatArray(10), 44100) == null)
    }
}
