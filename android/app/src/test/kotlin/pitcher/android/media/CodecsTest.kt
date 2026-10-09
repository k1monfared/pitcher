package pitcher.android.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodecsTest {

    private val vendorAac = Codecs.Candidate("c2.qti.aac.encoder", vendor = true)
    private val platformAac = Codecs.Candidate("c2.android.aac.encoder", vendor = false)

    @Test
    fun androidsOwnOpenSourceCodecWinsOverAVendorCodec() {
        assertEquals(platformAac.name, Codecs.choose(listOf(vendorAac, platformAac))?.name)
        assertEquals(platformAac.name, Codecs.choose(listOf(platformAac, vendorAac))?.name)
    }

    @Test
    fun aVendorCodecIsUsedOnlyWhenNothingElseExists() {
        assertEquals(vendorAac.name, Codecs.choose(listOf(vendorAac))?.name)
    }

    @Test
    fun noCandidatesMeansNoChoice() {
        assertNull(Codecs.choose(emptyList()))
    }

    @Test
    fun theListedOrderIsKeptWithinEachGroup() {
        val a = Codecs.Candidate("c2.android.opus.encoder", vendor = false)
        val b = Codecs.Candidate("OMX.google.opus.encoder", vendor = false)
        assertEquals(a.name, Codecs.choose(listOf(vendorAac, a, b))?.name)
    }
}
