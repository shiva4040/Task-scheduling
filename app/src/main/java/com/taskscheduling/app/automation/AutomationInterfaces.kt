package com.taskscheduling.app.automation

import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.ExecutionSession
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.domain.model.WorkflowStep
import kotlinx.coroutines.flow.StateFlow

enum class StepExecutionStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED
}

data class StepExecutionResult(
    val status: StepExecutionStatus,
    val stepOrder: Int,
    val actionType: ActionType,
    val message: String = "",
    val latencyMs: Long = 0L
)

interface AutomationEngine {
    val executionSession: StateFlow<ExecutionSession>

    suspend fun executeTap(x: Int, y: Int, durationMs: Long = 100L): StepExecutionResult
    suspend fun executeLongPress(x: Int, y: Int, durationMs: Long = 900L): StepExecutionResult
    suspend fun executeSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long = 500L): StepExecutionResult
    suspend fun executeWait(durationMs: Long): StepExecutionResult
    suspend fun executeBack(): StepExecutionResult
    suspend fun executeStep(step: WorkflowStep): StepExecutionResult
    suspend fun executeWorkflow(workflow: Workflow, speedMultiplier: Float = 1.0f)
    suspend fun testStep(step: WorkflowStep): StepExecutionResult

    // Legacy and control operations
    suspend fun execute(task: Task, speedMultiplier: Float = 1.0f)
    fun pause()
    fun resume()
    fun stop()
    fun restart()
    suspend fun stepNext()
}

interface DeviceController {
    suspend fun injectTap(x: Int, y: Int, durationMs: Long)
    suspend fun injectSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long)
    suspend fun sendSystemKey(keyCode: Int)
}

enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    EXECUTING,
    COMPLETED,
    ERROR
}

interface VoiceEngine {
    val voiceState: StateFlow<VoiceState>
    val recognizedText: StateFlow<String>

    suspend fun startListening()
    fun stopListening()
    suspend fun processCommand(command: String): Task?
}

data class ScreenAnalysisResult(
    val uiElementsDetected: List<String>,
    val targetElement: String,
    val suggestedCoordinate: Pair<Int, Int>,
    val confidence: Float
)

interface ScreenAnalyzer {
    suspend fun analyzeCurrentScreen(): ScreenAnalysisResult
}
