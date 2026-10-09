package pitcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

class PitchShifterTest {

    private fun sine(hz: Double, sr: Int = 44100, secs: Double = 1.0): FloatArray {
        val n = (sr * secs).toInt()
        return FloatArray(n) { i ->
            (0.6 * kotlin.math.sin(2.0 * Math.PI * hz * i / sr)).toFloat()
        }
    }

    @Test
    fun zeroCentsKeepsPitch() {
        val out = PitchShifter.shift(sine(440.0), 44100, 0)
        val r = Tuner.detect(out, 44100)!!
        assertTrue("hz=${r.hz}", abs(r.hz - 440.0) < 4.0)
    }

    @Test
    fun downOneSemitoneLowersPitch() {
        val out = PitchShifter.shift(sine(440.0), 44100, -100)
        val r = Tuner.detect(out, 44100)!!
        val expected = 440.0 * Math.pow(2.0, -100.0 / 1200.0)
        assertTrue("hz=${r.hz} expected~$expected", abs(r.hz - expected) < 5.0)
    }

    @Test
    fun upSevenSemitonesRaisesPitch() {
        val out = PitchShifter.shift(sine(440.0), 44100, 700)
        val r = Tuner.detect(out, 44100)!!
        val expected = 440.0 * Math.pow(2.0, 700.0 / 1200.0)
        assertTrue("hz=${r.hz} expected~$expected", abs(r.hz - expected) < 8.0)
    }

    @Test
    fun preservesDuration() {
        val input = sine(440.0, secs = 1.0)
        val out = PitchShifter.shift(input, 44100, -300)
        val ratio = out.size.toDouble() / input.size
        assertTrue("ratio=$ratio", ratio in 0.98..1.02)
    }

    @Test
    fun fasterSpeedShortensTheAudioAndKeepsThePitch() {
        val input = sine(440.0, secs = 2.0)
        val out = PitchShifter.shift(input, 44100, 0, speed = 2.0)
        val ratio = out.size.toDouble() / (input.size / 2.0)
        assertTrue("ratio=$ratio", ratio in 0.97..1.03)
        val r = Tuner.detect(out, 44100)!!
        assertTrue("hz=${r.hz}", abs(r.hz - 440.0) < 5.0)
    }

    @Test
    fun slowerSpeedLengthensTheAudio() {
        val input = sine(440.0, secs = 1.0)
        val out = PitchShifter.shift(input, 44100, 0, speed = 0.5)
        val ratio = out.size.toDouble() / (input.size * 2.0)
        assertTrue("ratio=$ratio", ratio in 0.97..1.03)
    }

    @Test
    fun pitchAndSpeedApplyTogether() {
        val input = sine(440.0, secs = 2.0)
        val out = PitchShifter.shift(input, 44100, -300, speed = 1.25)
        val ratio = out.size.toDouble() / (input.size / 1.25)
        assertTrue("ratio=$ratio", ratio in 0.97..1.03)
        val expected = 440.0 * Math.pow(2.0, -300.0 / 1200.0)
        val r = Tuner.detect(out, 44100)!!
        assertTrue("hz=${r.hz} expected~$expected", abs(r.hz - expected) < 6.0)
    }

    @Test
    fun handlesSilence() {
        val out = PitchShifter.shift(FloatArray(44100), 44100, -100)
        assertEquals(44100.0, out.size.toDouble(), 1000.0)
        assertTrue(out.all { it.isFinite() })
    }

    @Test
    fun emptyInputReturnsEmpty() {
        assertEquals(0, PitchShifter.shift(FloatArray(0), 44100, 100).size)
    }

    @Test
    fun throwsWhenCancelledBeforeStart() {
        try {
            PitchShifter.shift(sine(440.0, secs = 2.0), 44100, -300) { true }
            fail("expected cancellation")
        } catch (_: CancellationException) {
        }
    }

    @Test
    fun stopsWhenCancelledPartway() {
        var checks = 0
        try {
            PitchShifter.shift(sine(440.0, secs = 3.0), 44100, -300) {
                checks++
                checks > 3
            }
            fail("expected cancellation")
        } catch (_: CancellationException) {
        }
        assertTrue("checks=$checks", checks > 3)
    }
}
