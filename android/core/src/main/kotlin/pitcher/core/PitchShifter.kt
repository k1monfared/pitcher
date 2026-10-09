package pitcher.core

import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Offline pitch shifter: resample by the pitch ratio, then time-stretch back
 * with WSOLA (waveform-similarity overlap-add) so duration is preserved.
 * Mirrors the desktop approach of shifting pitch while keeping tempo.
 *
 * [cancelled] is polled throughout the shift; when it returns true a
 * [CancellationException] is thrown so long renders can be aborted.
 */
object PitchShifter {

    private const val FRAME = 1024
    private const val SYNTH_HOP = FRAME / 2
    private const val SEARCH = FRAME / 4

    /**
     * Shifts by [cents] and plays back at [speed] (2.0 is twice as fast, same
     * pitch). Speed folds into the stretch: the resampled audio is stretched by
     * ratio / speed instead of ratio, so both cost one pass.
     */
    fun shift(
        samples: FloatArray,
        sampleRate: Int,
        cents: Int,
        speed: Double = 1.0,
        cancelled: () -> Boolean = { false },
    ): FloatArray {
        if (samples.isEmpty()) return FloatArray(0)
        val s = if (speed > 0.0) speed else 1.0
        if (cents == 0 && s == 1.0) return samples.copyOf()
        val ratio = Math.pow(2.0, cents / 1200.0)
        val resampled = if (cents == 0) samples else resample(samples, ratio, cancelled)
        return timeStretch(resampled, ratio / s, cancelled)
    }

    private fun resample(input: FloatArray, ratio: Double, cancelled: () -> Boolean): FloatArray {
        val outLen = max(1, (input.size / ratio).toInt())
        val out = FloatArray(outLen)
        val last = input.size - 1
        for (i in 0 until outLen) {
            if (i and 0xFFFF == 0 && cancelled()) throw CancellationException("cancelled")
            val src = i * ratio
            val i0 = src.toInt()
            val frac = (src - i0).toFloat()
            val a = input[i0.coerceIn(0, last)]
            val b = input[(i0 + 1).coerceIn(0, last)]
            out[i] = a + (b - a) * frac
        }
        return out
    }

    private fun timeStretch(input: FloatArray, stretch: Double, cancelled: () -> Boolean): FloatArray {
        val n = input.size
        if (n < FRAME) return input.copyOf()
        val analysisHop = max(1, (SYNTH_HOP / stretch).roundToInt())
        val overlap = FRAME - SYNTH_HOP
        val window = hann(FRAME)

        val numFrames = ((n - FRAME) / analysisHop) + 1
        val outLen = (numFrames - 1) * SYNTH_HOP + FRAME
        val output = FloatArray(outLen)

        for (j in 0 until FRAME) output[j] += input[j] * window[j]

        var outPos = 0
        for (k in 1 until numFrames) {
            if (cancelled()) throw CancellationException("cancelled")
            val target = k * analysisHop
            var bestD = 0
            var bestCorr = Double.NEGATIVE_INFINITY
            for (d in -SEARCH..SEARCH) {
                val aStart = target + d
                if (aStart < 0 || aStart + FRAME > n) continue
                var corr = 0.0
                for (j in 0 until overlap) {
                    corr += input[aStart + j].toDouble() * output[outPos + SYNTH_HOP + j]
                }
                if (corr > bestCorr) {
                    bestCorr = corr
                    bestD = d
                }
            }
            val aStart = (target + bestD).coerceIn(0, n - FRAME)
            val sStart = outPos + SYNTH_HOP
            for (j in 0 until FRAME) {
                output[sStart + j] += input[aStart + j] * window[j]
            }
            outPos = sStart
        }

        for (i in output.indices) output[i] = output[i].coerceIn(-1f, 1f)
        return output
    }

    private fun hann(size: Int): FloatArray {
        val w = FloatArray(size)
        for (i in 0 until size) {
            w[i] = (0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / size))).toFloat()
        }
        return w
    }

    @Suppress("unused")
    private fun rms(samples: FloatArray): Double {
        var s = 0.0
        for (x in samples) s += x.toDouble() * x
        return sqrt(s / samples.size)
    }
}
