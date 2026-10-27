package com.taskscheduling.app.assistant

/**
 * Clean abstraction for resolving raw user commands into structured intents.
 * Operates purely locally without network or cloud AI dependencies.
 */
interface CommandResolver {
    /**
     * Resolves a raw command string into a [CommandIntent], or returns null if no intent is recognized.
     */
    fun resolve(command: String): CommandIntent?
}
