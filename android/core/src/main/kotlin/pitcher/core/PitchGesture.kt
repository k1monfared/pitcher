package pitcher.core

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The full-screen pitch gesture. Vertical movement changes the pitch; the
 * horizontal offset from the touch-down point sets sensitivity, continuously,
 * from coarse (left of the start) to fine (right of the start). The mapping is
 * incremental per pointer move, so changing gear mid-drag never jumps the value.
 */
object PitchGesture {

    const val COARSE = 8.0
    const val FINE = 0.15
    const val DEAD_ZONE_PX = 24.0

    enum class Mode { COARSE, NORMAL, FINE }

    /** Travel that spans the full gear range, as a fraction of screen width. */
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
        if (snapCents > 0) {
            next = ((next / snapCents).roundToInt() * snapCents).toDouble()
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
