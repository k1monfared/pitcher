package pitcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTest {

    private fun loop(id: Long, start: Long, end: Long, enabled: Boolean = true) =
        Timeline.Loop(id, start, end, enabled)

    @Test
    fun sortedByStart() {
        val list = listOf(loop(2, 5000, 6000), loop(1, 1000, 2000))
        assertEquals(listOf(1L, 2L), Timeline.sorted(list).map { it.id })
    }

    @Test
    fun activeKeepsOnlyEnabled() {
        val list = listOf(loop(1, 0, 1000), loop(2, 2000, 3000, enabled = false))
        assertEquals(listOf(1L), Timeline.active(list).map { it.id })
    }

    @Test
    fun addRejectsOverlapAndTooShort() {
        val list = listOf(loop(1, 0, 1000))
        assertFalse(Timeline.canAdd(list, 500, 900))
        assertFalse(Timeline.canAdd(list, 2000, 2050))
        assertTrue(Timeline.canAdd(list, 1500, 2500))
    }

    @Test
    fun addInsertsInOrder() {
        var list = Timeline.add(emptyList(), id = 1, startMs = 3000, endMs = 4000)
        list = Timeline.add(list, id = 2, startMs = 1000, endMs = 2000)
        assertEquals(listOf(2L, 1L), list.map { it.id })
        assertEquals(2, list.size)
    }

    @Test
    fun addIgnoresOverlappingRequest() {
        val list = Timeline.add(emptyList(), id = 1, startMs = 0, endMs = 1000)
        val after = Timeline.add(list, id = 2, startMs = 800, endMs = 1500)
        assertEquals(1, after.size)
    }

    @Test
    fun movingStartClampsToPreviousEnd() {
        val list = listOf(loop(1, 0, 1000), loop(2, 2000, 3000))
        val moved = Timeline.moveEdge(list, id = 2, isStart = true, valueMs = 500, durationMs = 10_000)
        assertEquals(1000L, moved.first { it.id == 2L }.startMs)
    }

    @Test
    fun movingEndClampsToNextStart() {
        val list = listOf(loop(1, 0, 1000), loop(2, 2000, 3000))
        val moved = Timeline.moveEdge(list, id = 1, isStart = false, valueMs = 5000, durationMs = 10_000)
        assertEquals(2000L, moved.first { it.id == 1L }.endMs)
    }

    @Test
    fun movingEdgesKeepsMinimumLength() {
        val list = listOf(loop(1, 1000, 2000))
        val startTooFar = Timeline.moveEdge(list, id = 1, isStart = true, valueMs = 3000, durationMs = 10_000)
        assertTrue(startTooFar.first().endMs - startTooFar.first().startMs >= Timeline.MIN_LOOP_MS)
        val endTooFar = Timeline.moveEdge(list, id = 1, isStart = false, valueMs = 0, durationMs = 10_000)
        assertTrue(endTooFar.first().endMs - endTooFar.first().startMs >= Timeline.MIN_LOOP_MS)
    }

    @Test
    fun edgeAtFindsTheNearestEdgeWithinTolerance() {
        val list = listOf(loop(1, 10_000, 20_000), loop(2, 30_000, 40_000))
        assertEquals(Timeline.Edge(1, isStart = false), Timeline.edgeAt(list, 21_000, toleranceMs = 4_000))
        assertEquals(Timeline.Edge(2, isStart = true), Timeline.edgeAt(list, 28_500, toleranceMs = 4_000))
        assertEquals(null, Timeline.edgeAt(list, 25_000, toleranceMs = 4_000))
    }

    @Test
    fun edgeAtWorksAtFullZoomOnALongTrack() {
        // A 3 minute track on a 1080 px chart is about 167 ms per pixel, so a
        // 24 px touch radius is about 4 s. A touch 1 s from the edge must hit.
        val msPerPx = 180_000.0 / 1080.0
        val tolerance = (24 * msPerPx).toLong()
        val list = listOf(loop(1, 60_000, 90_000))
        assertEquals(Timeline.Edge(1, isStart = true), Timeline.edgeAt(list, 61_000, tolerance))
    }

    @Test
    fun edgeAtPrefersTheCloserEdgeOfATinyLoop() {
        val list = listOf(loop(1, 10_000, 10_200))
        assertEquals(Timeline.Edge(1, isStart = true), Timeline.edgeAt(list, 9_900, toleranceMs = 4_000))
        assertEquals(Timeline.Edge(1, isStart = false), Timeline.edgeAt(list, 10_300, toleranceMs = 4_000))
    }

    @Test
    fun loopAtReturnsTheContainingLoop() {
        val list = listOf(loop(1, 0, 1000), loop(2, 2000, 3000))
        assertEquals(2L, Timeline.loopAt(list, 2500)?.id)
        assertEquals(null, Timeline.loopAt(list, 1500))
    }

    @Test
    fun nearestIndexHonoursTolerance() {
        val times = listOf(1_000L, 5_000L, 9_000L)
        assertEquals(1, Timeline.nearestIndex(times, 5_600, toleranceMs = 1_000))
        assertEquals(null, Timeline.nearestIndex(times, 7_000, toleranceMs = 1_000))
        assertEquals(null, Timeline.nearestIndex(emptyList<Long>(), 7_000, toleranceMs = 1_000))
    }

    @Test
    fun movingFirstStartAllowsZeroAndLastEndAllowsDuration() {
        val list = listOf(loop(1, 500, 1000), loop(2, 2000, 2500))
        val first = Timeline.moveEdge(list, id = 1, isStart = true, valueMs = -100, durationMs = 10_000)
        assertEquals(0L, first.first().startMs)
        val last = Timeline.moveEdge(list, id = 2, isStart = false, valueMs = 99_999, durationMs = 10_000)
        assertEquals(10_000L, last.last().endMs)
    }
}
