package pitcher.core

/** The master loop behavior: off (whole song), all enabled loops, or one loop. */
enum class LoopMode { NONE, ALL, ONE }

/**
 * Decides when the playhead should jump for looping. Used by the playback tick.
 * A null result means keep playing.
 */
object LoopPlayback {

    fun seekTarget(
        loops: List<Timeline.Loop>,
        mode: LoopMode,
        positionMs: Long,
        selectedId: Long?,
    ): Long? {
        val active = Timeline.active(loops)
        if (mode == LoopMode.NONE || active.isEmpty()) return null

        if (mode == LoopMode.ONE) {
            val target = active.firstOrNull { it.id == selectedId } ?: active.first()
            return if (positionMs >= target.endMs || positionMs < target.startMs) {
                target.startMs
            } else {
                null
            }
        }

        val idx = active.indexOfLast { it.startMs <= positionMs }
        if (idx < 0) return active.first().startMs
        val current = active[idx]
        if (positionMs >= current.endMs) {
            return (active.getOrNull(idx + 1) ?: active.first()).startMs
        }
        return null
    }
}
