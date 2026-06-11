package com.github.opscalehub.avacore.dsp

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Duration-preserving pitch shifter for 16 kHz–24 kHz speech.
 *
 * VITS/Sherpa exposes no pitch parameter, so to honor the Android system pitch
 * setting we post-process the synthesized waveform. A naive resample would shift
 * formants and sound robotic; instead we use SOLA (Synchronized Overlap-Add)
 * time-stretching followed by resampling, which keeps duration constant while
 * moving the perceived pitch and is far cleaner on speech.
 *
 *   pitch up  (factor > 1): time-stretch longer, then resample faster
 *   pitch down (factor < 1): time-compress, then resample slower
 *
 * The default path (factor ≈ 1.0) returns the input untouched, so normal
 * playback is bit-for-bit unchanged and incurs zero cost.
 *
 * Pure Kotlin, no dependencies — unit-testable on the JVM.
 */
object PitchShifter {

    private const val FRAME = 1024
    private const val SYNTH_HOP = FRAME / 2          // 512, => overlap = 512
    private const val SEARCH = 256                   // ± similarity search range
    private const val CORR_STRIDE = 8                // subsample correlation for speed
    private const val MIN_FACTOR = 0.5f
    private const val MAX_FACTOR = 2.0f
    private const val DEADBAND = 0.01f

    /** Shift pitch by [pitchFactor] (1.0 = unchanged) while preserving duration. */
    fun process(input: FloatArray, pitchFactor: Float): FloatArray {
        if (input.size < FRAME * 2 || abs(pitchFactor - 1f) < DEADBAND) return input
        val factor = pitchFactor.coerceIn(MIN_FACTOR, MAX_FACTOR)
        val stretched = solaTimeStretch(input, factor)
        return resampleByStep(stretched, factor)
    }

    /** SOLA time-stretch: output length ≈ x.size * alpha, pitch unchanged. */
    private fun solaTimeStretch(x: FloatArray, alpha: Float): FloatArray {
        val analysisHop = (SYNTH_HOP / alpha).roundToInt().coerceAtLeast(1)
        val overlap = FRAME - SYNTH_HOP
        val out = FloatArray((x.size * alpha).toInt() + FRAME)

        System.arraycopy(x, 0, out, 0, minOf(FRAME, x.size))
        var outPos = SYNTH_HOP
        var inBase = analysisHop

        while (inBase + FRAME + SEARCH < x.size && outPos + FRAME < out.size) {
            // Find the read offset whose overlap region best matches what we have
            // already written, so the overlap-add stays phase-aligned (no warble).
            var bestK = 0
            var bestCorr = -Float.MAX_VALUE
            val kStart = maxOf(-SEARCH, -inBase)
            for (k in kStart..SEARCH) {
                var corr = 0f
                var j = 0
                while (j < overlap) {
                    corr += out[outPos + j] * x[inBase + k + j]
                    j += CORR_STRIDE
                }
                if (corr > bestCorr) {
                    bestCorr = corr
                    bestK = k
                }
            }
            val readPos = inBase + bestK

            // Linear cross-fade over the overlap, then copy the frame's tail.
            var j = 0
            while (j < overlap) {
                val w = j.toFloat() / overlap
                out[outPos + j] = out[outPos + j] * (1f - w) + x[readPos + j] * w
                j++
            }
            var t = overlap
            while (t < FRAME && outPos + t < out.size && readPos + t < x.size) {
                out[outPos + t] = x[readPos + t]
                t++
            }

            outPos += SYNTH_HOP
            inBase += analysisHop
        }
        return out.copyOf(minOf(outPos + overlap, out.size))
    }

    /** Linear-interpolating resample reading [step] input samples per output sample. */
    private fun resampleByStep(x: FloatArray, step: Float): FloatArray {
        if (x.isEmpty()) return x
        val outLen = (x.size / step).toInt().coerceAtLeast(1)
        val out = FloatArray(outLen)
        val last = x.size - 1
        for (i in 0 until outLen) {
            // Compute position directly (not accumulated) in double precision so
            // rounding cannot drift the index past the end over long buffers.
            val pos = i * step.toDouble()
            val idx = pos.toInt()
            if (idx >= last) {
                out[i] = x[last]
            } else {
                val frac = (pos - idx).toFloat()
                out[i] = x[idx] + (x[idx + 1] - x[idx]) * frac
            }
        }
        return out
    }
}
