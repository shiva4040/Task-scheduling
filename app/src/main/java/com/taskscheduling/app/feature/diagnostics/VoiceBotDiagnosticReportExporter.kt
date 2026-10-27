package com.taskscheduling.app.feature.diagnostics

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates comprehensive TXT and JSON diagnostic reports covering all 15 required sections.
 * Automatically redacts sensitive paths and values.
 */
object VoiceBotDiagnosticReportExporter {

    private fun redactPath(path: String?): String {
        if (path == null) return "None"
        return path
            .replace(Regex("/data/user/\\d+/[^/]+"), "<app-storage>")
            .replace(Regex("/data/data/[^/]+"), "<app-storage>")
            .replace(Regex("/storage/emulated/\\d+"), "<external-storage>")
    }

    /**
     * Builds a human-readable plain text report.
     */
    fun buildTextReport(
        context: Context,
        tracker: VoiceBotDiagnosticTracker
    ): String {
        val sys = tracker.systemOverview.value
        val audio = tracker.audioCapture.value
        val model = tracker.speechModel.value
        val longSpeech = tracker.longSpeech.value
        val bot = tracker.floatingBotPipeline.value
        val cmd = tracker.commandPipeline.value
        val lab = tracker.testLabState.value
        val events = tracker.eventLogs.value

        val nowFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault()).format(Date())

        return buildString {
            appendLine("================================================================================")
            appendLine("             VOICE & BOT DIAGNOSTIC REPORT — TASK SCHEDULING                   ")
            appendLine("================================================================================")
            appendLine("Report Timestamp: $nowFormatted")
            appendLine()

            appendLine("--- 1. APP & ANDROID ENVIRONMENT ---")
            appendLine("App Package:        ${context.packageName}")
            appendLine("Device Model:       ${Build.MANUFACTURER ?: "Unknown"} ${Build.MODEL ?: "Device"} (${Build.PRODUCT ?: ""})")
            appendLine("Android Version:    ${Build.VERSION.RELEASE ?: ""} (API Level ${Build.VERSION.SDK_INT})")
            appendLine("Device Architecture:${Build.SUPPORTED_ABIS?.joinToString(", ") ?: "arm64-v8a"}")
            appendLine()

            appendLine("--- 2. PERMISSIONS & SERVICE HEALTH ---")
            appendLine("Microphone Permission (RECORD_AUDIO): ${if (sys.micPermissionGranted) "GRANTED" else "DENIED"}")
            appendLine("Accessibility Service Enabled:        ${if (sys.isAccessibilityEnabled) "YES" else "NO"}")
            appendLine("Accessibility Service Connected:      ${if (sys.isAccessibilityConnected) "CONNECTED" else "DISCONNECTED"}")
            appendLine("Floating Bot State:                   ${sys.floatingBotState}")
            appendLine()

            appendLine("--- 3. SPEECH MODEL & RUNTIME CONFIGURATION ---")
            appendLine("Engine Implementation: ${model.engineImplementation}")
            appendLine("Loaded Model File:     ${model.modelFilename} (${model.modelSizeBytes / (1024 * 1024)} MB)")
            appendLine("Redacted Model Path:   ${redactPath(sys.loadedModelPath)}")
            appendLine("Model Exists / Readable: ${model.modelFileExists} / ${model.modelFileReadable}")
            appendLine("Model State:           ${model.modelState}")
            appendLine("Model Load Latency:    ${model.modelLoadDurationMs} ms")
            appendLine("Quantization Type:     ${model.quantization}")
            appendLine("Worker Thread Count:   ${model.configuredThreadCount} threads")
            appendLine("Inference Parameters:  ${model.inferenceParameters}")
            appendLine("Requested Language:    ${model.requestedLanguageCode} (Auto-Detect: ${model.isAutoLanguageDetection})")
            appendLine("Translation Mode:      ${if (model.isTranslationEnabled) "TRANSLATE" else "OFFLINE SOURCE (translate=false)"}")
            appendLine("Inference Lock State:  ${model.activeInferenceOwner}")
            appendLine("Last Native Error:     ${model.nativeError ?: "None"}")
            appendLine()

            appendLine("--- 4. MICROPHONE & AUDIO CAPTURE STATISTICS ---")
            appendLine("Audio Source (Req/Act):${audio.requestedSource} / ${audio.actualSource}")
            appendLine("Recorder State:        ${audio.recorderState} (Initialized: ${audio.isInitialized})")
            appendLine("Format & Encoding:     ${audio.sampleRate} Hz, ${audio.channelCount} Channel(s), ${audio.pcmEncoding}")
            appendLine("Buffer Size:           ${audio.bufferSizeBytes} bytes")
            appendLine("Total Samples Captured:${audio.totalSamplesCaptured} samples (${audio.totalCapturedBytes} bytes)")
            appendLine("Actual vs Eff. Duration:${audio.actualRecordingDurationMs} ms vs ${audio.effectiveAudioDurationMs} ms")
            appendLine("Frames Received / Empty:${audio.framesReceivedCount} / ${audio.emptyFramesCount}")
            appendLine("Read Errors:           ${audio.readErrorsCount} (Last Code: ${audio.lastReadErrorCode})")
            appendLine("Buffer Overruns:       ${audio.bufferOverrunsCount}")
            appendLine("Buffer Dispatch Status:${audio.bufferStatus}")
            appendLine("Stopped Normally:      ${audio.stoppedNormally} (Before speech end: ${audio.stoppedBeforeSpeechFinished})")
            appendLine("Audio RMS Level:       ${"%.4f".format(audio.rmsLevel)}")
            appendLine("Peak Amplitude:        ${"%.4f".format(audio.peakAmplitude)}")
            appendLine("Min / Max Sample:      ${"%.4f".format(audio.minSampleValue)} / ${"%.4f".format(audio.maxSampleValue)}")
            appendLine("Near-Zero Samples:     ${"%.2f".format(audio.nearZeroSamplesPercentage)}% (< 0.001)")
            appendLine("Clipped Samples:       ${"%.2f".format(audio.clippedSamplesPercentage)}% (>= 0.99)")
            appendLine("Estimated Silence:     ${"%.2f".format(audio.estimatedSilencePercentage)}% (< 0.005)")
            appendLine("Failure Cause Diagnosis:${audio.failureCause.title} — ${audio.failureCause.explanation}")
            if (audio.failureDetails.isNotBlank()) {
                appendLine("Failure Details:       ${audio.failureDetails}")
            }
            appendLine()

            appendLine("--- 5. RECORDING & ENDPOINTING DETAILS ---")
            appendLine("Max Recording Duration Limit:  ${longSpeech.configuredMaxRecordingMs} ms")
            appendLine("Endpoint Silence Timeout:      ${longSpeech.configuredEndpointSilenceMs} ms")
            appendLine("Total Utterance Duration:      ${longSpeech.totalDurationMs} ms")
            appendLine("Buffers Collected:             ${longSpeech.buffersCollected}")
            appendLine("Buffer Flushed Before Inference: ${longSpeech.finalBufferFlushed}")
            appendLine("Truncated By 10s Limit:        ${longSpeech.isTruncatedByLimit}")
            appendLine("Stopped By Silence Detection:  ${longSpeech.stoppedBySilenceDetection}")
            appendLine("Stopped By Word Pause:         ${longSpeech.stoppedByWordPause}")
            appendLine("Full Buffer Reached Engine:    ${longSpeech.transcriptionReceivedFullBuffer}")
            if (longSpeech.stageTimeline.isNotEmpty()) {
                appendLine("Endpoint Stage Timeline:")
                for (st in longSpeech.stageTimeline) {
                    appendLine("  • [${st.timestampFormatted}] ${st.stageName} -> ${st.status} (${st.durationMs}ms) ${st.details}")
                }
            }
            appendLine()

            appendLine("--- 6. FLOATING BOT PIPELINE STAGES ---")
            appendLine("Session ID:      ${bot.sessionId.ifBlank { "None" }}")
            appendLine("Bot Activated:   ${bot.botActivated}")
            appendLine("Bot Listening:   ${bot.isListening}")
            appendLine("Current Stage:   ${bot.currentStage}")
            appendLine("Failure Stage:   ${bot.failureStage ?: "None"}")
            appendLine("Failure Reason:  ${bot.failureReason ?: "None"}")
            if (bot.stages.isNotEmpty()) {
                appendLine("Pipeline Progression:")
                for (p in bot.stages) {
                    appendLine("  [Step ${p.stageIndex}] [${p.timestampFormatted}] ${p.stageName} => ${p.status} (${p.durationMs}ms) ${p.details}")
                }
            }
            appendLine()

            appendLine("--- 7. COMPONENT SHARING & RUNTIME IDENTITY MATRIX ---")
            appendLine("• Recorder Implementation Shared:       ${if (bot.sharedRecorderImplementation) "YES (AudioRecord 16kHz Mono)" else "NO"}")
            appendLine("• Audio Source Identical:               ${if (bot.sharedAudioSource) "YES" else "NO"}")
            appendLine("• PCM Format Identical:                 ${if (bot.sharedPcmFormat) "YES (16kHz PCM16)" else "NO"}")
            appendLine("• Audio Preprocessing Shared:           ${if (bot.sharedPreprocessing) "YES (prepareAudioForWhisper)" else "NO"}")
            appendLine("• Whisper Model Instance Shared:        ${if (bot.sharedModelInstance) "YES (Singleton RAM Instance)" else "NO"}")
            appendLine("• Transcription Entry Point Shared:     ${if (bot.sharedTranscriptionEntryPoint) "YES (SpeechRecognitionService)" else "NO"}")
            appendLine("• Inference Lock Mutex Shared:          ${if (bot.sharedInferenceLock) "YES (inferenceLock)" else "NO"}")
            appendLine("• Language Config Shared:               ${if (bot.sharedLanguageConfig) "YES (LanguageMode)" else "NO"}")
            appendLine()

            appendLine("--- 8. COMMAND & WORKFLOW PIPELINE ---")
            appendLine("Raw Transcript Received:     \"${cmd.exactRecognizedText}\"")
            appendLine("Normalized Text:             \"${cmd.normalizedText}\"")
            appendLine("Resolver Entry Time:         ${cmd.resolverEntryTimeFormatted.ifBlank { "N/A" }}")
            appendLine("Resolver Result:             ${cmd.resolverResult.ifBlank { "None" }}")
            appendLine("Matched Intent / Workflow:   ${cmd.matchedCommandOrWorkflowId.ifBlank { "None" }}")
            appendLine("Unmatched Reason:            ${cmd.noMatchReason ?: "N/A"}")
            appendLine("Confirmation Required / Shown: ${cmd.isConfirmationRequired} / ${cmd.isConfirmationDisplayed}")
            appendLine("User Confirmation Action:    ${cmd.userConfirmationAction}")
            appendLine("Workflow Status / Step:      ${cmd.workflowExecutionStatus} ${cmd.failedWorkflowStep?.let { "Step $it" } ?: ""}")
            if (cmd.workflowError != null) {
                appendLine("Workflow Error:              ${cmd.workflowError}")
            }
            appendLine()

            appendLine("--- 9. TEST LAB RESULTS (EN / HI / HINGLISH) ---")
            if (lab.testId.isBlank()) {
                appendLine("Status:                 No test has been run yet.")
                appendLine("Note:                   This report contains current system status, but no test results.")
            } else {
                appendLine("Test ID:                ${lab.testId}")
                appendLine("Language Mode:          ${lab.languageMode.displayName} (${lab.languageMode.code})")
                appendLine("Caller Label:           ${lab.callerLabel}")
                appendLine("Lifecycle Stage:        ${lab.lifecycleStage}")
                appendLine("Expected Text:          ${lab.expectedText.ifBlank { "None" }}")
                appendLine("Recognized Text:        ${lab.recognizedText.ifBlank { "None" }}")
                appendLine("Outcome Classification: ${lab.outcomeClassification.displayName}")
                appendLine("Audio Duration:         ${if (lab.recordingDurationMs > 0) "${lab.recordingDurationMs} ms (${lab.capturedSampleCount} samples)" else "Unavailable"}")
                appendLine("RMS / Peak:             ${"%.4f".format(lab.rmsLevel)} / ${"%.4f".format(lab.peakAmplitude)}")
                appendLine("Silence Ratio:          ${"%.1f".format(lab.silencePercentage)}%")
                appendLine("Inference Duration:     ${if (lab.inferenceTimeMs > 0) "${lab.inferenceTimeMs} ms" else "Unavailable"}")
                appendLine("Total Latency:          ${if (lab.totalTimeMs > 0) "${lab.totalTimeMs} ms" else "Unavailable"}")
                if (lab.errorDetails != null) {
                    appendLine("Lab Error:              ${lab.errorDetails}")
                }
            }
            appendLine()

            appendLine("--- 10. UNRESOLVED CAUSES & MISSING INFORMATION ---")
            val missing = mutableListOf<String>()
            if (!sys.micPermissionGranted) missing.add("Microphone permission was not granted.")
            if (!sys.isAccessibilityConnected) missing.add("Accessibility service is disconnected.")
            if (audio.peakAmplitude <= 0.005f) missing.add("Audio peak was below 0.005f; check hardware mic gain or physical distance.")
            if (longSpeech.isTruncatedByLimit) missing.add("Sentence exceeded 10-second limit and was truncated.")
            if (missing.isEmpty()) {
                appendLine("None: All pipeline metrics collected successfully.")
            } else {
                for (m in missing) appendLine("• $m")
            }
            appendLine()

            appendLine("--- 11. RECENT EVENT TIMELINE (LAST ${events.size} EVENTS) ---")
            for (e in events.take(50)) {
                appendLine("[${e.timestampFormatted}] [${e.severity}] [${e.component}] ${e.eventName}: ${e.description}")
                if (e.suggestedCheck != null) {
                    appendLine("    ↳ Suggested Check: ${e.suggestedCheck}")
                }
            }
            appendLine("================================================================================")
            appendLine("                              END OF REPORT                                     ")
            appendLine("================================================================================")
        }
    }

    /**
     * Builds a structured JSON report for programmatic analysis.
     */
    fun buildJsonReport(
        context: Context,
        tracker: VoiceBotDiagnosticTracker
    ): JSONObject {
        val sys = tracker.systemOverview.value
        val audio = tracker.audioCapture.value
        val model = tracker.speechModel.value
        val longSpeech = tracker.longSpeech.value
        val bot = tracker.floatingBotPipeline.value
        val cmd = tracker.commandPipeline.value
        val lab = tracker.testLabState.value
        val events = tracker.eventLogs.value

        return JSONObject().apply {
            put("reportVersion", "1.0")
            put("timestamp", System.currentTimeMillis())
            put("formattedDate", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault()).format(Date()))

            put("environment", JSONObject().apply {
                put("packageName", context.packageName)
                put("deviceManufacturer", Build.MANUFACTURER)
                put("deviceModel", Build.MODEL)
                put("androidRelease", Build.VERSION.RELEASE)
                put("apiLevel", Build.VERSION.SDK_INT)
                put("supportedAbis", JSONArray(Build.SUPPORTED_ABIS?.toList() ?: listOf("arm64-v8a")))
            })

            put("permissionsAndServices", JSONObject().apply {
                put("micPermissionGranted", sys.micPermissionGranted)
                put("accessibilityServiceEnabled", sys.isAccessibilityEnabled)
                put("accessibilityServiceConnected", sys.isAccessibilityConnected)
                put("floatingBotState", sys.floatingBotState)
            })

            put("speechModel", JSONObject().apply {
                put("implementation", model.engineImplementation)
                put("modelFilename", model.modelFilename)
                put("modelSizeBytes", model.modelSizeBytes)
                put("modelFileExists", model.modelFileExists)
                put("modelState", model.modelState)
                put("loadDurationMs", model.modelLoadDurationMs)
                put("threads", model.configuredThreadCount)
                put("languageCode", model.requestedLanguageCode)
                put("isAutoLanguage", model.isAutoLanguageDetection)
                put("isTranslationEnabled", model.isTranslationEnabled)
                put("lockOwner", model.activeInferenceOwner)
                put("lastError", model.nativeError ?: JSONObject.NULL)
            })

            put("audioCapture", JSONObject().apply {
                put("requestedSource", audio.requestedSource)
                put("actualSource", audio.actualSource)
                put("sampleRate", audio.sampleRate)
                put("channelCount", audio.channelCount)
                put("pcmEncoding", audio.pcmEncoding)
                put("totalSamples", audio.totalSamplesCaptured)
                put("totalBytes", audio.totalCapturedBytes)
                put("durationMs", audio.actualRecordingDurationMs)
                put("effectiveDurationMs", audio.effectiveAudioDurationMs)
                put("rmsLevel", audio.rmsLevel)
                put("peakAmplitude", audio.peakAmplitude)
                put("silencePercentage", audio.estimatedSilencePercentage)
                put("bufferStatus", audio.bufferStatus)
                put("failureCause", audio.failureCause.name)
                put("failureDetails", audio.failureDetails)
            })

            put("longSpeech", JSONObject().apply {
                put("maxDurationLimitMs", longSpeech.configuredMaxRecordingMs)
                put("endpointSilenceMs", longSpeech.configuredEndpointSilenceMs)
                put("totalDurationMs", longSpeech.totalDurationMs)
                put("buffersCollected", longSpeech.buffersCollected)
                put("truncatedByLimit", longSpeech.isTruncatedByLimit)
                put("stoppedBySilence", longSpeech.stoppedBySilenceDetection)
                put("stoppedByWordPause", longSpeech.stoppedByWordPause)
            })

            put("floatingBotPipeline", JSONObject().apply {
                put("sessionId", bot.sessionId)
                put("currentStage", bot.currentStage)
                put("failureStage", bot.failureStage ?: JSONObject.NULL)
                put("failureReason", bot.failureReason ?: JSONObject.NULL)
                put("sharedIdentity", JSONObject().apply {
                    put("recorderShared", bot.sharedRecorderImplementation)
                    put("sourceShared", bot.sharedAudioSource)
                    put("pcmShared", bot.sharedPcmFormat)
                    put("preprocessingShared", bot.sharedPreprocessing)
                    put("modelShared", bot.sharedModelInstance)
                    put("entryPointShared", bot.sharedTranscriptionEntryPoint)
                    put("lockShared", bot.sharedInferenceLock)
                    put("languageShared", bot.sharedLanguageConfig)
                })
            })

            put("commandPipeline", JSONObject().apply {
                put("rawTranscript", cmd.exactRecognizedText)
                put("normalizedText", cmd.normalizedText)
                put("resolverResult", cmd.resolverResult)
                put("matchedCommand", cmd.matchedCommandOrWorkflowId)
                put("isConfirmationRequired", cmd.isConfirmationRequired)
                put("workflowStatus", cmd.workflowExecutionStatus)
            })

            put("testLab", JSONObject().apply {
                put("testId", lab.testId)
                put("languageMode", lab.languageMode.name)
                put("expectedText", lab.expectedText)
                put("recognizedText", lab.recognizedText)
                put("outcome", lab.outcomeClassification.name)
                put("rms", lab.rmsLevel)
                put("peak", lab.peakAmplitude)
                put("inferenceMs", lab.inferenceTimeMs)
            })

            val eventsArr = JSONArray()
            for (e in events.take(100)) {
                eventsArr.put(JSONObject().apply {
                    put("id", e.id)
                    put("time", e.timestampFormatted)
                    put("severity", e.severity.name)
                    put("component", e.component.name)
                    put("name", e.eventName)
                    put("desc", e.description)
                })
            }
            put("events", eventsArr)
        }
    }

    /**
     * Saves both TXT and JSON reports to disk and returns a share intent for the TXT file.
     */
    fun exportReportFiles(
        context: Context,
        tracker: VoiceBotDiagnosticTracker
    ): Pair<File, File> {
        val dir = File(context.filesDir, "diagnostic_reports").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

        val txtFile = File(dir, "voice_bot_diagnostic_$timeStamp.txt")
        val jsonFile = File(dir, "voice_bot_diagnostic_$timeStamp.json")

        val textContent = buildTextReport(context, tracker)
        val jsonContent = buildJsonReport(context, tracker).toString(2)

        txtFile.writeText(textContent)
        jsonFile.writeText(jsonContent)

        return Pair(txtFile, jsonFile)
    }

    /**
     * Creates an Android share Intent for the exported report.
     */
    fun createShareIntent(context: Context, reportFile: File): Intent {
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", reportFile)
        } catch (_: Throwable) {
            android.net.Uri.fromFile(reportFile)
        }

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Voice & Bot Diagnostic Report")
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Attached is the diagnostic report from the Voice & Bot Diagnostic Center.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Copies the full plain-text diagnostic report to the Android system clipboard.
     * Returns true if successful.
     */
    fun copyReportToClipboard(context: Context, tracker: VoiceBotDiagnosticTracker): Boolean {
        return try {
            val text = buildTextReport(context, tracker)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                ?: return false
            val clip = android.content.ClipData.newPlainText("Voice & Bot Diagnostic Report", text)
            clipboard.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            android.util.Log.e("ReportExporter", "Failed to copy report to clipboard", e)
            false
        }
    }

    /**
     * Writes either plain text or JSON report content directly to a user-selected SAF Uri.
     */
    fun writeReportToUri(context: Context, uri: android.net.Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                os.write(content.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("ReportExporter", "Failed to write report to Uri: $uri", e)
            false
        }
    }

    /**
     * Generates a sensible default filename for saving reports via SAF.
     */
    fun generateDefaultFilename(format: String = "txt"): String {
        val timeStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
        return "TaskScheduling_Diagnostic_$timeStamp.$format"
    }
}
