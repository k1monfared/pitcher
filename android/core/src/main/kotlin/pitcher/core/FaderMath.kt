package pitcher.core

import kotlin.math.roundToInt

/**
 * Relative-drag math for the pitch fader, following the touch conventions of
 * DAW scrub zones and iOS seek bars: the value never jumps on touch-down, and
 * the horizontal offset from the touch anchor scales sensitivity, so sliding
 * left tunes finely and sliding right moves fast. Vertical movement is the
 * value.
 */
object FaderMath {

    const val FINE = 0.2
    const val NORMAL = 1.0
    const val COARSE = 5.0
    const val ZONE_FRAC = 0.35

    enum class Mode { FINE, NORMAL, COARSE }

    fun sensitivity(dxPx: Float, zonePx: Float): Double {
        if (zonePx <= 0f) return NORMAL
        val t = (dxPx / zonePx).coerceIn(-1f, 1f).toDouble()
        return if (t < 0) NORMAL + (FINE - NORMAL) * (-t) else NORMAL + (COARSE - NORMAL) * t
    }

    fun modeFor(dxPx: Float, zonePx: Float): Mode {
        val s = sensitivity(dxPx, zonePx)
        return when {
            s < NORMAL - 0.05 -> Mode.FINE
            s > NORMAL + 0.05 -> Mode.COARSE
            else -> Mode.NORMAL
        }
    }

    /**
     * [dyPx] is the vertical movement with up positive (anchorY - currentY).
     * [dxPx] is the horizontal offset from the anchor.
     */
    fun centsFromDrag(
        startCents: Int,
        dyPx: Float,
        dxPx: Float,
        heightPx: Float,
        range: Int,
        zoneFrac: Double = ZONE_FRAC,
    ): Int {
        if (heightPx <= 0f) return startCents
        val baseCentsPerPx = (2.0 * range) / heightPx
        val zonePx = (zoneFrac * heightPx).toFloat()
        val gain = sensitivity(dxPx, zonePx)
        val delta = dyPx * baseCentsPerPx * gain
        return (startCents + delta).roundToInt().coerceIn(-range, range)
    }
}
