package pitcher.android.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pitcher.core.Tuner

/** Renders go through the same Sonic engine as live playback. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SonicShifterTest {

    private val sr = 44100

    private fun sine(hz: Double, secs: Double): FloatArray =
        FloatArray((sr * secs).toInt()) { i -> (0.5 * sin(2.0 * Math.PI * hz * i / sr)).toFloat() }

    private fun stereo(hz: Double, secs: Double) = arrayOf(sine(hz, secs), sine(hz, secs))

    @Test
    fun shiftsPitchAndKeepsLength() {
        val out = SonicShifter.process(stereo(440.0, 2.0), sr, cents = 700, speed = 1.0)
        assertEquals(2, out.size)
        val ratio = out[0].size.toDouble() / (2.0 * sr)
        assertTrue("ratio=$ratio", ratio in 0.98..1.02)
        val expected = 440.0 * Math.pow(2.0, 700 / 1200.0)
        val hz = Tuner.detect(out[0].copyOfRange(sr / 2, sr / 2 + 8192), sr)!!.hz
        assertTrue("hz=$hz expected~$expected", abs(hz - expected) < 8.0)
    }

    @Test
    fun speedChangesLengthNotPitch() {
        val out = SonicShifter.process(stereo(440.0, 2.0), sr, cents = 0, speed = 2.0)
        val ratio = out[0].size.toDouble() / sr
        assertTrue("ratio=$ratio", ratio in 0.97..1.03)
        val hz = Tuner.detect(out[0].copyOfRange(sr / 4, sr / 4 + 8192), sr)!!.hz
        assertTrue("hz=$hz", abs(hz - 440.0) < 6.0)
    }

    @Test
    fun noChangeIsACopy() {
        val input = stereo(440.0, 0.5)
        val out = SonicShifter.process(input, sr, cents = 0, speed = 1.0)
        assertTrue(out[0].contentEquals(input[0]))
        assertTrue(out[1].contentEquals(input[1]))
    }

    @Test
    fun streamingInChunksMatchesTheWholeLength() {
        val input = sine(440.0, 2.0)
        val pcm = ShortArray(input.size) { (input[it] * 32767f).toInt().toShort() }
        var total = 0
        val stream = SonicShifter.Stream(sr, channels = 1, cents = -300, speed = 1.0)
        var pos = 0
        while (pos < pcm.size) {
            val n = minOf(4096, pcm.size - pos)
            stream.feed(pcm.copyOfRange(pos, pos + n), n) { _, count -> total += count }
            pos += n
        }
        stream.finish { _, count -> total += count }
        val ratio = total.toDouble() / pcm.size
        assertTrue("ratio=$ratio", ratio in 0.98..1.02)
    }
}
