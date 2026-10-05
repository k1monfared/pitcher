package pitcher.core

/**
 * Fractional window over a track for the waveform view. All values are
 * fractions of total duration in 0..1. Mirrors the web `view.ts` behavior:
 * zoom anchors on a point, pan shifts, follow keeps the playhead visible.
 */
object WaveView {

    const val MIN_SPAN = 0.02
    const val FOLLOW_MARGIN = 0.1

    data class Window(val start: Double, val end: Double)

    fun clamp(start: Double, end: Double, duration: Double): Window? {
        if (duration <= 0.0) return null
        var span = (end - start).coerceIn(MIN_SPAN, 1.0)
        if (span >= 1.0) return null
        val s = start.coerceIn(0.0, 1.0 - span)
        return Window(s, s + span)
    }

    fun zoomAt(window: Window, centerFrac: Double, factor: Double): Window? {
        val span = window.end - window.start
        if (span <= 0.0) return null
        val c = centerFrac.coerceIn(0.0, 1.0)
        val newSpan = span * factor
        if (newSpan >= 1.0) return null
        val ratio = (c - window.start) / span
        val start = c - ratio * newSpan
        return clamp(start, start + newSpan, 1.0)
    }

    fun panBy(window: Window, deltaFrac: Double): Window? {
        val span = window.end - window.start
        if (span >= 1.0) return null
        return clamp(window.start + deltaFrac, window.end + deltaFrac, 1.0)
    }

    fun follow(window: Window?, playFrac: Double): Window? {
        if (window == null) return null
        val span = window.end - window.start
        if (span >= 1.0) return null
        val lo = window.start + FOLLOW_MARGIN * span
        val hi = window.end - FOLLOW_MARGIN * span
        if (playFrac in lo..hi) return window
        val start = if (playFrac < lo) {
            playFrac - FOLLOW_MARGIN * span
        } else {
            playFrac - (1.0 - FOLLOW_MARGIN) * span
        }
        return clamp(start, start + span, 1.0)
    }

    fun fracToTime(window: Window, frac: Double, duration: Double): Double =
        (window.start + frac * (window.end - window.start)) * duration

    fun timeToFrac(window: Window, time: Double, duration: Double): Double {
        if (duration <= 0.0) return 0.0
        val span = window.end - window.start
        if (span <= 0.0) return 0.0
        return ((time / duration) - window.start) / span
    }
}
