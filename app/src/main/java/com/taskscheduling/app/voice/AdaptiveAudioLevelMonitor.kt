package com.taskscheduling.app.voice

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

data class AudioFrameMetrics(
    val rms: Float,
    val peak: Float,
    val minSample: Short,
    val maxSample: Short,
    val zeroPercent: Float,
    val noiseFloorRms: Float,
    val speechThreshold: Float,
    val speechRms: Float,
    val snrDb: Float,
    val isSpeechDetected: Boolean,
    val levelCategory: String // SILENCE, QUIET SPEECH, NORMAL SPEECH, LOUD SPEECH, CLIPPING
)

/**
 * Real-time adaptive audio level monitor and voice activity detector.
 * Continuously measures RMS, peak, extrema, zero percentage, and dynamic noise floor.
 * Adapts to quiet rooms, fan/AC noise, and normal room noise without using a hard-coded fixed threshold.
 */
class AdaptiveAudioLevelMonitor {

    var noiseFloorRms: Float = 0.002f
        private set
    var speechRms: Float = 0.020f
        private set

    private var consecutiveSpeechFrames = 0
    private var consecutiveSilenceFrames = 0
    private var isSpeechActive = false

    /**
     * Analyzes a raw PCM ShortArray chunk and its normalized FloatArray representation.
     */
    fun analyze(
        shortSamples: ShortArray,
        floatSamples: FloatArray,
        samplesCount: Int
    ): AudioFrameMetrics {
        if (samplesCount <= 0) {
            return AudioFrameMetrics(
                rms = 0f,
                peak = 0f,
                minSample = 0,
                maxSample = 0,
                zeroPercent = 0f,
                noiseFloorRms = noiseFloorRms,
                speechThreshold = noiseFloorRms * 1.8f,
                speechRms = speechRms,
                snrDb = 0f,
                isSpeechDetected = false,
                levelCategory = "SILENCE"
            )
        }

        var sumSquares = 0.0
        var peak = 0f
        var minVal = Short.MAX_VALUE
        var maxVal = Short.MIN_VALUE
        var zeroCount = 0

        for (i in 0 until samplesCount) {
            val s = shortSamples[i]
            if (s == 0.toShort()) zeroCount++
            if (s < minVal) minVal = s
            if (s > maxVal) maxVal = s

            val f = floatSamples[i]
            sumSquares += (f * f).toDouble()
            val absF = abs(f)
            if (absF > peak) peak = absF
        }

        val rms = sqrt(sumSquares / samplesCount).toFloat()
        val zeroPercent = (zeroCount * 100f) / samplesCount

        // Dynamically compute speech threshold based on the measured noise floor
        // Quiet rooms (floor ~0.001) -> threshold ~0.0025
        // Fan/AC noise (floor ~0.006) -> threshold ~0.0085
        val speechThreshold = max(noiseFloorRms * 1.4f, noiseFloorRms + 0.0015f)

        // Raw frame classification: Sensitive to quiet consonants and unstressed speech
        val isCandidateSpeech = (rms > speechThreshold && peak > 0.004f) || peak > 0.012f

        if (isCandidateSpeech) {
            consecutiveSpeechFrames++
            consecutiveSilenceFrames = 0
            // Fast onset: 1 frame if peak is clear, or 2 candidate frames
            if (consecutiveSpeechFrames >= 2 || peak > 0.012f) {
                isSpeechActive = true
            }
            speechRms = speechRms * 0.85f + rms * 0.15f
        } else {
            consecutiveSilenceFrames++
            consecutiveSpeechFrames = 0
            // Generous hangover: ~500ms (approx 8 chunks of 1024 samples @ 16kHz)
            // Preserves natural pauses between words (e.g. "hey, [pause] listen")
            if (consecutiveSilenceFrames >= 8) {
                isSpeechActive = false
            }
            // Adapt noise floor only during extended silence periods
            if (consecutiveSilenceFrames >= 4) {
                noiseFloorRms = noiseFloorRms * 0.97f + rms * 0.03f
                if (noiseFloorRms < 0.0005f) noiseFloorRms = 0.0005f
            }
        }

        val safeFloor = max(noiseFloorRms, 0.0001f)
        val currentSignal = if (isSpeechActive) max(rms, speechRms) else rms
        val snrDb = if (currentSignal > safeFloor) 20f * log10(currentSignal / safeFloor) else 0f

        // Classify level category: SILENCE, QUIET SPEECH, NORMAL SPEECH, LOUD SPEECH, CLIPPING
        val levelCategory = when {
            peak >= 0.95f || minVal <= -32767 || maxVal >= 32766 -> "CLIPPING"
            !isSpeechActive -> "SILENCE"
            rms > 0.15f -> "LOUD SPEECH"
            rms > speechThreshold * 1.8f || peak > 0.030f -> "NORMAL SPEECH"
            else -> "QUIET SPEECH"
        }

        return AudioFrameMetrics(
            rms = rms,
            peak = peak,
            minSample = minVal,
            maxSample = maxVal,
            zeroPercent = zeroPercent,
            noiseFloorRms = noiseFloorRms,
            speechThreshold = speechThreshold,
            speechRms = speechRms,
            snrDb = snrDb,
            isSpeechDetected = isSpeechActive,
            levelCategory = levelCategory
        )
    }

    fun reset() {
        consecutiveSpeechFrames = 0
        consecutiveSilenceFrames = 0
        isSpeechActive = false
        noiseFloorRms = 0.002f
        speechRms = 0.020f
    }
}
