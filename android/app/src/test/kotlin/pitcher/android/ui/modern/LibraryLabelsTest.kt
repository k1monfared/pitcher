package pitcher.android.ui.modern

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryLabelsTest {

    @Test
    fun singularForOne() {
        assertEquals("1 song", songCountLabel(1))
    }

    @Test
    fun pluralForZero() {
        assertEquals("0 songs", songCountLabel(0))
    }

    @Test
    fun pluralForMany() {
        assertEquals("7 songs", songCountLabel(7))
    }
}
