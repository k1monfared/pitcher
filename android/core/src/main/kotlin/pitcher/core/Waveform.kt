package pitcher.core

object Waveform {
    /**
     * Downsample interleaved-or-mono samples into [buckets] normalized peak
     * amplitudes in 0..1. Mirrors the web `computePeaks`.
     */
    fun computePeaks(samples: FloatArray, buckets: Int): FloatArray {
        if (buckets <= 0 || samples.isEmpty()) return FloatArray(0)
        val size = maxOf(1, samples.size / buckets)
        val count = minOf(buckets, (samples.size + size - 1) / size)
        val peaks = FloatArray(count)
        var globalMax = 1e-6f
        for (b in 0 until count) {
            var max = 0f
            val start = b * size
            val stop = minOf(start + size, samples.size)
            for (i in start until stop) {
                val v = kotlin.math.abs(samples[i])
                if (v > max) max = v
            }
            peaks[b] = max
            if (max > globalMax) globalMax = max
        }
        for (b in peaks.indices) peaks[b] = peaks[b] / globalMax
        return peaks
    }

    fun formatClock(seconds: Double): String {
        val total = seconds.toInt().coerceAtLeast(0)
        val m = total / 60
        val s = total % 60
        return "$m:" + s.toString().padStart(2, '0')
    }
}
