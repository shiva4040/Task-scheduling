package com.taskscheduling.app.automation.mock

import com.taskscheduling.app.automation.ScreenAnalysisResult
import com.taskscheduling.app.automation.ScreenAnalyzer
import com.taskscheduling.app.automation.VoiceEngine
import com.taskscheduling.app.automation.VoiceState
import com.taskscheduling.app.domain.model.Task
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MockVoiceEngine(
    private val taskLookup: (String) -> Task?
) : VoiceEngine {

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    override val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    override val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    override suspend fun startListening() {
        _voiceState.value = VoiceState.LISTENING
        _recognizedText.value = ""
        delay(1800) // Simulated listening duration
        
        _voiceState.value = VoiceState.PROCESSING
        _recognizedText.value = "Change my wallpaper"
        delay(800)

        val matched = taskLookup("Change my wallpaper")
        if (matched != null) {
            _voiceState.value = VoiceState.COMPLETED
        } else {
            _voiceState.value = VoiceState.ERROR
        }
    }

    override fun stopListening() {
        _voiceState.value = VoiceState.IDLE
    }

    override suspend fun processCommand(command: String): Task? {
        _voiceState.value = VoiceState.PROCESSING
        _recognizedText.value = command
        delay(600)
        val matched = taskLookup(command)
        _voiceState.value = if (matched != null) VoiceState.COMPLETED else VoiceState.ERROR
        return matched
    }
}

class MockScreenAnalyzer : ScreenAnalyzer {
    override suspend fun analyzeCurrentScreen(): ScreenAnalysisResult {
        delay(700)
        return ScreenAnalysisResult(
            uiElementsDetected = listOf("AppIcon: Settings", "Header: Wednesday, Sep 30", "Widget: Weather"),
            targetElement = "Settings App Icon",
            suggestedCoordinate = Pair(880, 1600),
            confidence = 0.96f
        )
    }
}
