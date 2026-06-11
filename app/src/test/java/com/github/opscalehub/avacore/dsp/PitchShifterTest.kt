package com.github.opscalehub.avacore.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class PitchShifterTest {

    private val sampleRate = 22050

    private fun sine(freq: Double, samples: Int): FloatArray =
        FloatArray(samples) { sin(2.0 * PI * freq * it / sampleRate).toFloat() }

    /** Estimate fundamental via positive-going zero crossings. */
    private fun estimateFreq(x: FloatArray): Double {
        var crossings = 0
        for (i in 1 until x.size) if (x[i - 1] <= 0f && x[i] > 0f) crossings++
        return crossings.toDouble() * sampleRate / x.size
    }

    @Test fun defaultFactor_returnsInputUntouched() {
        val x = sine(200.0, 8000)
        assertSame("factor 1.0 must be a no-op", x, PitchShifter.process(x, 1.0f))
    }

    @Test fun shortInput_returnsInputUntouched() {
        val x = sine(200.0, 100)
        assertSame(x, PitchShifter.process(x, 1.5f))
    }

    @Test fun pitchUp_preservesDuration() {
        val x = sine(200.0, 22050) // 1s
        val y = PitchShifter.process(x, 1.5f)
        // duration within 5%
        assertTrue("len ${y.size}", kotlin.math.abs(y.size - x.size) < x.size * 0.05)
    }

    @Test fun pitchUp_raisesFundamental() {
        val base = sine(200.0, 22050)
        val up = PitchShifter.process(base, 1.5f)
        val f = estimateFreq(up)
        // expect ~300 Hz; allow generous tolerance for the estimator
        assertTrue("got $f Hz", f in 255.0..345.0)
    }

    @Test fun pitchDown_lowersFundamental() {
        val base = sine(200.0, 22050)
        val down = PitchShifter.process(base, 0.75f)
        val f = estimateFreq(down)
        // expect ~150 Hz
        assertTrue("got $f Hz", f in 120.0..180.0)
    }

    @Test fun largeBuffer_nonTrivialFactor_doesNotOverflow() {
        // Regression: a ~63k-sample buffer with factor 0.87 previously drove the
        // resampler index one past the end via float accumulation drift.
        val x = sine(190.0, 63488)
        val y = PitchShifter.process(x, 0.87f)
        assertTrue(y.isNotEmpty())
        for (v in y) assertTrue(v.isFinite())
    }

    @Test fun output_isFinite() {
        val x = sine(180.0, 16000)
        for (v in PitchShifter.process(x, 1.3f)) {
            assertTrue("non-finite sample", v.isFinite())
        }
    }
}
