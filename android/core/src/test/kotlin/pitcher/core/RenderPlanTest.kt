package pitcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RenderPlanTest {

    private fun record(id: Long, format: String, name: String, segments: String = "") =
        RenderPlan.Record(id = id, uri = "content://r/$id", format = format, fileName = name, segments = segments)

    @Test
    fun wholeTrackHasAnEmptySegmentsKey() {
        assertEquals("", RenderPlan.segmentsKey(null))
        assertEquals("", RenderPlan.segmentsKey(emptyList()))
    }

    @Test
    fun segmentsKeyIsStableAndOrdered() {
        val key = RenderPlan.segmentsKey(listOf(3000L to 4000L, 1000L to 2000L))
        assertEquals("1000-2000,3000-4000", key)
    }

    @Test
    fun reusesARenderWithTheSameNameFormatAndSegments() {
        val records = listOf(record(1, "m4a", "Song - +300"), record(2, "mp3", "Song - +300"))
        assertEquals(2L, RenderPlan.reusable(records, "mp3", "Song - +300", "")?.id)
    }

    @Test
    fun aChangedNameFormatOrLoopSetNeedsANewRender() {
        val records = listOf(record(1, "m4a", "Song - +300"))
        assertNull(RenderPlan.reusable(records, "wav", "Song - +300", ""))
        assertNull(RenderPlan.reusable(records, "m4a", "Song - key of E", ""))
        assertNull(RenderPlan.reusable(records, "m4a", "Song - +300", "1000-2000"))
    }

    @Test
    fun aDifferentSpeedNeedsANewRender() {
        val records = listOf(record(1, "m4a", "Song - +300"))
        assertNull(RenderPlan.reusable(records, "m4a", "Song - +300", "", speed = 0.8))
        val slow = listOf(record(2, "m4a", "Song - +300").copy(speed = 0.8))
        assertEquals(2L, RenderPlan.reusable(slow, "m4a", "Song - +300", "", speed = 0.8)?.id)
    }

    @Test
    fun nameMatchingIgnoresSurroundingSpaces() {
        val records = listOf(record(1, "m4a", "Song - +300"))
        assertEquals(1L, RenderPlan.reusable(records, "m4a", "  Song - +300 ", "")?.id)
    }

    @Test
    fun theNewestMatchingRenderWins() {
        val records = listOf(record(1, "m4a", "A"), record(5, "m4a", "A"))
        assertEquals(5L, RenderPlan.reusable(records, "m4a", "A", "")?.id)
    }
}
