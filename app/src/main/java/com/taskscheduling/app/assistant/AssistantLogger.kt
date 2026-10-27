package com.taskscheduling.app.assistant

/**
 * Safe, structured logging for offline voice assistant operations.
 * Operates safely in both Android runtime and JVM unit test environments.
 */
object AssistantLogger {
    var logHandler: (String, String) -> Unit = { tag, msg ->
        try {
            android.util.Log.d(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    fun d(tag: String, msg: String) = logHandler(tag, msg)
    fun i(tag: String, msg: String) = logHandler(tag, msg)
    fun w(tag: String, msg: String) = logHandler(tag, msg)
    fun e(tag: String, msg: String) = logHandler(tag, msg)
}
