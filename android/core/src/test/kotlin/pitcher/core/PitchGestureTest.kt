package pitcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchGestureTest {

    @Test
    fun slowPointerIsFineFastPointerIsCoarse() {
        assertEquals(PitchGesture.FINE_CENTS_PER_PX, PitchGesture.centsPerPx(0.0), 1e-9)
        assertEquals(
            PitchGesture.COARSE_CENTS_PER_PX,
            PitchGesture.centsPerPx(PitchGesture.FAST_SPEED_PX_PER_S),
            1e-9,
        )
        assertTrue(
            PitchGesture.centsPerPx(PitchGesture.FAST_SPEED_PX_PER_S) >
                PitchGesture.centsPerPx(0.0),
        )
    }

    @Test
    fun sensitivityIsMonotonicInSpeed() {
        var prev = 0.0
        var s = PitchGesture.SLOW_SPEED_PX_PER_S
        while (s <= PitchGesture.FAST_SPEED_PX_PER_S) {
            val c = PitchGesture.centsPerPx(s)
            assertTrue("sensitivity should not drop at speed=$s", c >= prev - 1e-9)
            prev = c
            s += 50.0
        }
    }

    @Test
    fun sensitivityIsClampedOutsideTheSpeedWindow() {
        assertEquals(PitchGesture.FINE_CENTS_PER_PX, PitchGesture.centsPerPx(-100.0), 1e-9)
        assertEquals(
            PitchGesture.COARSE_CENTS_PER_PX,
            PitchGesture.centsPerPx(1e9),
            1e-9,
        )
    }

    @Test
    fun accumulationIsContinuousAndDoesNotLoseSmallMoves() {
        var value = 0.0
        var travel = 100.0
        val deltas = mutableListOf<Double>()
        repeat(10) {
            val next = PitchGesture.advance(
                rawCents = value,
                dyPx = 1.0,
                totalTravelPx = travel,
                speedPxPerSec = 0.0,
                rampPx = 0.0,
            )
            deltas.add(next - value)
            value = next
            travel += 1.0
        }
        val first = deltas.first()
        for (d in deltas) assertEquals("each 1px move should add the same amount", first, d, 1e-9)
        assertTrue(first > 0.0)
    }

    @Test
    fun precisionRampScalesEarlyMovementThenLatches() {
        val ramp = 100.0
        val full = PitchGesture.advance(0.0, dyPx = 2.0, totalTravelPx = ramp, speedPxPerSec = 0.0, rampPx = ramp)
        val half = PitchGesture.advance(0.0, dyPx = 2.0, totalTravelPx = ramp / 2, speedPxPerSec = 0.0, rampPx = ramp)
        val past = PitchGesture.advance(0.0, dyPx = 2.0, totalTravelPx = ramp * 3, speedPxPerSec = 0.0, rampPx = ramp)
        assertEquals(full * 0.5, half, full * 0.001)
        assertEquals(full, past, full * 0.001)
    }

    @Test
    fun zeroRampMeansNoScaling() {
        val a = PitchGesture.advance(0.0, dyPx = 3.0, totalTravelPx = 1.0, speedPxPerSec = 0.0, rampPx = 0.0)
        val b = PitchGesture.advance(0.0, dyPx = 3.0, totalTravelPx = 900.0, speedPxPerSec = 0.0, rampPx = 0.0)
        assertEquals(a, b, 1e-9)
    }

    @Test
    fun directionIsSymmetric() {
        val up = PitchGesture.advance(0.0, dyPx = 30.0, totalTravelPx = 200.0, speedPxPerSec = 0.0, rampPx = 0.0)
        val down = PitchGesture.advance(0.0, dyPx = -30.0, totalTravelPx = 200.0, speedPxPerSec = 0.0, rampPx = 0.0)
        assertEquals(up, -down, 1e-9)
    }

    @Test
    fun advanceClampsToRange() {
        assertEquals(
            PitchGesture.MAX_CENTS.toDouble(),
            PitchGesture.advance(1000.0, dyPx = 10000.0, totalTravelPx = 1000.0, speedPxPerSec = 1e6, rampPx = 0.0),
            1e-9,
        )
        assertEquals(
            PitchGesture.MIN_CENTS.toDouble(),
            PitchGesture.advance(-1000.0, dyPx = -10000.0, totalTravelPx = 1000.0, speedPxPerSec = 1e6, rampPx = 0.0),
            1e-9,
        )
    }

    @Test
    fun zeroMoveIsIdentity() {
        assertEquals(
            250.0,
            PitchGesture.advance(250.0, dyPx = 0.0, totalTravelPx = 500.0, speedPxPerSec = 100.0, rampPx = 24.0),
            1e-9,
        )
    }

    @Test
    fun fastMoveGoesFurtherThanSlowMove() {
        val fast = PitchGesture.advance(0.0, dyPx = 50.0, totalTravelPx = 500.0, speedPxPerSec = 3000.0, rampPx = 0.0)
        val slow = PitchGesture.advance(0.0, dyPx = 50.0, totalTravelPx = 500.0, speedPxPerSec = 0.0, rampPx = 0.0)
        assertTrue("fast=$fast slow=$slow", fast > slow * 5)
    }

    @Test
    fun grainIsOneOfTheLevelsAndMonotonic() {
        var prev = 0
        var s = 0.0
        while (s <= PitchGesture.FAST_SPEED_PX_PER_S * 1.5) {
            val g = PitchGesture.grain(s)
            assertTrue("grain=$g not a level", g in PitchGesture.GRAINS)
            assertTrue("grain should not drop", g >= prev)
            prev = g
            s += 40.0
        }
        assertEquals(1, PitchGesture.grain(0.0))
        assertEquals(PitchGesture.GRAINS.last(), PitchGesture.grain(1e9))
    }

    @Test
    fun dualSnapPullsHardNearSemitonesAndSoftElsewhere() {
        assertEquals(0, PitchGesture.snapCents(6.0))
        assertEquals(100, PitchGesture.snapCents(106.0))
        assertEquals(20, PitchGesture.snapCents(24.0))
        assertEquals(100, PitchGesture.snapCents(112.0))
        assertEquals(120, PitchGesture.snapCents(114.0))
        assertEquals(-100, PitchGesture.snapCents(-112.0))
        assertEquals(0, PitchGesture.snapCents(0.0))
    }

    @Test
    fun flingDeceleratesMovesThenStops() {
        var cents = 0.0
        var velocity = 800.0
        var done = false
        var steps = 0
        while (!done && steps < 100_000) {
            val f = PitchGesture.fling(cents, velocity, dtSeconds = 1.0 / 60.0)
            cents = f.cents
            velocity = f.velocityCentsPerSec
            done = f.done
            steps++
        }
        assertTrue("should terminate", done)
        assertTrue("should move forward, cents=$cents", cents > 0.0)
        assertTrue("should decelerate", velocity < 800.0)
        assertTrue("should settle under a second or so, steps=$steps", steps < 120)
    }

    @Test
    fun flingStopsAtTheClamp() {
        val f = PitchGesture.fling(1190.0, 5000.0, dtSeconds = 1.0 / 60.0)
        assertTrue(f.cents <= PitchGesture.MAX_CENTS.toDouble())
        assertTrue(f.done)
    }

    @Test
    fun smoothedSpeedEasesTowardTheInstantValue() {
        val a = PitchGesture.smoothedSpeed(previous = 0.0, instantPxPerSec = 1000.0, alpha = 0.25)
        assertTrue(a > 0.0 && a < 1000.0)
        assertEquals(1000.0, PitchGesture.smoothedSpeed(1000.0, 1000.0, 0.25), 1e-9)
    }
}
