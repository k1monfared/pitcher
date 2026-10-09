package pitcher.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ChartLabelsTest {

    @Test
    fun sideLabelSitsRightOfTheLineWhenItFits() {
        assertEquals(104f, ChartLabels.sideLabelX(anchorX = 100f, labelWidth = 40f, viewWidth = 400f, gap = 4f))
    }

    @Test
    fun sideLabelFlipsLeftNearTheRightEdge() {
        assertEquals(336f, ChartLabels.sideLabelX(anchorX = 380f, labelWidth = 40f, viewWidth = 400f, gap = 4f))
    }

    @Test
    fun sideLabelNeverLeavesTheView() {
        assertEquals(0f, ChartLabels.sideLabelX(anchorX = 20f, labelWidth = 500f, viewWidth = 400f, gap = 4f))
    }

    @Test
    fun fitShowsTheFullTextWhenThereIsRoom() {
        assertEquals(ChartLabels.Fit.FULL, ChartLabels.fit(available = 80f, fullWidth = 60f, minimalWidth = 14f))
    }

    @Test
    fun fitEllipsizesWhenOneLetterAndEllipsisFit() {
        assertEquals(ChartLabels.Fit.ELLIPSIZED, ChartLabels.fit(available = 30f, fullWidth = 60f, minimalWidth = 14f))
        assertEquals(ChartLabels.Fit.ELLIPSIZED, ChartLabels.fit(available = 14f, fullWidth = 60f, minimalWidth = 14f))
    }

    @Test
    fun fitHidesTheTextWhenNotEvenOneLetterFits() {
        assertEquals(ChartLabels.Fit.NONE, ChartLabels.fit(available = 10f, fullWidth = 60f, minimalWidth = 14f))
    }

    @Test
    fun slideSelectFollowsTheFingerInSteps() {
        assertEquals(2, ChartLabels.slideIndex(startIndex = 2, deltaPx = 10f, stepPx = 40f, count = 4))
        assertEquals(3, ChartLabels.slideIndex(startIndex = 2, deltaPx = 30f, stepPx = 40f, count = 4))
        assertEquals(0, ChartLabels.slideIndex(startIndex = 2, deltaPx = -100f, stepPx = 40f, count = 4))
        assertEquals(3, ChartLabels.slideIndex(startIndex = 2, deltaPx = 500f, stepPx = 40f, count = 4))
    }
}
