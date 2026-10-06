package pitcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchGestureTest {

    private val width = 1000f

    @Test
    fun normalAtStart() {
        val s = PitchGesture.sensitivity(0f, width)
        val geometricMean = Math.sqrt(PitchGesture.COARSE * PitchGesture.FINE)
        assertEquals(geometricMean, s, 1e-6)
        assertEquals(PitchGesture.Mode.NORMAL, PitchGesture.mode(0f, width))
    }

    @Test
    fun coarseToTheLeftFineToTheRight() {
        assertEquals(PitchGesture.COARSE, PitchGesture.sensitivity(-width / 2f, width), 1e-6)
        assertEquals(PitchGesture.FINE, PitchGesture.sensitivity(width / 2f, width), 1e-6)
        assertEquals(PitchGesture.Mode.COARSE, PitchGesture.mode(-width / 2f, width))
        assertEquals(PitchGesture.Mode.FINE, PitchGesture.mode(width / 2f, width))
    }

    @Test
    fun sensitivityDecreasesWithRightwardOffset() {
        var prev = Double.MAX_VALUE
        var d = -width / 2f
        while (d <= width / 2f) {
            val s = PitchGesture.sensitivity(d, width)
            assertTrue("sensitivity should decrease, at d=$d", s <= prev + 1e-9)
            prev = s
            d += 25f
        }
    }

    @Test
    fun deadZoneKeepsNormal() {
        assertEquals(PitchGesture.Mode.NORMAL, PitchGesture.mode(10f, width))
        assertEquals(PitchGesture.Mode.NORMAL, PitchGesture.mode(-10f, width))
    }

    @Test
    fun stepUpIncreasesCents() {
        val out = PitchGesture.step(0, dyPx = 100f, offsetPx = 0f, widthPx = width)
        assertTrue("expected increase, got $out", out > 0)
    }

    @Test
    fun stepDownDecreasesCents() {
        val out = PitchGesture.step(0, dyPx = -100f, offsetPx = 0f, widthPx = width)
        assertTrue(out < 0)
    }

    @Test
    fun coarseMovesMoreThanFine() {
        val coarse = PitchGesture.step(0, dyPx = 100f, offsetPx = -width / 2f, widthPx = width)
        val fine = PitchGesture.step(0, dyPx = 100f, offsetPx = width / 2f, widthPx = width)
        assertTrue("coarse=$coarse fine=$fine", coarse > fine * 5)
    }

    @Test
    fun stepAccumulatesFromCurrentCents() {
        val out = PitchGesture.step(300, dyPx = 100f, offsetPx = 0f, widthPx = width)
        assertTrue(out > 300)
    }

    @Test
    fun stepClampsToRange() {
        assertEquals(1200, PitchGesture.step(0, dyPx = 100000f, offsetPx = 0f, widthPx = width))
        assertEquals(-1200, PitchGesture.step(0, dyPx = -100000f, offsetPx = 0f, widthPx = width))
    }

    @Test
    fun zeroWidthIsSafe() {
        assertEquals(PitchGesture.Mode.NORMAL, PitchGesture.mode(50f, 0f))
    }

    @Test
    fun stepNoVerticalMoveIsIdentity() {
        assertEquals(250, PitchGesture.step(250, dyPx = 0f, offsetPx = width / 4f, widthPx = width))
    }
}
