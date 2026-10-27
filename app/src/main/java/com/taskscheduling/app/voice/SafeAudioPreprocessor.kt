package com.taskscheduling.app.voice

/**
 * Gentle, safe audio preprocessor that eliminates microphone DC bias and sub-15Hz mechanical rumble
 * while preserving 100% of human speech frequencies (80Hz - 8000Hz).
 *
 * Uses a single-pole DC-blocking IIR high-pass filter:
 *   y[n] = x[n] - x[n-1] + R * y[n-1]
 * with R = 0.995 (cutoff ~12.7Hz at 16kHz).
 *
 * Avoids any aggressive spectral subtraction, compression, or AGC that could distort
 * acoustic speech phonemes and formants before Sherpa-ONNX streaming Zipformer2 ASR.
 */
class SafeAudioPreprocessor(private val pole: Float = 0.995f) {

    private var prevX = 0f
    private var prevY = 0f

    /**
     * Filters float samples in-place or returns a clean float array in range [-1.0, 1.0].
     */
    fun process(samples: FloatArray): FloatArray {
        val out = FloatArray(samples.size)
        var px = prevX
        var py = prevY

        for (i in samples.indices) {
            val x = samples[i]
            val y = x - px + pole * py
            px = x
            py = y

            // Gentle soft-limiting for any extreme peaks to prevent numerical overflow
            out[i] = when {
                y > 1.0f -> 1.0f
                y < -1.0f -> -1.0f
                else -> y
            }
        }

        prevX = px
        prevY = py
        return out
    }

    /**
     * Resets filter state between separate speech utterances.
     */
    fun reset() {
        prevX = 0f
        prevY = 0f
    }
}
