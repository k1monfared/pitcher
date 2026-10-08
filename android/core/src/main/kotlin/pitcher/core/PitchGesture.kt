package pitcher.core

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The full-screen pitch gesture. Vertical movement changes the pitch; the
 * horizontal offset from the touch-down point selects a grain, continuously
 * from coarse at the left of the start to fine at the right. The value is
 * snapped to the current grain, so the increments are always a clean 1, 5, 10,
 * 25, 50, 100, or 200 cents. The mapping is incremental per pointer move, so
 * changing grain mid-drag never jumps the value.
 */
object PitchGesture {

    /** Cents per pixel at the coarse and fine ends. */
    const val COARSE = 8.0
    const val FINE = 0.15
    const val DEAD_ZONE_PX = 24.0

    /** Grain levels, coarse to fine. The rightmost is single-cent precision. */
    val GRAINS = intArrayOf(200, 100, 50, 25, 10, 5, 1)

    enum class Mode { COARSE, NORMAL, FINE }

    /** Travel that spans the full grain range, as a fraction of screen width. */
    private const val TRAVEL_FRAC = 0.5

    fun sensitivity(
        offsetPx: Float,
        widthPx: Float,
        coarse: Double = COARSE,
        fine: Double = FINE,
        deadZonePx: Double = DEAD_ZONE_PX,
    ): Double {
        val t = blend(offsetPx, widthPx, deadZonePx)
        return coarse * Math.pow(fine / coarse, t)
    }

    /** The current grain in cents, from 200 at the left to 1 at the right. */
    fun grain(offsetPx: Float, widthPx: Float, deadZonePx: Double = DEAD_ZONE_PX): Int {
        val t = blend(offsetPx, widthPx, deadZonePx)
        val idx = (t * (GRAINS.size - 1)).roundToInt().coerceIn(0, GRAINS.size - 1)
        return GRAINS[idx]
    }

    fun mode(offsetPx: Float, widthPx: Float, deadZonePx: Double = DEAD_ZONE_PX): Mode {
        val t = blend(offsetPx, widthPx, deadZonePx)
        return when {
            t < 0.42 -> Mode.COARSE
            t > 0.58 -> Mode.FINE
            else -> Mode.NORMAL
        }
    }

    fun step(
        currentCents: Int,
        dyPx: Float,
        offsetPx: Float,
        widthPx: Float,
        range: Int = 1200,
        snapCents: Int = 0,
    ): Int {
        val s = sensitivity(offsetPx, widthPx)
        var next = currentCents + dyPx * s
        val grain = if (snapCents > 0) snapCents else grain(offsetPx, widthPx)
        if (grain > 0) {
            next = (next / grain).roundToInt().toDouble() * grain
        }
        return next.roundToInt().coerceIn(-range, range)
    }

    /** Blend factor in 0..1: 0 is coarse (left), 1 is fine (right). */
    private fun blend(offsetPx: Float, widthPx: Float, deadZonePx: Double): Double {
        if (widthPx <= 0f) return 0.5
        val travel = (TRAVEL_FRAC * widthPx).toDouble()
        if (travel <= deadZonePx) return 0.5
        val d = offsetPx.toDouble()
        val mag = abs(d)
        if (mag <= deadZonePx) return 0.5
        val frac = ((mag - deadZonePx) / (travel - deadZonePx)).coerceIn(0.0, 1.0)
        return if (d > 0) 0.5 + 0.5 * frac else 0.5 - 0.5 * frac
    }
}
