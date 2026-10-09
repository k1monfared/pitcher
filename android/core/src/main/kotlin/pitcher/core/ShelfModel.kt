package pitcher.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The pitch shelf as a list of pills, plus the geometry of the format knob.
 *
 * There is one pill per pitch: the original, each kept pitch, and at most one
 * new pill for the current pitch while no kept pitch is at that value.
 */
object ShelfModel {

    enum class Kind { ORIGINAL, KEPT, NEW }

    data class Kept(val id: Long, val cents: Int)

    data class Pill(val kind: Kind, val cents: Int, val id: Long?, val selected: Boolean)

    fun pills(kept: List<Kept>, currentCents: Int): List<Pill> {
        val unique = kept.filter { it.cents != 0 }.groupBy { it.cents }.map { (_, same) -> same.minBy { it.id } }
        val out = ArrayList<Pill>(unique.size + 2)
        out.add(Pill(Kind.ORIGINAL, 0, null, currentCents == 0))
        unique.forEach { out.add(Pill(Kind.KEPT, it.cents, it.id, it.cents == currentCents)) }
        if (currentCents != 0 && unique.none { it.cents == currentCents }) {
            out.add(Pill(Kind.NEW, currentCents, null, true))
        }
        return out.sortedBy { it.cents }
    }

    data class DrumSlot(val y: Float, val scale: Float, val visible: Boolean)

    /**
     * Where item [index] sits on a drum of [radius] px rolled to [scroll] (the
     * item index at the middle, fractional while moving). Items are
     * [stepDegrees] apart on the drum; past 90 degrees they are out of sight.
     */
    fun drumSlot(index: Int, scroll: Float, stepDegrees: Float, radius: Float): DrumSlot {
        val degrees = (index - scroll) * stepDegrees
        if (abs(degrees) >= 90f) return DrumSlot(0f, 0f, false)
        val rad = Math.toRadians(degrees.toDouble())
        return DrumSlot((radius * sin(rad)).toFloat(), cos(rad).toFloat(), true)
    }

    /** The drum position after the finger moves [dyPx]; [stepPx] is the travel per item. */
    fun drumScroll(startScroll: Float, dyPx: Float, stepPx: Float): Float =
        if (stepPx <= 0f) startScroll else startScroll - dyPx / stepPx

    fun drumNearest(scroll: Float, count: Int): Int =
        scroll.roundToInt().coerceIn(0, (count - 1).coerceAtLeast(0))
}
