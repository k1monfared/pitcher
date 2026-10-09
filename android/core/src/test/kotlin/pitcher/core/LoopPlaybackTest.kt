package pitcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoopPlaybackTest {

    private val a = Timeline.Loop(id = 1, startMs = 1000, endMs = 2000)
    private val b = Timeline.Loop(id = 2, startMs = 3000, endMs = 4000)

    @Test
    fun noneNeverSeeks() {
        assertNull(LoopPlayback.seekTarget(listOf(a, b), LoopMode.NONE, 5000, null))
    }

    @Test
    fun emptyNeverSeeks() {
        assertNull(LoopPlayback.seekTarget(emptyList(), LoopMode.ALL, 5000, null))
    }

    @Test
    fun oneRepeatsTheSelectedLoop() {
        assertEquals(1000L, LoopPlayback.seekTarget(listOf(a, b), LoopMode.ONE, 2000, 1))
        assertEquals(3000L, LoopPlayback.seekTarget(listOf(a, b), LoopMode.ONE, 4001, 2))
        assertNull(LoopPlayback.seekTarget(listOf(a, b), LoopMode.ONE, 1500, 1))
    }

    @Test
    fun oneFallsBackToFirstWhenSelectionMissingOrDisabled() {
        assertEquals(1000L, LoopPlayback.seekTarget(listOf(a, b), LoopMode.ONE, 2000, 99))
        val disabledA = a.copy(enabled = false)
        assertEquals(3000L, LoopPlayback.seekTarget(listOf(disabledA, b), LoopMode.ONE, 5000, 1))
    }

    @Test
    fun allPlaysInsideALoopWithoutSeeking() {
        assertNull(LoopPlayback.seekTarget(listOf(a, b), LoopMode.ALL, 1500, null))
    }

    @Test
    fun allAdvancesToTheNextLoop() {
        assertEquals(3000L, LoopPlayback.seekTarget(listOf(a, b), LoopMode.ALL, 2000, null))
        assertEquals(3000L, LoopPlayback.seekTarget(listOf(a, b), LoopMode.ALL, 2500, null))
    }

    @Test
    fun allWrapsAfterTheLastLoop() {
        assertEquals(1000L, LoopPlayback.seekTarget(listOf(a, b), LoopMode.ALL, 4000, null))
        assertEquals(1000L, LoopPlayback.seekTarget(listOf(a, b), LoopMode.ALL, 9000, null))
    }

    @Test
    fun allJumpsIntoTheFirstLoopFromBeforeIt() {
        assertEquals(1000L, LoopPlayback.seekTarget(listOf(a, b), LoopMode.ALL, 0, null))
    }

    @Test
    fun aLoopEndingAtTheTrackEndRestartsWhenPlaybackEnds() {
        val tail = Timeline.Loop(id = 3, startMs = 8000, endMs = 10_000)
        assertEquals(8000L, LoopPlayback.seekTarget(listOf(tail), LoopMode.ONE, 10_000, 3))
        assertEquals(1000L, LoopPlayback.seekTarget(listOf(a, tail), LoopMode.ALL, 10_000, null))
    }

    @Test
    fun allIgnoresDisabledLoops() {
        val disabledB = b.copy(enabled = false)
        assertEquals(1000L, LoopPlayback.seekTarget(listOf(a, disabledB), LoopMode.ALL, 2000, null))
    }
}
