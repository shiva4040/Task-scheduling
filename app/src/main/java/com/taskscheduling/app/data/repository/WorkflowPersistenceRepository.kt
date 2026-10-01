package com.taskscheduling.app.data.repository

import android.content.Context
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.CoordinateAction
import com.taskscheduling.app.domain.model.DeviceProfile
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.domain.model.WorkflowStatus
import com.taskscheduling.app.domain.model.WorkflowStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class RecordingSession(
    val sessionId: String,
    val workflowId: String,
    val workflowName: String,
    val startedAt: Long,
    val updatedAt: Long,
    val lastSavedAt: Long = updatedAt,
    val isRecordingActive: Boolean,
    val sessionState: com.taskscheduling.app.domain.model.SessionState = com.taskscheduling.app.domain.model.SessionState.RECORDING,
    val deviceProfile: DeviceProfile?,
    val steps: List<WorkflowStep>,
    val schemaVersion: Int = 1
)

data class ExecutionHistoryItem(
    val id: String,
    val workflowId: String,
    val workflowName: String,
    val timestamp: Long,
    val formattedTime: String,
    val status: String, // "COMPLETED", "FAILED", "CANCELLED"
    val completedSteps: Int,
    val totalSteps: Int,
    val failedStep: Int? = null,
    val failureReason: String? = null
)

class WorkflowPersistenceRepository(private val baseDir: File) {

    constructor(context: Context) : this(context.filesDir)

    private val workflowsFile = File(baseDir, "workflows_storage.json")
    private val historyFile = File(baseDir, "execution_history_storage.json")
    private val recordingSessionFile = File(baseDir, "recording_session.json")

    private val _workflows = MutableStateFlow<Map<String, Workflow>>(emptyMap())
    val workflows: StateFlow<Map<String, Workflow>> = _workflows.asStateFlow()

    private val _history = MutableStateFlow<List<ExecutionHistoryItem>>(emptyList())
    val history: StateFlow<List<ExecutionHistoryItem>> = _history.asStateFlow()

    init {
        loadAllWorkflows()
        loadAllHistory()
    }

    private fun loadAllWorkflows() {
        if (!workflowsFile.exists()) return
        try {
            val jsonStr = workflowsFile.readText()
            val jsonArray = JSONArray(jsonStr)
            val map = mutableMapOf<String, Workflow>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val wf = jsonToWorkflow(obj)
                map[wf.id] = wf
            }
            _workflows.value = map
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadAllHistory() {
        if (!historyFile.exists()) return
        try {
            val jsonStr = historyFile.readText()
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<ExecutionHistoryItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(jsonToHistory(obj))
            }
            _history.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun saveWorkflow(workflow: Workflow) = withContext(Dispatchers.IO) {
        val updated = _workflows.value.toMutableMap()
        updated[workflow.id] = workflow
        _workflows.value = updated
        persistWorkflows(updated.values.toList())
    }

    fun getWorkflow(id: String): Workflow? {
        return _workflows.value[id]
    }

    fun getAllWorkflows(): List<Workflow> {
        return _workflows.value.values.toList()
    }

    suspend fun deleteWorkflow(id: String) = withContext(Dispatchers.IO) {
        val updated = _workflows.value.toMutableMap()
        updated.remove(id)
        _workflows.value = updated
        persistWorkflows(updated.values.toList())
    }

    /**
     * Immediately and atomically persists an active recording session.
     */
    suspend fun saveRecordingSession(session: RecordingSession) = withContext(Dispatchers.IO) {
        try {
            val json = recordingSessionToJson(session)
            atomicWriteFile(recordingSessionFile, json.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Loads the unfinished recording session if present on disk.
     * Returns null if no session file exists or if corrupt.
     */
    suspend fun loadRecordingSession(): RecordingSession? = withContext(Dispatchers.IO) {
        if (!recordingSessionFile.exists()) return@withContext null
        try {
            val jsonStr = recordingSessionFile.readText()
            if (jsonStr.isBlank()) return@withContext null
            val obj = JSONObject(jsonStr)
            jsonToRecordingSession(obj)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Clears the unfinished recording session file.
     */
    suspend fun clearRecordingSession() = withContext(Dispatchers.IO) {
        try {
            if (recordingSessionFile.exists()) {
                recordingSessionFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun saveHistoryRecord(item: ExecutionHistoryItem) = withContext(Dispatchers.IO) {
        val updated = listOf(item) + _history.value
        _history.value = updated
        persistHistory(updated)
    }

    private fun persistWorkflows(list: List<Workflow>) {
        try {
            val jsonArray = JSONArray()
            for (wf in list) {
                jsonArray.put(workflowToJson(wf))
            }
            atomicWriteFile(workflowsFile, jsonArray.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persistHistory(list: List<ExecutionHistoryItem>) {
        try {
            val jsonArray = JSONArray()
            for (item in list) {
                jsonArray.put(historyToJson(item))
            }
            atomicWriteFile(historyFile, jsonArray.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Atomic write helper to prevent partial/corrupt state during sudden app close or process termination.
     */
    private fun atomicWriteFile(targetFile: File, content: String) {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        tempFile.writeText(content)
        if (targetFile.exists()) {
            targetFile.delete()
        }
        tempFile.renameTo(targetFile)
    }

    companion object {
        fun workflowToJson(wf: Workflow): JSONObject {
            return JSONObject().apply {
                put("id", wf.id)
                put("name", wf.name)
                put("description", wf.description)
                put("createdAt", wf.createdAt)
                put("updatedAt", wf.updatedAt)
                put("status", wf.status.name)
                put("schemaVersion", wf.schemaVersion)

                wf.deviceProfile?.let { dp ->
                    put("deviceProfile", JSONObject().apply {
                        put("manufacturer", dp.manufacturer)
                        put("model", dp.model)
                        put("androidVersion", dp.androidVersion)
                        put("apiLevel", dp.apiLevel)
                        put("screenWidth", dp.screenWidth)
                        put("screenHeight", dp.screenHeight)
                        put("density", dp.density.toDouble())
                        put("orientation", dp.orientation)
                    })
                }

                val stepsArr = JSONArray()
                for (s in wf.steps) {
                    stepsArr.put(JSONObject().apply {
                        put("id", s.id)
                        put("order", s.order)
                        put("type", s.type.name)
                        put("startX", s.startX)
                        put("startY", s.startY)
                        if (s.endX != null) put("endX", s.endX)
                        if (s.endY != null) put("endY", s.endY)
                        put("duration", s.duration)
                        put("waitAfter", s.waitAfter)
                        put("description", s.description)
                    })
                }
                put("steps", stepsArr)
            }
        }

        fun jsonToWorkflow(obj: JSONObject): Workflow {
            val dpObj = obj.optJSONObject("deviceProfile")
            val deviceProfile = dpObj?.let {
                DeviceProfile(
                    manufacturer = it.optString("manufacturer", ""),
                    model = it.optString("model", ""),
                    androidVersion = it.optString("androidVersion", ""),
                    apiLevel = it.optInt("apiLevel", 0),
                    screenWidth = it.optInt("screenWidth", 0),
                    screenHeight = it.optInt("screenHeight", 0),
                    density = it.optDouble("density", 1.0).toFloat(),
                    orientation = it.optString("orientation", "PORTRAIT")
                )
            }

            val stepsList = mutableListOf<WorkflowStep>()
            val stepsArr = obj.optJSONArray("steps")
            if (stepsArr != null) {
                for (i in 0 until stepsArr.length()) {
                    val sObj = stepsArr.getJSONObject(i)
                    val typeStr = sObj.optString("type", "TAP")
                    val actionType = try { ActionType.valueOf(typeStr) } catch (_: Exception) { ActionType.TAP }
                    val startX = sObj.optInt("startX", 0)
                    val startY = sObj.optInt("startY", 0)
                    val endX = if (sObj.has("endX")) sObj.getInt("endX") else null
                    val endY = if (sObj.has("endY")) sObj.getInt("endY") else null

                    stepsList.add(
                        WorkflowStep(
                            id = sObj.optString("id"),
                            order = sObj.optInt("order", i + 1),
                            type = actionType,
                            startX = startX,
                            startY = startY,
                            endX = endX,
                            endY = endY,
                            duration = sObj.optLong("duration", 100L),
                            waitAfter = sObj.optLong("waitAfter", 500L),
                            description = sObj.optString("description", ""),
                            stepNumber = sObj.optInt("order", i + 1),
                            label = sObj.optString("description", ""),
                            actionType = actionType,
                            coordinate = CoordinateAction(startX, startY, endX, endY)
                        )
                    )
                }
            }

            val statusStr = obj.optString("status", "NOT_CALIBRATED")
            val status = try { WorkflowStatus.valueOf(statusStr) } catch (_: Exception) { WorkflowStatus.NOT_CALIBRATED }
            val schemaVersion = obj.optInt("schemaVersion", 1)

            return Workflow(
                id = obj.getString("id"),
                name = obj.getString("name"),
                description = obj.optString("description", ""),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                status = status,
                deviceProfile = deviceProfile,
                steps = stepsList,
                schemaVersion = schemaVersion
            )
        }

        fun historyToJson(item: ExecutionHistoryItem): JSONObject {
            return JSONObject().apply {
                put("id", item.id)
                put("workflowId", item.workflowId)
                put("workflowName", item.workflowName)
                put("timestamp", item.timestamp)
                put("formattedTime", item.formattedTime)
                put("status", item.status)
                put("completedSteps", item.completedSteps)
                put("totalSteps", item.totalSteps)
                if (item.failedStep != null) put("failedStep", item.failedStep)
                if (item.failureReason != null) put("failureReason", item.failureReason)
            }
        }

        fun jsonToHistory(obj: JSONObject): ExecutionHistoryItem {
            return ExecutionHistoryItem(
                id = obj.getString("id"),
                workflowId = obj.getString("workflowId"),
                workflowName = obj.getString("workflowName"),
                timestamp = obj.getLong("timestamp"),
                formattedTime = obj.getString("formattedTime"),
                status = obj.getString("status"),
                completedSteps = obj.getInt("completedSteps"),
                totalSteps = obj.getInt("totalSteps"),
                failedStep = if (obj.has("failedStep")) obj.getInt("failedStep") else null,
                failureReason = if (obj.has("failureReason")) obj.getString("failureReason") else null
            )
        }

        fun recordingSessionToJson(session: RecordingSession): JSONObject {
            return JSONObject().apply {
                put("sessionId", session.sessionId)
                put("workflowId", session.workflowId)
                put("workflowName", session.workflowName)
                put("startedAt", session.startedAt)
                put("updatedAt", session.updatedAt)
                put("lastSavedAt", session.lastSavedAt)
                put("isRecordingActive", session.isRecordingActive)
                put("sessionState", session.sessionState.name)
                put("schemaVersion", session.schemaVersion)

                session.deviceProfile?.let { dp ->
                    put("deviceProfile", JSONObject().apply {
                        put("manufacturer", dp.manufacturer)
                        put("model", dp.model)
                        put("androidVersion", dp.androidVersion)
                        put("apiLevel", dp.apiLevel)
                        put("screenWidth", dp.screenWidth)
                        put("screenHeight", dp.screenHeight)
                        put("density", dp.density.toDouble())
                        put("orientation", dp.orientation)
                    })
                }

                val stepsArr = JSONArray()
                for (s in session.steps) {
                    stepsArr.put(JSONObject().apply {
                        put("id", s.id)
                        put("order", s.order)
                        put("type", s.type.name)
                        put("startX", s.startX)
                        put("startY", s.startY)
                        if (s.endX != null) put("endX", s.endX)
                        if (s.endY != null) put("endY", s.endY)
                        put("duration", s.duration)
                        put("waitAfter", s.waitAfter)
                        put("description", s.description)
                    })
                }
                put("steps", stepsArr)
            }
        }

        fun jsonToRecordingSession(obj: JSONObject): RecordingSession {
            val dpObj = obj.optJSONObject("deviceProfile")
            val deviceProfile = dpObj?.let {
                DeviceProfile(
                    manufacturer = it.optString("manufacturer", ""),
                    model = it.optString("model", ""),
                    androidVersion = it.optString("androidVersion", ""),
                    apiLevel = it.optInt("apiLevel", 0),
                    screenWidth = it.optInt("screenWidth", 0),
                    screenHeight = it.optInt("screenHeight", 0),
                    density = it.optDouble("density", 1.0).toFloat(),
                    orientation = it.optString("orientation", "PORTRAIT")
                )
            }

            val stepsList = mutableListOf<WorkflowStep>()
            val stepsArr = obj.optJSONArray("steps")
            if (stepsArr != null) {
                for (i in 0 until stepsArr.length()) {
                    val sObj = stepsArr.getJSONObject(i)
                    val typeStr = sObj.optString("type", "TAP")
                    val actionType = try { ActionType.valueOf(typeStr) } catch (_: Exception) { ActionType.TAP }
                    val startX = sObj.optInt("startX", 0)
                    val startY = sObj.optInt("startY", 0)
                    val endX = if (sObj.has("endX")) sObj.getInt("endX") else null
                    val endY = if (sObj.has("endY")) sObj.getInt("endY") else null

                    stepsList.add(
                        WorkflowStep(
                            id = sObj.optString("id"),
                            order = sObj.optInt("order", i + 1),
                            type = actionType,
                            startX = startX,
                            startY = startY,
                            endX = endX,
                            endY = endY,
                            duration = sObj.optLong("duration", 100L),
                            waitAfter = sObj.optLong("waitAfter", 500L),
                            description = sObj.optString("description", ""),
                            stepNumber = sObj.optInt("order", i + 1),
                            label = sObj.optString("description", ""),
                            actionType = actionType,
                            coordinate = CoordinateAction(startX, startY, endX, endY)
                        )
                    )
                }
            }

            val sessionStateStr = obj.optString("sessionState", "RECORDING")
            val sessionState = try {
                com.taskscheduling.app.domain.model.SessionState.valueOf(sessionStateStr)
            } catch (_: Exception) {
                com.taskscheduling.app.domain.model.SessionState.RECORDING
            }

            return RecordingSession(
                sessionId = obj.optString("sessionId", "session_unknown"),
                workflowId = obj.optString("workflowId", "wf_wallpaper"),
                workflowName = obj.optString("workflowName", "Change Wallpaper"),
                startedAt = obj.optLong("startedAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                lastSavedAt = obj.optLong("lastSavedAt", obj.optLong("updatedAt", System.currentTimeMillis())),
                isRecordingActive = obj.optBoolean("isRecordingActive", true),
                sessionState = sessionState,
                deviceProfile = deviceProfile,
                steps = stepsList,
                schemaVersion = obj.optInt("schemaVersion", 1)
            )
        }
    }
}
