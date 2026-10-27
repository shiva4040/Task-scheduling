package com.taskscheduling.app.assistant

import com.taskscheduling.app.domain.model.Workflow

sealed class WorkflowResolution {
    data class Found(val workflow: Workflow) : WorkflowResolution()
    data class NotFound(val queryName: String) : WorkflowResolution()
    data class Ambiguous(val queryName: String, val candidates: List<Workflow>) : WorkflowResolution()
}

/**
 * Abstraction for mapping recognized intents and voice/text queries to existing, saved workflows.
 * Strictly searches existing workflows and never creates new ones automatically.
 */
interface WorkflowResolver {
    fun resolveWorkflow(intent: CommandIntent): Workflow?
    fun resolveByName(commandText: String): WorkflowResolution
    fun getWallpaperWorkflows(): List<Workflow> = emptyList()
}
