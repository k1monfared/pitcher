package pitcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WaveViewTest {

    @Test
    fun followAtKeepsThePlayheadAtTheAnchor() {
        val w = WaveView.followAt(WaveView.Window(0.0, 0.2), playFrac = 0.5, anchor = 0.75)!!
        assertEquals(0.35, w.start, 1e-9)
        assertEquals(0.55, w.end, 1e-9)
    }

    @Test
    fun followAtClampsAtTheTrackEnds() {
        val early = WaveView.followAt(WaveView.Window(0.4, 0.6), playFrac = 0.05, anchor = 0.75)!!
        assertEquals(0.0, early.start, 1e-9)
        val late = WaveView.followAt(WaveView.Window(0.0, 0.2), playFrac = 0.99, anchor = 0.75)!!
        assertEquals(1.0, late.end, 1e-9)
    }

    @Test
    fun followAtLeavesTheFullViewAlone() {
        assertNull(WaveView.followAt(null, playFrac = 0.5, anchor = 0.75))
    }

    @Test
    fun fullWindowIsNull() {
        assertNull(WaveView.clamp(0.0, 1.0, 1.0))
    }

    @Test
    fun clampsInsideTrackKeepingSpan() {
        val v = WaveView.clamp(-0.1, 0.2, 1.0)!!
        assertEquals(0.0, v.start, 1e-9)
        assertEquals(0.3, v.end, 1e-9)
        val v2 = WaveView.clamp(0.9, 1.1, 1.0)!!
        assertEquals(0.8, v2.start, 1e-9)
        assertEquals(1.0, v2.end, 1e-9)
    }

    @Test
    fun enforcesMinimumSpan() {
        val v = WaveView.clamp(0.5, 0.5001, 1.0)!!
        assertEquals(WaveView.MIN_SPAN, v.end - v.start, 1e-9)
    }

    @Test
    fun zoomInAroundCenter() {
        val v = WaveView.zoomAt(WaveView.Window(0.0, 1.0), 0.25, 0.5)!!
        assertEquals(0.5, v.end - v.start, 1e-9)
        assertEquals(0.125, v.start, 1e-9)
    }

    @Test
    fun zoomOutPastFullReturnsNull() {
        assertNull(WaveView.zoomAt(WaveView.Window(0.0, 1.0), 0.5, 2.0))
    }

    @Test
    fun panShiftsWindow() {
        val v = WaveView.panBy(WaveView.Window(0.2, 0.6), 0.1)!!
        assertEquals(0.3, v.start, 1e-9)
        assertEquals(0.7, v.end, 1e-9)
    }

    @Test
    fun panClampsAtEdges() {
        val v = WaveView.panBy(WaveView.Window(0.2, 0.6), -0.5)!!
        assertEquals(0.0, v.start, 1e-9)
        assertEquals(0.4, v.end, 1e-9)
    }

    @Test
    fun followKeepsPlayheadWithMargin() {
        val window = WaveView.Window(0.0, 0.5)
        val inside = WaveView.follow(window, 0.25)
        assertEquals(window, inside)
        val shifted = WaveView.follow(window, 0.48)!!
        assertEquals(0.03, shifted.start, 1e-9)
        assertEquals(0.53, shifted.end, 1e-9)
    }

    @Test
    fun followPassesThroughFullWindow() {
        assertNull(WaveView.follow(null, 0.5))
    }

    @Test
    fun timeToFracRoundTrip() {
        val window = WaveView.Window(0.2, 0.6)
        assertEquals(8.0, WaveView.fracToTime(window, 0.5, 20.0), 1e-9)
        assertEquals(0.5, WaveView.timeToFrac(window, 8.0, 20.0), 1e-9)
    }
}
