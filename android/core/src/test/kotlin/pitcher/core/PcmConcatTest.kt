package pitcher.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PcmConcatTest {

    @Test
    fun concatenatesSegmentsInOrder() {
        val a = arrayOf(floatArrayOf(1f, 2f), floatArrayOf(10f, 20f))
        val b = arrayOf(floatArrayOf(3f), floatArrayOf(30f))
        val out = PcmConcat.concat(listOf(a, b))
        assertArrayEquals(floatArrayOf(1f, 2f, 3f), out[0], 0f)
        assertArrayEquals(floatArrayOf(10f, 20f, 30f), out[1], 0f)
    }

    @Test
    fun singleSegmentIsCopied() {
        val a = arrayOf(floatArrayOf(1f, 2f, 3f))
        val out = PcmConcat.concat(listOf(a))
        assertArrayEquals(a[0], out[0], 0f)
    }

    @Test
    fun emptyListYieldsNoChannels() {
        assertEquals(0, PcmConcat.concat(emptyList()).size)
    }

    @Test
    fun toleratesMissingChannels() {
        val stereo = arrayOf(floatArrayOf(1f, 1f), floatArrayOf(2f, 2f))
        val mono = arrayOf(floatArrayOf(9f))
        val out = PcmConcat.concat(listOf(stereo, mono))
        assertArrayEquals(floatArrayOf(1f, 1f, 9f), out[0], 0f)
        assertArrayEquals(floatArrayOf(2f, 2f, 0f), out[1], 0f)
    }

    @Test
    fun sumsTotalLength() {
        val seg = { n: Int -> arrayOf(FloatArray(n)) }
        val out = PcmConcat.concat(listOf(seg(3), seg(4), seg(5)))
        assertEquals(12, out[0].size)
    }
}
