package pitcher.core

import kotlin.math.abs

/**
 * Pure timeline rules for loop sections: they stay sorted by start and never
 * overlap. Edges are clamped against their neighbours and a minimum length.
 */
object Timeline {

    const val MIN_LOOP_MS = 100L

    data class Loop(
        val id: Long,
        val startMs: Long,
        val endMs: Long,
        val enabled: Boolean = true,
    )

    data class Edge(val loopId: Long, val isStart: Boolean)

    fun sorted(loops: List<Loop>): List<Loop> = loops.sortedBy { it.startMs }

    /**
     * The loop edge nearest to [touchMs], if it is within [toleranceMs]. Callers
     * convert their touch radius from pixels to milliseconds at the current zoom.
     */
    fun edgeAt(loops: List<Loop>, touchMs: Long, toleranceMs: Long): Edge? {
        var best: Edge? = null
        var bestDist = Long.MAX_VALUE
        for (loop in loops) {
            val toStart = abs(loop.startMs - touchMs)
            val toEnd = abs(loop.endMs - touchMs)
            if (toStart < bestDist) {
                bestDist = toStart
                best = Edge(loop.id, isStart = true)
            }
            if (toEnd < bestDist) {
                bestDist = toEnd
                best = Edge(loop.id, isStart = false)
            }
        }
        return best?.takeIf { bestDist <= toleranceMs }
    }

    fun loopAt(loops: List<Loop>, ms: Long): Loop? =
        loops.firstOrNull { ms >= it.startMs && ms < it.endMs }

    /** Index of the time nearest to [touchMs] within [toleranceMs], or null. */
    fun nearestIndex(timesMs: List<Long>, touchMs: Long, toleranceMs: Long): Int? {
        val idx = timesMs.indices.minByOrNull { abs(timesMs[it] - touchMs) } ?: return null
        return idx.takeIf { abs(timesMs[it] - touchMs) <= toleranceMs }
    }

    fun active(loops: List<Loop>): List<Loop> = sorted(loops).filter { it.enabled }

    private fun overlaps(startMs: Long, endMs: Long, other: Loop): Boolean =
        startMs < other.endMs && other.startMs < endMs

    fun canAdd(loops: List<Loop>, startMs: Long, endMs: Long): Boolean {
        val s = minOf(startMs, endMs)
        val e = maxOf(startMs, endMs)
        if (e - s < MIN_LOOP_MS) return false
        return loops.none { overlaps(s, e, it) }
    }

    fun add(loops: List<Loop>, id: Long, startMs: Long, endMs: Long): List<Loop> {
        val s = minOf(startMs, endMs)
        val e = maxOf(startMs, endMs)
        if (e - s < MIN_LOOP_MS || loops.any { overlaps(s, e, it) }) return sorted(loops)
        return sorted(loops + Loop(id, s, e))
    }

    fun moveEdge(
        loops: List<Loop>,
        id: Long,
        isStart: Boolean,
        valueMs: Long,
        durationMs: Long,
    ): List<Loop> {
        val list = sorted(loops)
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return list
        val loop = list[idx]
        val updated = if (isStart) {
            val lower = if (idx == 0) 0L else list[idx - 1].endMs
            val upper = loop.endMs - MIN_LOOP_MS
            loop.copy(startMs = valueMs.coerceIn(lower, maxOf(lower, upper)))
        } else {
            val lower = loop.startMs + MIN_LOOP_MS
            val upper = if (idx == list.lastIndex && durationMs <= 0) {
                Long.MAX_VALUE
            } else if (idx == list.lastIndex) {
                durationMs
            } else {
                list[idx + 1].startMs
            }
            loop.copy(endMs = valueMs.coerceIn(minOf(lower, upper), upper))
        }
        val mutable = list.toMutableList()
        mutable[idx] = updated
        return sorted(mutable)
    }
}
