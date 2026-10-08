package pitcher.core

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The full-screen pitch gesture, vertical only.
 *
 * A continuous accumulator is advanced by `dy * centsPerPixel`. The rate comes
 * from the pointer speed (slow is fine, fast is coarse), and the first few
 * pixels of a touch are finer than 1:1 (a precision ramp). The raw value is
 * never quantized, so small moves are never lost and changing rate mid-drag
 * does not jump. Snapping is applied only when asked, on a dual grid: a light
 * 20-cent grid and a stronger 100-cent (semitone) grid.
 */
object PitchGesture {

    const val MIN_CENTS = -1200
    const val MAX_CENTS = 1200

    /** Gear levels, fine to coarse, for the HUD label. */
    val GRAINS = intArrayOf(1, 5, 10, 25, 50, 100, 200)

    /** Cents per pixel at the fine and coarse ends of the speed window. */
    const val FINE_CENTS_PER_PX = 0.08
    const val COARSE_CENTS_PER_PX = 2.0

    /** Pointer speed window in pixels per second. */
    const val SLOW_SPEED_PX_PER_S = 120.0
    const val FAST_SPEED_PX_PER_S = 2400.0

    /** Dual magnetic snap grids. */
    const val LIGHT_GRID = 20
    const val STRONG_GRID = 100
    const val STRONG_RADIUS_CENTS = 12.0

    /** Momentum: exponential velocity decay and the stop threshold. */
    const val FRICTION = 4.0
    const val STOP_CENTS_PER_SEC = 8.0

    /** Cents per pixel for a pointer moving at [speedPxPerSec]. */
    fun centsPerPx(speedPxPerSec: Double): Double {
        val t = ((speedPxPerSec - SLOW_SPEED_PX_PER_S) /
            (FAST_SPEED_PX_PER_S - SLOW_SPEED_PX_PER_S)).coerceIn(0.0, 1.0)
        return FINE_CENTS_PER_PX * Math.pow(COARSE_CENTS_PER_PX / FINE_CENTS_PER_PX, t)
    }

    /** The coarse/fine gear for the HUD, one of [GRAINS]. */
    fun grain(speedPxPerSec: Double): Int {
        val ratio = Math.log(centsPerPx(speedPxPerSec) / FINE_CENTS_PER_PX) /
            Math.log(COARSE_CENTS_PER_PX / FINE_CENTS_PER_PX)
        val t = ratio.coerceIn(0.0, 1.0)
        val idx = (t * (GRAINS.size - 1)).roundToInt().coerceIn(0, GRAINS.size - 1)
        return GRAINS[idx]
    }

    /**
     * Advances the continuous value by [dyPx]. [totalTravelPx] is the net distance
     * travelled since the touch began, used by the precision ramp, which scales
     * the first [rampPx] pixels down to finer than 1:1 and then latches to 1:1.
     */
    fun advance(
        rawCents: Double,
        dyPx: Double,
        totalTravelPx: Double,
        speedPxPerSec: Double,
        rampPx: Double,
    ): Double {
        val rampScale = if (rampPx > 0.0) {
            (abs(totalTravelPx) / rampPx).coerceIn(0.0, 1.0)
        } else {
            1.0
        }
        val next = rawCents + dyPx * rampScale * centsPerPx(speedPxPerSec)
        return next.coerceIn(MIN_CENTS.toDouble(), MAX_CENTS.toDouble())
    }

    /**
     * Rounds [cents] on the dual grid: hard to the nearest 100 within
     * [STRONG_RADIUS_CENTS], otherwise to the nearest 20.
     */
    fun snapCents(cents: Double): Int {
        val strong = (cents / STRONG_GRID).roundToInt() * STRONG_GRID
        if (abs(cents - strong) <= STRONG_RADIUS_CENTS) return strong
        return (cents / LIGHT_GRID).roundToInt() * LIGHT_GRID
    }

    /** Low-pass the pointer speed so the rate does not flicker frame to frame. */
    fun smoothedSpeed(previous: Double, instantPxPerSec: Double, alpha: Double): Double =
        previous + alpha * (instantPxPerSec - previous)

    data class Fling(val cents: Double, val velocityCentsPerSec: Double, val done: Boolean)

    /**
     * One momentum frame. Decays the velocity exponentially, moves the value by
     * the average velocity over [dtSeconds], and reports when it has settled or
     * hit the clamp.
     */
    fun fling(
        rawCents: Double,
        velocityCentsPerSec: Double,
        dtSeconds: Double,
        friction: Double = FRICTION,
    ): Fling {
        if (abs(velocityCentsPerSec) < STOP_CENTS_PER_SEC) {
            return Fling(rawCents, 0.0, true)
        }
        val decayed = velocityCentsPerSec * Math.exp(-friction * dtSeconds)
        val next = rawCents + (velocityCentsPerSec + decayed) / 2.0 * dtSeconds
        if (next >= MAX_CENTS) return Fling(MAX_CENTS.toDouble(), 0.0, true)
        if (next <= MIN_CENTS) return Fling(MIN_CENTS.toDouble(), 0.0, true)
        val done = abs(decayed) < STOP_CENTS_PER_SEC
        return Fling(next, if (done) 0.0 else decayed, done)
    }
}
