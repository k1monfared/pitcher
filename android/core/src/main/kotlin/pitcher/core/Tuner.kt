package pitcher.core

import kotlin.math.abs

data class PitchReading(
    val hz: Double,
    val midi: Double,
    val note: NoteReading,
    val confidence: Double,
)

/**
 * Monophonic YIN pitch detector, a direct port of the Rust `tuner.rs` so the
 * Android and desktop apps agree. Silence-gated and range-limited.
 */
object Tuner {

    private const val YIN_THRESHOLD = 0.15
    private const val SILENCE_RMS = 1e-3
    private const val MIN_HZ = 40.0
    private const val MAX_HZ = 2100.0
    private const val WINDOW = 2048

    fun detect(samples: FloatArray, sampleRate: Int): PitchReading? {
        val sr = sampleRate.toDouble()
        val n = samples.size
        if (n < 64) return null

        var sumSq = 0.0
        for (s in samples) sumSq += s.toDouble() * s.toDouble()
        val rms = Math.sqrt(sumSq / n)
        if (rms < SILENCE_RMS) return null

        val maxTau = minOf((sr / MIN_HZ).toInt(), n / 2)
        val minTau = Math.ceil(sr / MAX_HZ).toInt()
        if (maxTau <= minTau + 2) return null

        val window = minOf(n, WINDOW)
        val buf = samples

        val d = DoubleArray(maxTau + 1)
        for (tau in 1..maxTau) {
            var sum = 0.0
            val limit = window - tau
            for (j in 0 until limit) {
                val diff = buf[j].toDouble() - buf[j + tau].toDouble()
                sum += diff * diff
            }
            d[tau] = sum
        }

        val cmnd = DoubleArray(maxTau + 1) { 1.0 }
        var running = 0.0
        for (tau in 1..maxTau) {
            running += d[tau]
            cmnd[tau] = if (running > 0.0) d[tau] * tau / running else 1.0
        }

        var tauEst = 0
        var tau = minTau
        while (tau < maxTau) {
            if (cmnd[tau] < YIN_THRESHOLD) {
                while (tau + 1 < maxTau && cmnd[tau + 1] < cmnd[tau]) tau++
                tauEst = tau
                break
            }
            tau++
        }
        if (tauEst == 0) return null

        val better = parabolic(cmnd, tauEst)
        val hz = sr / better
        if (hz < MIN_HZ || hz > MAX_HZ) return null
        val confidence = (1.0 - cmnd[tauEst]).coerceIn(0.0, 1.0)

        return PitchReading(
            hz = hz,
            midi = Notes.hzToMidi(hz),
            note = Notes.hzToNote(hz),
            confidence = confidence,
        )
    }

    private fun parabolic(cmnd: DoubleArray, tau: Int): Double {
        if (tau == 0 || tau + 1 >= cmnd.size) return tau.toDouble()
        val x0 = cmnd[tau - 1]
        val x1 = cmnd[tau]
        val x2 = cmnd[tau + 1]
        val denom = 2.0 * (2.0 * x1 - x2 - x0)
        if (abs(denom) < 1e-12) return tau.toDouble()
        val delta = (x2 - x0) / denom
        return tau + delta.coerceIn(-1.0, 1.0)
    }
}
