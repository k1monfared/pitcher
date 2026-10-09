package pitcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfModelTest {

    private val kept = listOf(ShelfModel.Kept(id = 7, cents = 300), ShelfModel.Kept(id = 3, cents = -200))

    @Test
    fun originalAndKeptPitchesAreSortedByCents() {
        val pills = ShelfModel.pills(kept, currentCents = 0)
        assertEquals(listOf(-200, 0, 300), pills.map { it.cents })
        assertEquals(ShelfModel.Kind.ORIGINAL, pills[1].kind)
        assertTrue(pills[1].selected)
    }

    @Test
    fun anUnkeptPitchAddsOneNewPillInItsPlace() {
        val pills = ShelfModel.pills(kept, currentCents = 150)
        assertEquals(listOf(-200, 0, 150, 300), pills.map { it.cents })
        val fresh = pills.single { it.kind == ShelfModel.Kind.NEW }
        assertEquals(150, fresh.cents)
        assertTrue(fresh.selected)
        assertEquals(1, pills.count { it.selected })
    }

    @Test
    fun reachingAKeptPitchHighlightsItAndDropsTheNewPill() {
        val pills = ShelfModel.pills(kept, currentCents = 300)
        assertTrue(pills.none { it.kind == ShelfModel.Kind.NEW })
        val selected = pills.single { it.selected }
        assertEquals(7L, selected.id)
    }

    @Test
    fun duplicateCentsShowOnce() {
        val twice = kept + ShelfModel.Kept(id = 9, cents = 300)
        val pills = ShelfModel.pills(twice, currentCents = 300)
        assertEquals(1, pills.count { it.cents == 300 })
        assertEquals(7L, pills.single { it.cents == 300 }.id)
    }

    @Test
    fun drumPutsTheChosenItemInTheMiddleAtFullSize() {
        val p = ShelfModel.drumSlot(index = 2, scroll = 2f, stepDegrees = 25f, radius = 60f)
        assertEquals(0f, p.y, 1e-4f)
        assertEquals(1f, p.scale, 1e-4f)
        assertTrue(p.visible)
    }

    @Test
    fun drumCurvesNeighboursAwayAndShrinksThem() {
        val above = ShelfModel.drumSlot(index = 1, scroll = 2f, stepDegrees = 25f, radius = 60f)
        val below = ShelfModel.drumSlot(index = 3, scroll = 2f, stepDegrees = 25f, radius = 60f)
        assertTrue(above.y < 0f && below.y > 0f)
        assertEquals(-below.y, above.y, 1e-4f)
        assertTrue(above.scale < 1f)
        val far = ShelfModel.drumSlot(index = 0, scroll = 4f, stepDegrees = 25f, radius = 60f)
        assertTrue(!far.visible)
    }

    @Test
    fun draggingTheDrumMovesItsSurfaceWithTheFinger() {
        // Finger up rolls the surface up, bringing the next item to the middle.
        assertEquals(3f, ShelfModel.drumScroll(startScroll = 2f, dyPx = -26.18f, stepPx = 26.18f), 1e-3f)
        assertEquals(1f, ShelfModel.drumScroll(startScroll = 2f, dyPx = 26.18f, stepPx = 26.18f), 1e-3f)
        assertEquals(2, ShelfModel.drumNearest(scroll = 2.4f, count = 4))
        assertEquals(3, ShelfModel.drumNearest(scroll = 9f, count = 4))
        assertEquals(0, ShelfModel.drumNearest(scroll = -2f, count = 4))
    }
}
