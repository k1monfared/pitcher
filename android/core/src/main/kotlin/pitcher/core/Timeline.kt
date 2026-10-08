package pitcher.core

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

    fun sorted(loops: List<Loop>): List<Loop> = loops.sortedBy { it.startMs }

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
