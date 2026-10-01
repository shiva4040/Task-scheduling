package com.taskscheduling.app.domain.model

enum class ExecutionState {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    STOPPED
}

data class ExecutionLogEntry(
    val timestamp: String,
    val stepNumber: Int,
    val actionType: ActionType,
    val details: String,
    val status: String = "SUCCESS",
    val latencyMs: Long = 0L
)

data class ExecutionSession(
    val taskId: String,
    val taskTitle: String,
    val state: ExecutionState = ExecutionState.IDLE,
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 0,
    val currentStep: WorkflowStep? = null,
    val progress: Float = 0f,
    val currentSimulatedScreen: String = "Home",
    val activeCoordinate: CoordinateAction? = null,
    val activeActionType: ActionType? = null,
    val logs: List<ExecutionLogEntry> = emptyList(),
    val errorMessage: String? = null
)
