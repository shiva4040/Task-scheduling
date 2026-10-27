package com.taskscheduling.app.assistant

import java.util.UUID

/**
 * Represents a single message bubble in the floating assistant conversation.
 */
data class AssistantChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
