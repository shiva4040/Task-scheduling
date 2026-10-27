package com.taskscheduling.app.assistant

/**
 * Explicit user confirmation intent for authorizing workflow execution.
 */
enum class ConfirmationIntent {
    YES,
    NO,
    UNKNOWN
}

/**
 * Interface for resolving affirmative or negative confirmation from user speech or text.
 */
interface ConfirmationResolver {
    fun resolve(text: String): ConfirmationIntent
}

/**
 * Deterministic local confirmation resolver.
 * Rejects ambiguous input and only recognizes explicit affirmative/negative statements.
 */
class LocalConfirmationResolver : ConfirmationResolver {

    override fun resolve(text: String): ConfirmationIntent {
        val normalized = LocalCommandResolver.normalize(text)
        return when (normalized) {
            "yes",
            "yeah",
            "yep",
            "yup",
            "yes please",
            "run it",
            "yeah run it",
            "yes run it",
            "yes yes",
            "yes yes yes",
            "yeah yeah",
            "start",
            "confirm",
            "confirm it",
            "yes run",
            "run",
            "do it",
            "yes do it",
            "sure",
            "please run",
            "okay",
            "ok",
            "ok run it",
            "okay run it",
            "please do it",
            "do it please",
            "proceed" -> ConfirmationIntent.YES

            "no",
            "nope",
            "no thanks",
            "no thank you",
            "cancel",
            "cancel that",
            "don t do it",
            "dont do it",
            "don t",
            "dont",
            "don t run",
            "dont run",
            "don t run it",
            "dont run it",
            "stop",
            "never mind",
            "nevermind",
            "dismiss",
            "abort" -> ConfirmationIntent.NO

            else -> ConfirmationIntent.UNKNOWN
        }
    }
}

/**
 * Resolver for detecting explicit "Stop listening" voice commands and its variants.
 * When matched, continuous voice listening is terminated and assistant returns to IDLE.
 */
object StopListeningResolver {
    fun isStopListening(text: String): Boolean {
        val normalized = LocalCommandResolver.normalize(text)
        return when (normalized) {
            "stop listening",
            "stop listening now",
            "stop assistant",
            "stop",
            "turn off listening",
            "stop voice assistant",
            "stop the assistant",
            "turn off the assistant",
            "deactivate listening",
            "stop listening please",
            "please stop listening",
            "listening band karo",
            "band karo",
            "सुनना बंद करो",
            "बंद करो",
            "आवाज बंद करो" -> true
            else -> false
        }
    }

    /**
     * Checks if the utterance is an explicit multi-word stop-listening command,
     * differentiating from a single "stop" which acts as a NO response during confirmation.
     */
    fun isExplicitStopListening(text: String): Boolean {
        val normalized = LocalCommandResolver.normalize(text)
        return when (normalized) {
            "stop listening",
            "stop listening now",
            "stop assistant",
            "turn off listening",
            "stop voice assistant",
            "stop the assistant",
            "turn off the assistant",
            "deactivate listening",
            "stop listening please",
            "please stop listening",
            "listening band karo",
            "सुनना बंद करो",
            "आवाज बंद करो" -> true
            else -> false
        }
    }
}
