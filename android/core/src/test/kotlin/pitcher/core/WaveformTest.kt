package pitcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformTest {

    @Test
    fun normalizesToPeakOne() {
        val samples = floatArrayOf(0f, 0.5f, 1f, -1f, 0.2f, 0f)
        val peaks = Waveform.computePeaks(samples, 3)
        assertEquals(3, peaks.size)
        assertEquals(1.0, peaks.max().toDouble(), 1e-6)
    }

    @Test
    fun emptyForZeroBuckets() {
        assertTrue(Waveform.computePeaks(floatArrayOf(1f, 2f), 0).isEmpty())
    }

    @Test
    fun emptyForNoSamples() {
        assertTrue(Waveform.computePeaks(FloatArray(0), 10).isEmpty())
    }

    @Test
    fun usesAbsoluteAmplitude() {
        val peaks = Waveform.computePeaks(floatArrayOf(-0.8f, -0.8f, -0.8f, -0.8f), 2)
        assertEquals(2, peaks.size)
        assertEquals(1.0, peaks[0].toDouble(), 1e-6)
    }

    @Test
    fun bucketsAtMostSampleCount() {
        val peaks = Waveform.computePeaks(floatArrayOf(0.1f, 0.2f), 100)
        assertTrue(peaks.size <= 2)
    }
}
