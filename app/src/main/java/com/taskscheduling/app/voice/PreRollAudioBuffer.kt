package com.taskscheduling.app.voice

/**
 * Circular pre-roll audio buffer maintaining recent audio history (~200-250ms).
 * When speech onset is detected, the pre-roll window is prepended to the streaming ASR session,
 * guaranteeing that initial consonants, plosives, and quiet syllables (e.g., 'h' in "hello")
 * are never clipped or lost.
 */
class PreRollAudioBuffer(private val capacitySamples: Int = 4000) {

    private val ringBuffer = FloatArray(capacitySamples)
    private var writeIndex = 0
    private var totalWritten = 0L

    /**
     * Appends freshly captured audio samples into the circular buffer.
     */
    fun append(samples: FloatArray, count: Int = samples.size) {
        val toWrite = minOf(count, samples.size)
        for (i in 0 until toWrite) {
            ringBuffer[writeIndex] = samples[i]
            writeIndex = (writeIndex + 1) % capacitySamples
            totalWritten++
        }
    }

    /**
     * Extracts up to [samplesCount] of the most recent samples immediately preceding the current point.
     * Returns a chronological FloatArray.
     */
    fun getRecentSamples(samplesCount: Int = 3200): FloatArray {
        val available = minOf(totalWritten, capacitySamples.toLong()).toInt()
        val extractCount = minOf(samplesCount, available)
        if (extractCount <= 0) return FloatArray(0)

        val out = FloatArray(extractCount)
        var readIndex = (writeIndex - extractCount) % capacitySamples
        if (readIndex < 0) readIndex += capacitySamples

        for (i in 0 until extractCount) {
            out[i] = ringBuffer[readIndex]
            readIndex = (readIndex + 1) % capacitySamples
        }
        return out
    }

    fun clear() {
        writeIndex = 0
        totalWritten = 0L
    }
}
