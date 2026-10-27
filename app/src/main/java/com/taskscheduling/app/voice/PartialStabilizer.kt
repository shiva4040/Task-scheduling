package com.taskscheduling.app.voice

/**
 * Stabilizes streaming partial recognition hypotheses across consecutive frames.
 * Maintains previousPartial, currentPartial, and stablePartial to eliminate transient
 * acoustic flicker while retaining fast, responsive word emergence during live human speech.
 */
class PartialStabilizer {

    var previousPartial: String = ""
        private set
    var currentPartial: String = ""
        private set
    var stablePartial: String = ""
        private set

    private var stabilityStreak = 0

    /**
     * Updates with the latest raw partial result from the recognizer.
     * Returns true if [stablePartial] changed.
     */
    fun update(rawHypothesis: String): Boolean {
        val trimmed = rawHypothesis.trim()
        previousPartial = currentPartial
        currentPartial = trimmed

        if (trimmed.isEmpty()) {
            if (stablePartial.isNotEmpty()) {
                stabilityStreak++
                if (stabilityStreak >= 4) {
                    stablePartial = ""
                    stabilityStreak = 0
                    return true
                }
            }
            return false
        }

        if (trimmed == previousPartial) {
            stabilityStreak++
        } else {
            stabilityStreak = 1
        }

        val isExtension = trimmed.length > stablePartial.length && trimmed.startsWith(stablePartial, ignoreCase = true)
        val isConsistent = stabilityStreak >= 2 && trimmed != stablePartial
        val isInitial = stablePartial.isEmpty()

        if (isInitial || isExtension || isConsistent) {
            val changed = stablePartial != trimmed
            stablePartial = trimmed
            return changed
        }

        return false
    }

    fun reset() {
        previousPartial = ""
        currentPartial = ""
        stablePartial = ""
        stabilityStreak = 0
    }
}
