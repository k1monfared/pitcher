package pitcher.core

import kotlin.math.roundToInt

/** Placement rules for text drawn on the song chart, and slide-to-pick steps. */
object ChartLabels {

    enum class Fit { FULL, ELLIPSIZED, NONE }

    /**
     * X for a label beside a vertical line at [anchorX]: to its right when it
     * fits inside [viewWidth], otherwise to its left, never off the view.
     */
    fun sideLabelX(anchorX: Float, labelWidth: Float, viewWidth: Float, gap: Float): Float {
        val right = anchorX + gap
        if (right + labelWidth <= viewWidth) return right
        return (anchorX - gap - labelWidth).coerceIn(0f, maxOf(0f, viewWidth - labelWidth))
    }

    /**
     * Whether a label fits in [available] px: whole, cut to at least one letter
     * and an ellipsis ([minimalWidth]), or not at all.
     */
    fun fit(available: Float, fullWidth: Float, minimalWidth: Float): Fit = when {
        fullWidth <= available -> Fit.FULL
        minimalWidth <= available -> Fit.ELLIPSIZED
        else -> Fit.NONE
    }

    /**
     * The picked index after the finger travels [deltaPx] from where the
     * gesture began on [startIndex], one item per [stepPx].
     */
    fun slideIndex(startIndex: Int, deltaPx: Float, stepPx: Float, count: Int): Int {
        if (count <= 0) return 0
        val steps = if (stepPx > 0f) (deltaPx / stepPx).roundToInt() else 0
        return (startIndex + steps).coerceIn(0, count - 1)
    }
}
