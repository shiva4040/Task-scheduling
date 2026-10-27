package com.taskscheduling.app.voice

/**
 * Authoritative validator for English-only speech recognition and assistant interaction.
 *
 * Enforces:
 * 1. Rejection of Devanagari and non-Latin scripts (Hindi, etc.).
 * 2. Rejection of typical conversational Hindi / Hinglish phrases.
 * 3. Rejection of repetitive hallucinations or nonsensical transcripts.
 * 4. Standard polite English fallback message when speech cannot be processed as English.
 */
object EnglishSpeechValidator {

    const val REJECTION_MESSAGE: String = "Sorry, I can only understand English. Please try again in English."

    private val KNOWN_NON_ENGLISH_WORDS = setOf(
        "namaste", "namaskar", "mera", "meri", "apna", "apni", "badal", "badlo", "karo", "kardo",
        "kaise", "kaisa", "kya", "theek", "shukriya", "dhanyawad", "chalao", "kholo", "peeche",
        "aage", "wapas", "suno", "sunao", "bhai", "yaar", "kripya", "dijiye", "bolo", "batao",
        "kar", "do", "karna", "hai", "hain", "hoon", "nahi", "nahin", "mat", "yahan", "wahan",
        "accha", "achha", "bahut", "thoda", "zyada", "abhi", "baad", "mein", "par", "se", "ko"
    )

    /**
     * Returns true if the transcript contains non-English scripts, non-English vocabulary,
     * or unreliable repetitive hallucination.
     */
    fun isNonEnglishOrUnreliable(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true

        // 1. Check for Devanagari Unicode characters (U+0900 - U+097F)
        if (trimmed.any { it in '\u0900'..'\u097F' }) {
            return true
        }

        // 2. Check for other non-Latin scripts (Arabic, Cyrillic, Chinese, etc.)
        if (trimmed.any { it.code > 0x024F && !it.isWhitespace() && it !in ",.!?'\"-:;()[]" }) {
            return true
        }

        // 3. Tokenize words and check for predominant conversational Hindi/Hinglish vocabulary
        val tokens = trimmed.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
        if (tokens.isNotEmpty()) {
            val nonEnglishMatches = tokens.count { it in KNOWN_NON_ENGLISH_WORDS }
            // If any known non-English word accounts for 30%+ of the utterance or is a single word like "namaste"
            if (nonEnglishMatches > 0 && (nonEnglishMatches.toFloat() / tokens.size >= 0.3f || tokens.size <= 2)) {
                return true
            }
        }

        // 4. Repetitive hallucination check (e.g. single word repeated 4+ times)
        if (tokens.size >= 4 && tokens.distinct().size == 1) {
            return true
        }

        return false
    }
}
