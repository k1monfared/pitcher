package pitcher.core

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class FaderMathTest {

    private val range = 1200
    private val height = 600f

    @Test
    fun sensitivityAtAnchorIsNormal() {
        assertEquals(1.0, FaderMath.sensitivity(0f, 210f), 1e-9)
    }

    @Test
    fun sensitivityLeftIsFineRightIsCoarse() {
        assertEquals(FaderMath.FINE, FaderMath.sensitivity(-210f, 210f), 1e-9)
        assertEquals(FaderMath.COARSE, FaderMath.sensitivity(210f, 210f), 1e-9)
    }

    @Test
    fun sensitivityClampsBeyondZone() {
        assertEquals(FaderMath.FINE, FaderMath.sensitivity(-1000f, 210f), 1e-9)
        assertEquals(FaderMath.COARSE, FaderMath.sensitivity(1000f, 210f), 1e-9)
    }

    @Test
    fun dragUpIncreasesCents() {
        val c = FaderMath.centsFromDrag(0, 100f, 0f, height, range)
        assertEquals(400, c)
    }

    @Test
    fun dragDownDecreasesCents() {
        val c = FaderMath.centsFromDrag(0, -100f, 0f, height, range)
        assertEquals(-400, c)
    }

    @Test
    fun dragStartsFromCurrentCents() {
        val c = FaderMath.centsFromDrag(300, 100f, 0f, height, range)
        assertEquals(700, c)
    }

    @Test
    fun slidingLeftIsFiner() {
        val normal = FaderMath.centsFromDrag(0, 100f, 0f, height, range)
        val fine = FaderMath.centsFromDrag(0, 100f, -210f, height, range)
        assertEquals(80, fine)
        assert(abs(fine) < abs(normal))
    }

    @Test
    fun slidingRightIsCoarser() {
        val normal = FaderMath.centsFromDrag(0, 30f, 0f, height, range)
        val coarse = FaderMath.centsFromDrag(0, 30f, 210f, height, range)
        assertEquals(600, coarse)
        assert(coarse > normal)
    }

    @Test
    fun coarseClampsToRange() {
        assertEquals(1200, FaderMath.centsFromDrag(0, 100f, 210f, height, range))
    }

    @Test
    fun clampsToRange() {
        assertEquals(1200, FaderMath.centsFromDrag(0, 100000f, 0f, height, range))
        assertEquals(-1200, FaderMath.centsFromDrag(0, -100000f, 0f, height, range))
    }

    @Test
    fun modeNamesReflectOffset() {
        assertEquals(FaderMath.Mode.FINE, FaderMath.modeFor(-100f, 210f))
        assertEquals(FaderMath.Mode.NORMAL, FaderMath.modeFor(0f, 210f))
        assertEquals(FaderMath.Mode.COARSE, FaderMath.modeFor(100f, 210f))
    }

    @Test
    fun zeroDragIsIdentity() {
        assertEquals(500, FaderMath.centsFromDrag(500, 0f, 0f, height, range))
    }
}
