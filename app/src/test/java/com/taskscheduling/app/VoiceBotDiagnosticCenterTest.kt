package com.taskscheduling.app

import android.content.Context
import android.content.ContextWrapper
import com.taskscheduling.app.feature.diagnostics.AudioFailureCause
import com.taskscheduling.app.feature.diagnostics.DiagnosticComponent
import com.taskscheduling.app.feature.diagnostics.DiagnosticSeverity
import com.taskscheduling.app.feature.diagnostics.TestOutcomeClassification
import com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticReportExporter
import com.taskscheduling.app.feature.diagnostics.TestLabRunState
import com.taskscheduling.app.feature.diagnostics.VoiceBotDiagnosticTracker
import com.taskscheduling.app.voice.LanguageMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class VoiceBotDiagnosticCenterTest {

    private lateinit var dummyContext: Context
    private lateinit var tempDir: File
    private lateinit var tracker: VoiceBotDiagnosticTracker

    private class TestContextWrapper(private val baseDir: File) : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getCacheDir(): File = baseDir
        override fun getFilesDir(): File = baseDir
        override fun getPackageName(): String = "com.taskscheduling.app"
    }

    @Before
    fun setUp() {
        tempDir = java.nio.file.Files.createTempDirectory("diag_center_test").toFile()
        dummyContext = TestContextWrapper(tempDir)
        tracker = VoiceBotDiagnosticTracker.getInstance(dummyContext)
        tracker.clearEventLogs()
    }

    @Test
    fun testAccurateStatusReporting() {
        tracker.refreshSystemStatus(
            isMicPermissionGranted = true,
            isAccessibilityServiceEnabled = true,
            isAccessibilityServiceConnected = true,
            botOverlayVisible = true,
            botIsListening = true
        )

        val sys = tracker.systemOverview.value
        assertTrue(sys.micPermissionGranted)
        assertTrue(sys.isAccessibilityEnabled)
        assertTrue(sys.isAccessibilityConnected)
        assertEquals("LISTENING", sys.floatingBotState)
    }

    @Test
    fun testEmptyAudioClassification() {
        tracker.recordCapturedAudioStats(
            samples = FloatArray(0),
            source = "DEFAULT",
            isSubmitted = false,
            rejectionReason = "Empty buffer",
            owner = "BOT"
        )

        val audio = tracker.audioCapture.value
        assertEquals(AudioFailureCause.BUFFER_EMPTY, audio.failureCause)
        assertEquals(0L, audio.totalSamplesCaptured)
        assertEquals("EMPTY", audio.bufferStatus)
    }

    @Test
    fun testSilentAudioClassification() {
        // Samples with peak 0.002f <= 0.005f threshold
        val silentSamples = FloatArray(4000) { 0.002f }
        tracker.recordCapturedAudioStats(
            samples = silentSamples,
            source = "DEFAULT",
            isSubmitted = false,
            rejectionReason = "Peak <= 0.005f",
            owner = "BOT"
        )

        val audio = tracker.audioCapture.value
        assertEquals(AudioFailureCause.AUDIO_NEARLY_SILENT, audio.failureCause)
        assertTrue(audio.peakAmplitude <= 0.005f)
        assertEquals("REJECTED (Peak <= 0.005f)", audio.bufferStatus)
    }

    @Test
    fun testTruncatedRecordingDetection() {
        // Samples with length < 3200 (less than 200ms)
        val shortSamples = FloatArray(2000) { 0.1f }
        tracker.recordCapturedAudioStats(
            samples = shortSamples,
            source = "DEFAULT",
            isSubmitted = false,
            rejectionReason = "Truncated (<200ms)",
            owner = "BOT"
        )

        val audio = tracker.audioCapture.value
        assertEquals(AudioFailureCause.AUDIO_TRUNCATED, audio.failureCause)
        assertEquals(2000L, audio.totalSamplesCaptured)
    }

    @Test
    fun testAudioStatisticsComputation() {
        // Normal speech buffer: 16000 samples (1.0 second) with known peak and silence
        val samples = FloatArray(16000) { i ->
            if (i < 8000) 0.4f * kotlin.math.sin(i.toDouble() * 0.1).toFloat() else 0.0005f
        }

        tracker.recordCapturedAudioStats(
            samples = samples,
            source = "DEFAULT",
            isSubmitted = true,
            owner = "BOT"
        )

        val audio = tracker.audioCapture.value
        assertEquals(AudioFailureCause.NONE, audio.failureCause)
        assertEquals(16000L, audio.totalSamplesCaptured)
        assertEquals(32000L, audio.totalCapturedBytes)
        assertEquals(1000L, audio.actualRecordingDurationMs)
        assertTrue("RMS must be positive", audio.rmsLevel > 0.01f)
        assertTrue("Peak amplitude must be around 0.4f", audio.peakAmplitude > 0.35f)
        assertTrue("Estimated silence percentage should be roughly 50%", audio.estimatedSilencePercentage > 40f)
    }

    @Test
    fun testLongSpeechStageTimingAndCapping() {
        tracker.recordLongSpeechTelemetry(
            totalDurationMs = 10050L,
            buffersCount = 10,
            isTruncatedByLimit = true,
            stoppedBySilence = false,
            stoppedByWordPause = false,
            endpointSilenceMs = 1200L,
            timeline = emptyList()
        )

        val longSpeech = tracker.longSpeech.value
        assertTrue("Should be flagged as truncated by 10s ceiling", longSpeech.isTruncatedByLimit)
        assertEquals(10050L, longSpeech.totalDurationMs)
        assertEquals(10, longSpeech.buffersCollected)
        assertFalse(longSpeech.stoppedByWordPause)
    }

    @Test
    fun testFloatingBotPipelineStageProgression() {
        tracker.startNewBotSession("bot_test_session_101")
        tracker.recordBotPipelineStage("Microphone Started", "SUCCESS")
        tracker.recordBotPipelineStage("Audio Buffers Received", "SUCCESS", "Speech active")
        tracker.recordBotPipelineStage("Speech Endpoint Reached", "SUCCESS", "1200ms pause")
        tracker.recordBotPipelineStage("Transcript Returned", "SUCCESS", "\"Change my wallpaper\"")

        val bot = tracker.floatingBotPipeline.value
        assertEquals("bot_test_session_101", bot.sessionId)
        assertEquals(5, bot.stages.size)
        assertEquals("Bot Activated", bot.stages[0].stageName)
        assertEquals("Microphone Started", bot.stages[1].stageName)
        assertEquals("Audio Buffers Received", bot.stages[2].stageName)
        assertEquals("Speech Endpoint Reached", bot.stages[3].stageName)
        assertEquals("Transcript Returned", bot.stages[4].stageName)
    }

    @Test
    fun testComponentSharingIdentityMatrix() {
        val bot = tracker.floatingBotPipeline.value
        assertTrue(bot.sharedRecorderImplementation)
        assertTrue(bot.sharedAudioSource)
        assertTrue(bot.sharedPcmFormat)
        assertTrue(bot.sharedPreprocessing)
        assertTrue(bot.sharedModelInstance)
        assertTrue(bot.sharedTranscriptionEntryPoint)
        assertTrue(bot.sharedInferenceLock)
        assertTrue(bot.sharedLanguageConfig)
    }

    @Test
    fun testCommandResolverTelemetryAndSafety() {
        tracker.recordCommandResolution(
            rawText = "Change my wallpaper",
            normalizedText = "change my wallpaper",
            language = "ENGLISH",
            resultIntent = "WORKFLOW_ACTION (Change Wallpaper)",
            isConfirmationRequired = true,
            workflowId = "wf_wallpaper_123"
        )

        val cmd = tracker.commandPipeline.value
        assertEquals("Change my wallpaper", cmd.exactRecognizedText)
        assertEquals("change my wallpaper", cmd.normalizedText)
        assertEquals("WORKFLOW_ACTION (Change Wallpaper)", cmd.resolverResult)
        assertTrue("Sensitive workflow must require confirmation", cmd.isConfirmationRequired)
        assertTrue(cmd.isConfirmationDisplayed)
        assertEquals("AWAITING CONFIRMATION", cmd.userConfirmationAction)
    }

    @Test
    fun testEventLogBoundsAndCapping() {
        tracker.clearEventLogs()

        // Append 300 events
        for (i in 1..300) {
            tracker.logEvent(
                severity = DiagnosticSeverity.INFO,
                component = DiagnosticComponent.AUDIO_CAPTURE,
                eventName = "EVENT_$i",
                description = "Test event number $i"
            )
        }

        val logs = tracker.eventLogs.value
        assertEquals("Event queue must be capped at MAX_EVENT_LOG_SIZE (250)", 250, logs.size)
        // Most recent event should be EVENT_300
        assertEquals("EVENT_300", logs.first().eventName)
    }

    @Test
    fun testEventLogFiltering() {
        tracker.clearEventLogs(recordClearEvent = false)
        tracker.logEvent(severity = DiagnosticSeverity.ERROR, component = DiagnosticComponent.MICROPHONE, eventName = "MIC_FAIL", description = "Mic failed")
        tracker.logEvent(severity = DiagnosticSeverity.INFO, component = DiagnosticComponent.WHISPER_INFERENCE, eventName = "INFER_OK", description = "Inference succeeded")
        tracker.logEvent(severity = DiagnosticSeverity.WARNING, component = DiagnosticComponent.ENDPOINTING, eventName = "PAUSE_DETECTED", description = "Word pause")

        val allLogs = tracker.eventLogs.value
        assertEquals(3, allLogs.size)

        val errorOnly = allLogs.filter { it.severity == DiagnosticSeverity.ERROR }
        assertEquals(1, errorOnly.size)
        assertEquals("MIC_FAIL", errorOnly.first().eventName)

        val whisperOnly = allLogs.filter { it.component == DiagnosticComponent.WHISPER_INFERENCE }
        assertEquals(1, whisperOnly.size)
        assertEquals("INFER_OK", whisperOnly.first().eventName)
    }

    @Test
    fun testDiagnosticReportExportAndRedaction() {
        tracker.refreshSystemStatus(
            isMicPermissionGranted = true,
            isAccessibilityServiceEnabled = true,
            isAccessibilityServiceConnected = true,
            botOverlayVisible = true,
            botIsListening = false
        )

        val (txtFile, jsonFile) = VoiceBotDiagnosticReportExporter.exportReportFiles(dummyContext, tracker)

        assertTrue(txtFile.exists())
        assertTrue(jsonFile.exists())
        assertTrue("TXT report should be non-empty", txtFile.length() > 0)
        assertTrue("JSON report should be non-empty", jsonFile.length() > 0)

        val txtContent = txtFile.readText()
        assertTrue(txtContent.contains("VOICE & BOT DIAGNOSTIC REPORT"))
        assertTrue(txtContent.contains("APP & ANDROID ENVIRONMENT"))
        assertTrue(txtContent.contains("MICROPHONE & AUDIO CAPTURE STATISTICS"))
        assertTrue(txtContent.contains("FLOATING BOT PIPELINE STAGES"))
        assertTrue(txtContent.contains("COMPONENT SHARING & RUNTIME IDENTITY MATRIX"))

        // Verify sensitive user data paths are redacted
        assertFalse(txtContent.contains("/data/user/0"))
        assertFalse(txtContent.contains("/storage/emulated/0"))

        val jsonContent = jsonFile.readText()
        val json = org.json.JSONObject(jsonContent)
        assertEquals("1.0", json.getString("reportVersion"))
        assertNotNull(json.getJSONObject("environment"))
        assertNotNull(json.getJSONObject("audioCapture"))
        assertNotNull(json.getJSONObject("floatingBotPipeline"))
    }

    @Test
    fun testAudioSessionLifecycleMeasurement() {
        tracker.recordAudioSessionLifecycle(
            startTimeMs = 1000L,
            stopTimeMs = 3500L,
            actualElapsedMs = 2500L,
            successfulReads = 39L,
            samplesPerRead = 1024,
            bytesPerRead = 2048,
            totalSamplesCaptured = 39936L,
            totalBytesCaptured = 79872L,
            finalizedAudioDurationMs = 2496L,
            stopReason = "SILENCE_ENDPOINT",
            samplesDiscarded = 0L,
            finalBufferFlushed = true,
            submittedToInference = true
        )

        val capture = tracker.audioCapture.value
        assertEquals(2500L, capture.actualRecordingDurationMs)
        assertEquals(2496L, capture.effectiveAudioDurationMs)
        assertEquals(39936L, capture.totalSamplesCaptured)
        assertEquals(79872L, capture.totalCapturedBytes)
        assertEquals(39L, capture.framesReceivedCount)
        assertEquals("SILENCE_ENDPOINT", capture.stopReason)
        assertTrue(capture.stoppedNormally)
        assertTrue(capture.finalBufferFlushed)
        assertTrue(capture.submittedToInference)
    }

    @Test
    fun testInferenceEntryPointAndEmptyTranscriptTelemetry() {
        tracker.recordInferenceEntryPoint(
            sessionId = "test_sess_42",
            jobId = "test_job_1",
            sampleCount = 32000,
            durationMs = 2000L,
            sampleFormat = "16kHz Mono 32-bit Float",
            rms = 0.045f,
            peak = 0.32f,
            bufferQuality = "VALID",
            preprocessingSuccess = true
        )

        var modelState = tracker.speechModel.value
        assertEquals("test_job_1", modelState.currentJobId)
        assertEquals("test_sess_42", modelState.lastSessionId)
        assertEquals(32000, modelState.lastAudioSampleCount)
        assertEquals(2000L, modelState.lastAudioDurationMs)
        assertEquals("16kHz Mono 32-bit Float", modelState.sampleFormat)
        assertEquals("VALID", modelState.bufferQuality)
        assertTrue(modelState.preprocessingSuccess)
        assertFalse(modelState.inferenceStarted)

        tracker.recordInferenceExecution(
            jobId = "test_job_1",
            started = true,
            completed = true,
            decodingMs = 340L,
            transcript = "",
            nativeError = null
        )

        modelState = tracker.speechModel.value
        assertTrue(modelState.inferenceStarted)
        assertTrue(modelState.inferenceCompleted)
        assertEquals(340L, modelState.decodingDurationMs)
        assertEquals("", modelState.lastReturnedTranscript)

        // Verify pipeline stage reflects EMPTY_TRANSCRIPT rather than false SUCCESS
        tracker.recordBotPipelineStage(
            stageName = "Transcript Returned",
            status = "EMPTY_TRANSCRIPT",
            details = "Returned empty string",
            durationMs = 340L,
            jobId = "test_job_1"
        )

        val pipeline = tracker.floatingBotPipeline.value
        val stage = pipeline.stages.find { it.stageName == "Transcript Returned" }
        assertNotNull(stage)
        assertEquals("EMPTY_TRANSCRIPT", stage!!.status)
        assertEquals(340L, stage.durationMs)
        assertEquals("Transcript Returned", pipeline.failureStage)
    }

    @Test
    fun testSkippedAndFailedPipelineStagesWithoutFabricatedTiming() {
        tracker.startNewBotSession("sess_skip_test")
        tracker.recordBotPipelineStage(
            stageName = "Recognition Requested",
            status = "SKIPPED",
            details = "Bypassed due to rejected audio",
            durationMs = null
        )

        val pipeline = tracker.floatingBotPipeline.value
        val stage = pipeline.stages.find { it.stageName == "Recognition Requested" }
        assertNotNull(stage)
        assertEquals("SKIPPED", stage!!.status)
        // Duration should be null, not fabricated 0
        org.junit.Assert.assertNull(stage.durationMs)
    }

    @Test
    fun testTestLabLifecycleTransitionsAndTiming() {
        val samples = FloatArray(32000) { 0.1f } // 2 seconds of 16kHz audio
        val calculatedDuration = (samples.size * 1000L) / 16000L
        assertEquals(2000L, calculatedDuration)

        tracker.updateTestLabState(
            TestLabRunState(
                testId = "lab_test_lifecycle_01",
                languageMode = LanguageMode.ENGLISH,
                callerLabel = "DIAGNOSTIC_PATH",
                expectedText = "Hello",
                outcomeClassification = TestOutcomeClassification.INCONCLUSIVE,
                capturedSampleCount = samples.size,
                recordingDurationMs = calculatedDuration,
                rmsLevel = 0.05f,
                peakAmplitude = 0.25f,
                silencePercentage = 10f,
                lifecycleStage = "RECORDING"
            )
        )

        var labState = tracker.testLabState.value
        assertEquals("RECORDING", labState.lifecycleStage)
        assertEquals(32000, labState.capturedSampleCount)
        assertEquals(2000L, labState.recordingDurationMs)
        assertEquals("DIAGNOSTIC_PATH", labState.callerLabel)

        // Progress to FINALIZING
        tracker.updateTestLabState(
            labState.copy(lifecycleStage = "FINALIZING")
        )
        labState = tracker.testLabState.value
        assertEquals("FINALIZING", labState.lifecycleStage)

        // Progress to READY_TO_TRANSCRIBE
        tracker.updateTestLabState(
            labState.copy(lifecycleStage = "READY_TO_TRANSCRIBE")
        )
        labState = tracker.testLabState.value
        assertEquals("READY_TO_TRANSCRIBE", labState.lifecycleStage)

        // Progress to TRANSCRIBING
        tracker.updateTestLabState(
            labState.copy(lifecycleStage = "TRANSCRIBING")
        )
        labState = tracker.testLabState.value
        assertEquals("TRANSCRIBING", labState.lifecycleStage)

        // Finish with RESULT
        tracker.updateTestLabState(
            labState.copy(
                recognizedText = "Hello",
                outcomeClassification = TestOutcomeClassification.SUCCESSFUL_TRANSCRIPT,
                inferenceTimeMs = 350L,
                totalTimeMs = 2350L,
                lifecycleStage = "RESULT"
            )
        )
        labState = tracker.testLabState.value
        assertEquals("RESULT", labState.lifecycleStage)
        assertEquals("Hello", labState.recognizedText)
        assertEquals(TestOutcomeClassification.SUCCESSFUL_TRANSCRIPT, labState.outcomeClassification)
    }

    @Test
    fun testZeroSampleRecordingClassification() {
        tracker.updateTestLabState(
            TestLabRunState(
                testId = "zero_sample_test",
                languageMode = LanguageMode.ENGLISH,
                callerLabel = "DIAGNOSTIC_PATH",
                outcomeClassification = TestOutcomeClassification.NO_AUDIO_CAPTURED,
                capturedSampleCount = 0,
                recordingDurationMs = 0L,
                errorDetails = "No audio samples were recorded"
            )
        )

        val labState = tracker.testLabState.value
        assertEquals(TestOutcomeClassification.NO_AUDIO_CAPTURED, labState.outcomeClassification)
        assertEquals(0, labState.capturedSampleCount)
        assertEquals(0L, labState.recordingDurationMs)
        assertEquals("No audio samples were recorded", labState.errorDetails)
    }

    @Test
    fun testPrematureStopAndHonestClassifications() {
        // Premature stop
        tracker.updateTestLabState(
            TestLabRunState(
                testId = "premature_test",
                languageMode = LanguageMode.ENGLISH,
                callerLabel = "DIAGNOSTIC_PATH",
                outcomeClassification = TestOutcomeClassification.STOPPED_PREMATURELY,
                capturedSampleCount = 1200,
                recordingDurationMs = 75L,
                errorDetails = "Recording stopped prematurely (<200 ms)"
            )
        )
        assertEquals(TestOutcomeClassification.STOPPED_PREMATURELY, tracker.testLabState.value.outcomeClassification)

        // Audio nearly silent
        tracker.updateTestLabState(
            TestLabRunState(
                testId = "silent_test",
                languageMode = LanguageMode.ENGLISH,
                callerLabel = "DIAGNOSTIC_PATH",
                outcomeClassification = TestOutcomeClassification.AUDIO_NEARLY_SILENT,
                capturedSampleCount = 16000,
                recordingDurationMs = 1000L,
                errorDetails = "Audio peak below 0.005 threshold"
            )
        )
        assertEquals(TestOutcomeClassification.AUDIO_NEARLY_SILENT, tracker.testLabState.value.outcomeClassification)

        // Mic in use by another component
        tracker.updateTestLabState(
            TestLabRunState(
                testId = "mic_conflict_test",
                languageMode = LanguageMode.ENGLISH,
                callerLabel = "DIAGNOSTIC_PATH",
                outcomeClassification = TestOutcomeClassification.MIC_IN_USE_BY_ANOTHER_COMPONENT,
                errorDetails = "Microphone is currently held by VoiceAssistantController"
            )
        )
        assertEquals(TestOutcomeClassification.MIC_IN_USE_BY_ANOTHER_COMPONENT, tracker.testLabState.value.outcomeClassification)

        // Cancelled
        tracker.updateTestLabState(
            TestLabRunState(
                testId = "cancelled_test",
                languageMode = LanguageMode.ENGLISH,
                callerLabel = "DIAGNOSTIC_PATH",
                outcomeClassification = TestOutcomeClassification.CANCELLED
            )
        )
        assertEquals(TestOutcomeClassification.CANCELLED, tracker.testLabState.value.outcomeClassification)

        // Engine busy
        tracker.updateTestLabState(
            TestLabRunState(
                testId = "busy_test",
                languageMode = LanguageMode.ENGLISH,
                callerLabel = "DIAGNOSTIC_PATH",
                outcomeClassification = TestOutcomeClassification.ENGINE_BUSY
            )
        )
        assertEquals(TestOutcomeClassification.ENGINE_BUSY, tracker.testLabState.value.outcomeClassification)
    }

    @Test
    fun testReportEmptyStateAndNoFabricatedZeroTimings() {
        // Clear lab run state
        tracker.updateTestLabState(TestLabRunState())

        val textReport = VoiceBotDiagnosticReportExporter.buildTextReport(dummyContext, tracker)
        assertTrue(textReport.contains("Status:                 No test has been run yet."))
        assertTrue(textReport.contains("Note:                   This report contains current system status, but no test results."))

        // Run a test with unmeasured timing (0 ms)
        tracker.updateTestLabState(
            TestLabRunState(
                testId = "unmeasured_timing_test",
                languageMode = LanguageMode.ENGLISH,
                callerLabel = "DIAGNOSTIC_PATH",
                outcomeClassification = TestOutcomeClassification.NO_AUDIO_CAPTURED,
                capturedSampleCount = 0,
                recordingDurationMs = 0L,
                inferenceTimeMs = 0L,
                totalTimeMs = 0L
            )
        )

        val unmeasuredReport = VoiceBotDiagnosticReportExporter.buildTextReport(dummyContext, tracker)
        // Duration and latency should display "Unavailable" rather than fabricated "0 ms"
        assertTrue(unmeasuredReport.contains("Audio Duration:         Unavailable"))
        assertTrue(unmeasuredReport.contains("Inference Duration:     Unavailable"))
        assertTrue(unmeasuredReport.contains("Total Latency:          Unavailable"))
    }

    @Test
    fun testDefaultReportFilenames() {
        val txtFilename = VoiceBotDiagnosticReportExporter.generateDefaultFilename("txt")
        val jsonFilename = VoiceBotDiagnosticReportExporter.generateDefaultFilename("json")

        assertTrue(txtFilename.startsWith("TaskScheduling_Diagnostic_"))
        assertTrue(txtFilename.endsWith(".txt"))
        assertTrue(jsonFilename.startsWith("TaskScheduling_Diagnostic_"))
        assertTrue(jsonFilename.endsWith(".json"))
    }

    @Test
    fun testDiagnosticAndFloatingBotCallerIsolation() {
        // Set up diagnostic path run
        tracker.updateTestLabState(
            TestLabRunState(
                testId = "diag_run",
                languageMode = LanguageMode.ENGLISH,
                callerLabel = "DIAGNOSTIC_PATH",
                outcomeClassification = TestOutcomeClassification.INCONCLUSIVE,
                capturedSampleCount = 16000,
                recordingDurationMs = 1000L
            )
        )
        assertEquals("DIAGNOSTIC_PATH", tracker.testLabState.value.callerLabel)

        // Set up floating bot path run
        tracker.updateTestLabState(
            TestLabRunState(
                testId = "bot_run",
                languageMode = LanguageMode.ENGLISH,
                callerLabel = "FLOATING_BOT_PATH",
                outcomeClassification = TestOutcomeClassification.SUCCESSFUL_TRANSCRIPT,
                capturedSampleCount = 32000,
                recordingDurationMs = 2000L,
                recognizedText = "Open camera"
            )
        )
        assertEquals("FLOATING_BOT_PATH", tracker.testLabState.value.callerLabel)
        assertEquals("Open camera", tracker.testLabState.value.recognizedText)

        // Verify command pipeline has not executed this as a live workflow
        val cmd = tracker.commandPipeline.value
        assertFalse("Diagnostic test lab runs must not trigger live workflow execution", cmd.workflowExecutionStatus.startsWith("EXECUTING"))
    }

    @Test
    fun testBotPreferencesDefaultsAndCustomization() {
        val defaultPrefs = com.taskscheduling.app.domain.model.BotPreferences()
        assertEquals(com.taskscheduling.app.domain.model.BotSize.MEDIUM, defaultPrefs.botSize)
        assertEquals(0.95f, defaultPrefs.windowOpacity, 0.001f)
        assertFalse(defaultPrefs.isCompactMode)
        assertTrue(defaultPrefs.showIcon)
        assertFalse(defaultPrefs.showLabel)
        assertTrue(defaultPrefs.enableAnimations)
        org.junit.Assert.assertNull(defaultPrefs.savedPositionX)
        org.junit.Assert.assertNull(defaultPrefs.savedPositionY)
        assertTrue(defaultPrefs.tapToListen)
        assertFalse(defaultPrefs.continuousListening)
        assertEquals(LanguageMode.ENGLISH, defaultPrefs.languageMode)
        assertTrue(defaultPrefs.persistentFloatingMode)

        // Test custom preferences
        val customized = defaultPrefs.copy(
            botSize = com.taskscheduling.app.domain.model.BotSize.LARGE,
            windowOpacity = 0.8f,
            isCompactMode = true,
            persistentFloatingMode = true,
            languageMode = LanguageMode.HINGLISH
        )
        assertEquals(com.taskscheduling.app.domain.model.BotSize.LARGE, customized.botSize)
        assertEquals(0.8f, customized.windowOpacity, 0.001f)
        assertTrue(customized.isCompactMode)
        assertTrue(customized.persistentFloatingMode)
        assertEquals(LanguageMode.HINGLISH, customized.languageMode)
    }
}
