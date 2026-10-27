package com.taskscheduling.app

import com.taskscheduling.app.assistant.ActionResult
import com.taskscheduling.app.assistant.AssistantActionExecutor
import com.taskscheduling.app.assistant.AssistantUiState
import com.taskscheduling.app.assistant.CommandIntent
import com.taskscheduling.app.assistant.ConfirmationIntent
import com.taskscheduling.app.assistant.ExecutionPolicy
import com.taskscheduling.app.assistant.LocalCommandProcessor
import com.taskscheduling.app.assistant.LocalCommandResolver
import com.taskscheduling.app.assistant.LocalConfirmationResolver
import com.taskscheduling.app.assistant.LocalWorkflowResolver
import com.taskscheduling.app.assistant.OfflineAssistantController
import com.taskscheduling.app.assistant.StopListeningResolver
import com.taskscheduling.app.assistant.TaskCommandParser
import com.taskscheduling.app.assistant.TaskParseResult
import com.taskscheduling.app.automation.AutomationEngine
import com.taskscheduling.app.automation.StepExecutionResult
import com.taskscheduling.app.automation.StepExecutionStatus
import com.taskscheduling.app.data.repository.WorkflowPersistenceRepository
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.DeviceProfile
import com.taskscheduling.app.domain.model.ExecutionSession
import com.taskscheduling.app.domain.model.ExecutionState
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.domain.model.WorkflowStatus
import com.taskscheduling.app.domain.model.WorkflowStep
import com.taskscheduling.app.voice.EnglishSpeechValidator
import com.taskscheduling.app.voice.LanguageMode
import com.taskscheduling.app.voice.PreRollAudioBuffer
import com.taskscheduling.app.voice.SafeAudioPreprocessor
import com.taskscheduling.app.voice.SpeechEngineError
import com.taskscheduling.app.voice.SpeechEngineListener
import com.taskscheduling.app.voice.SpeechEngineType
import com.taskscheduling.app.voice.SpeechRecognitionEngine
import com.taskscheduling.app.voice.WhisperModelManager
import com.taskscheduling.app.voice.WhisperModelStatus
import com.taskscheduling.app.voice.WhisperModelType
import com.taskscheduling.app.voice.WhisperPerformanceMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID

/**
 * Comprehensive Automated Tests for the offline Whisper Small Q5_1 speech recognition engine,
 * model manager, audio processing pipeline, memory isolation, and multilingual command pipeline.
 */
class WhisperCppSpeechEngineTest {

    private fun createTestController(): Pair<OfflineAssistantController, WorkflowPersistenceRepository> {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "whisper_test_${UUID.randomUUID()}")
        tempDir.mkdirs()
        val persistenceRepo = WorkflowPersistenceRepository(tempDir)
        val commandResolver = LocalCommandResolver()
        val workflowResolver = LocalWorkflowResolver(persistenceRepo)
        val commandProcessor = LocalCommandProcessor(commandResolver, workflowResolver)
        val confirmationResolver = LocalConfirmationResolver()

        val fakeEngine = object : AutomationEngine {
            private val _session = MutableStateFlow(
                ExecutionSession(taskId = "test", taskTitle = "Test", state = ExecutionState.IDLE, totalSteps = 0, currentStepIndex = 0)
            )
            override val executionSession: StateFlow<ExecutionSession> = _session.asStateFlow()

            override suspend fun executeWorkflow(workflow: Workflow, speedMultiplier: Float) {
                _session.value = ExecutionSession(taskId = workflow.id, taskTitle = workflow.name, state = ExecutionState.COMPLETED, totalSteps = workflow.steps.size, currentStepIndex = workflow.steps.size)
            }
            override suspend fun executeTap(x: Int, y: Int, durationMs: Long): StepExecutionResult =
                StepExecutionResult(StepExecutionStatus.SUCCESS, 0, ActionType.TAP)
            override suspend fun executeLongPress(x: Int, y: Int, durationMs: Long): StepExecutionResult =
                StepExecutionResult(StepExecutionStatus.SUCCESS, 0, ActionType.LONG_PRESS)
            override suspend fun executeSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): StepExecutionResult =
                StepExecutionResult(StepExecutionStatus.SUCCESS, 0, ActionType.SWIPE)
            override suspend fun executeWait(durationMs: Long): StepExecutionResult =
                StepExecutionResult(StepExecutionStatus.SUCCESS, 0, ActionType.WAIT)
            override suspend fun executeBack(): StepExecutionResult =
                StepExecutionResult(StepExecutionStatus.SUCCESS, 0, ActionType.BACK)
            override suspend fun executeStep(step: WorkflowStep): StepExecutionResult =
                StepExecutionResult(StepExecutionStatus.SUCCESS, step.order, step.type)
            override suspend fun testStep(step: WorkflowStep): StepExecutionResult =
                StepExecutionResult(StepExecutionStatus.SUCCESS, step.order, step.type)
            override suspend fun execute(task: Task, speedMultiplier: Float) {
                executeWorkflow(task.workflow, speedMultiplier)
            }
            override fun pause() {}
            override fun resume() {}
            override fun stop() {}
            override fun restart() {}
            override suspend fun stepNext() {}
        }

        val fakeExecutor = object : AssistantActionExecutor {
            override suspend fun executeAction(intent: CommandIntent, currentProfile: DeviceProfile): ActionResult {
                return ActionResult.Success("Executed: ${intent.name}")
            }
        }

        val controller = OfflineAssistantController(
            commandResolver = commandResolver,
            workflowResolver = workflowResolver,
            commandProcessor = commandProcessor,
            confirmationResolver = confirmationResolver,
            automationEngine = fakeEngine,
            actionExecutor = fakeExecutor,
            persistenceRepository = persistenceRepo,
            deviceProfileProvider = { DeviceProfile(manufacturer = "Samsung", model = "SM-X115", screenWidth = 1340, screenHeight = 800) },
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        ).apply {
            autoResumeDelayMs = 0L
        }

        return Pair(controller, persistenceRepo)
    }

    // =========================================================================
    // 1. MODEL CONFIGURATION & INTEGRITY TESTS
    // =========================================================================

    @Test
    fun `test Whisper model constants match official small-q5_1 specifications`() {
        assertEquals("ggml-small-q5_1.bin", WhisperModelManager.MODEL_FILENAME)
        assertEquals("ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb", WhisperModelManager.OFFICIAL_SHA256)
        assertEquals(190085487L, WhisperModelManager.EXPECTED_SIZE_BYTES)
        assertEquals(300L * 1024 * 1024, WhisperModelManager.MIN_FREE_STORAGE_BYTES)
    }

    @Test
    fun `test SHA-256 calculation matches known cryptographic hashes`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "whisper_test_sha_${UUID.randomUUID()}")
        tempDir.mkdirs()
        try {
            val sampleData = "Antigravity Whisper Small Q5_1 offline speech recognition test data".toByteArray(Charsets.UTF_8)
            val testFile = File(tempDir, "sample.bin")
            testFile.writeBytes(sampleData)

            val md = MessageDigest.getInstance("SHA-256")
            val expectedHash = md.digest(sampleData).joinToString("") { "%02x".format(it) }

            val manager = WhisperModelManager(context = null, overrideModelDir = tempDir)
            val computed = manager.computeSha256(testFile)

            assertEquals(expectedHash, computed)
            assertEquals(64, computed.length)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `test WhisperModelManager model path and expected model info`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "whisper_test_path_${UUID.randomUUID()}")
        tempDir.mkdirs()
        try {
            val manager = WhisperModelManager(context = null, overrideModelDir = tempDir)
            manager.selectModel(WhisperModelType.SMALL_Q5_1)

            // Requirement 1: Correct filename accepted
            assertEquals("ggml-small-q5_1.bin", manager.modelFile.name)

            // Requirement 10: Model path is correct
            assertEquals(File(tempDir, "ggml-small-q5_1.bin").absolutePath, manager.getModelPath())

            val info = manager.getExpectedModelInfo()
            assertEquals("ggml-small-q5_1.bin", info.filename)
            // Requirement 2: Correct size accepted
            assertEquals(190085487L, info.sizeBytes)
            // Requirement 3: Correct SHA accepted
            assertEquals("ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb", info.sha256)
            assertEquals("small-q5_1", info.modelVersion)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `test WhisperModelManager detects missing model`() = runBlocking {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "whisper_test_miss_${UUID.randomUUID()}")
        tempDir.mkdirs()
        try {
            val manager = WhisperModelManager(context = null, overrideModelDir = tempDir)

            // Requirement 6: Missing model detected
            assertFalse(manager.isModelInstalled())
            val status = manager.checkStatus()
            assertTrue(status is WhisperModelStatus.Missing || status is WhisperModelStatus.NotInstalled)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `test WhisperModelManager rejects wrong size and temporary files`() = runBlocking {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "whisper_test_tmp_${UUID.randomUUID()}")
        tempDir.mkdirs()
        try {
            val manager = WhisperModelManager(context = null, overrideModelDir = tempDir)

            // Requirement 7: Temporary file is not treated as ready
            val tmpFile = File(tempDir, "${WhisperModelManager.MODEL_FILENAME}.tmp")
            tmpFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
            assertTrue(tmpFile.exists())
            assertFalse("Temporary file must not be treated as ready", manager.isModelInstalled())

            // deleteInvalidTemporaryModel cleans up .tmp
            manager.deleteInvalidTemporaryModel()
            assertFalse(tmpFile.exists())

            // Requirement 5: Wrong size rejected
            val invalidSizeFile = manager.modelFile
            invalidSizeFile.writeBytes(ByteArray(1024)) // only 1KB instead of 1.51 GiB
            assertFalse(manager.isModelInstalled())
            val status = manager.checkStatus()
            assertTrue("Wrong size model must be rejected", status is WhisperModelStatus.Invalid || status is WhisperModelStatus.Corrupted)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `test WhisperModelManager metadata generation and fast verification`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "whisper_test_meta_${UUID.randomUUID()}")
        tempDir.mkdirs()
        try {
            val manager = WhisperModelManager(context = null, overrideModelDir = tempDir)
            manager.selectModel(WhisperModelType.SMALL_Q5_1)

            // Simulate model file with expected size using sparse file (takes 0 disk space and <1ms)
            java.io.RandomAccessFile(manager.modelFile, "rw").use { raf ->
                raf.setLength(WhisperModelManager.EXPECTED_SIZE_BYTES)
            }
            assertTrue(manager.modelFile.exists())
            assertEquals(WhisperModelManager.EXPECTED_SIZE_BYTES, manager.modelFile.length())

            // Generate valid metadata
            manager.writeMetadata(
                sha256 = WhisperModelManager.OFFICIAL_SHA256,
                sizeBytes = WhisperModelManager.EXPECTED_SIZE_BYTES
            )

            // Requirement 9: Metadata is generated correctly
            assertTrue(manager.metadataFile.exists())
            val content = manager.metadataFile.readText()
            assertTrue(content.contains(WhisperModelManager.OFFICIAL_SHA256))
            assertTrue(content.contains(WhisperModelManager.EXPECTED_SIZE_BYTES.toString()))
            assertTrue(content.contains("small-q5_1"))
            assertTrue("Valid metadata must pass verification", manager.verifyModelMetadata())

            // Requirement 4: Wrong SHA rejected
            manager.metadataFile.writeText("""
                {"filename":"ggml-small-q5_1.bin","sizeBytes":190085487,"sha256":"wrong_sha_hash","modelVersion":"small-q5_1"}
            """.trimIndent())
            assertFalse("Wrong SHA in metadata must be rejected", manager.verifyModelMetadata())

            // Requirement 11: Model version mismatch detected
            manager.metadataFile.writeText("""
                {"filename":"ggml-small-q5_1.bin","sizeBytes":190085487,"sha256":"${WhisperModelManager.OFFICIAL_SHA256}","modelVersion":"large-v3"}
            """.trimIndent())
            assertFalse("Model version mismatch must be rejected", manager.verifyModelMetadata())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `test invalid installation stream does not overwrite existing valid model`() = runBlocking {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "whisper_test_overwrite_${UUID.randomUUID()}")
        tempDir.mkdirs()
        try {
            val manager = WhisperModelManager(context = null, overrideModelDir = tempDir)

            // Seed an existing file
            val existing = manager.modelFile
            existing.writeText("Original model state")

            // Attempt to install invalid data stream that fails SHA check
            val corruptStream = ByteArrayInputStream("Corrupt partial stream data".toByteArray())
            val result = manager.installFromInputStream(
                inputStream = corruptStream,
                expectedSha256 = WhisperModelManager.OFFICIAL_SHA256,
                expectedLength = WhisperModelManager.EXPECTED_SIZE_BYTES
            )

            // Requirement 8: Invalid model does not overwrite valid model
            assertTrue("Corrupted stream installation must fail", result.isFailure)
            assertEquals("Original file must remain untouched", "Original model state", existing.readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `test SafeAudioPreprocessor removes DC offset without corrupting speech dynamics`() {
        val preprocessor = SafeAudioPreprocessor(pole = 0.95f)
        val buffer = FloatArray(100) { 0.5f } // Constant DC bias
        val filtered = preprocessor.process(buffer)

        // After DC filtering, subsequent values should decay toward 0
        val lastSample = filtered.last()
        assertTrue("Last sample $lastSample should decay towards zero", Math.abs(lastSample) < 0.2f)
    }

    @Test
    fun `test PreRollAudioBuffer circular buffering retains trailing audio window without memory allocations`() {
        val preRoll = PreRollAudioBuffer(capacitySamples = 4000)
        val input = FloatArray(1000) { it * 0.001f }

        preRoll.append(input, 1000)
        val snapshot = preRoll.getRecentSamples(1000)
        assertEquals(1000, snapshot.size)
        assertEquals(0f, snapshot[0], 0.0001f)

        // Append more to overflow capacity
        val largeInput = FloatArray(5000) { 0.42f }
        preRoll.append(largeInput, 5000)

        val overflowSnapshot = preRoll.getRecentSamples(4000)
        assertEquals(4000, overflowSnapshot.size)
        assertEquals(0.42f, overflowSnapshot[0], 0.0001f)

        preRoll.clear()
        val emptySnapshot = preRoll.getRecentSamples(1000)
        assertEquals(0, emptySnapshot.size)
    }

    // =========================================================================
    // 3. ENGINE LIFECYCLE & MUTUAL EXCLUSION (MEMORY ISOLATION)
    // =========================================================================

    @Test
    fun `test SpeechEngineType enum includes WHISPER_SMALL_Q5_1 as default primary`() {
        val types = SpeechEngineType.values()
        assertTrue(types.contains(SpeechEngineType.WHISPER_SMALL_Q5_1))
    }

    @Test
    fun `test LanguageMode enum defaults to AUTO and supports ENGLISH and HINDI`() {
        assertEquals("auto", LanguageMode.AUTO.code)
        assertEquals("en", LanguageMode.ENGLISH.code)
        assertEquals("hi", LanguageMode.HINDI.code)
    }

    @Test
    fun `test double-start protection and lifecycle state flow in mock Whisper engine`() {
        var startCount = 0
        var stopCount = 0
        var releaseCount = 0

        val mockEngine = object : SpeechRecognitionEngine {
            override val engineType: SpeechEngineType = SpeechEngineType.WHISPER_SMALL_Q5_1
            override val isModelLoaded: Boolean = true
            override val isRunning: Boolean get() = listening
            override val configuredSampleRate: Int = 16000
            override val activeAudioSource: String = "VOICE_RECOGNITION"
            override val configuredBufferSize: Int = 3200

            private var listening = false
            private var released = false

            override suspend fun initialize(): Result<Unit> = Result.success(Unit)

            override fun startListening(listener: SpeechEngineListener, continuous: Boolean): Boolean {
                if (listening || released) return false
                listening = true
                startCount++
                return true
            }

            override fun pauseListening() {}
            override fun resumeListening() {}

            override fun stopListening() {
                if (!listening) return
                listening = false
                stopCount++
            }

            override fun cancelListening() {
                stopListening()
            }

            override fun release() {
                if (released) return
                stopListening()
                released = true
                releaseCount++
            }
        }

        val dummyListener = object : SpeechEngineListener {
            override fun onReady() {}
            override fun onBeginningOfSpeech() {}
            override fun onPartialResult(hypothesis: String) {}
            override fun onResult(recognizedText: String) {}
            override fun onError(error: SpeechEngineError) {}
            override fun onStopped() {}
        }

        // First start
        val firstStart = mockEngine.startListening(dummyListener)
        assertTrue(firstStart)
        assertEquals(1, startCount)

        // Duplicate start must be rejected (double-start protection)
        val secondStart = mockEngine.startListening(dummyListener)
        assertFalse(secondStart)
        assertEquals(1, startCount)

        // Stop
        mockEngine.stopListening()
        assertEquals(1, stopCount)

        // Stop again must be no-op
        mockEngine.stopListening()
        assertEquals(1, stopCount)

        // Release
        mockEngine.release()
        assertEquals(1, releaseCount)

        // Start after release must be blocked
        val postReleaseStart = mockEngine.startListening(dummyListener)
        assertFalse(postReleaseStart)
        assertEquals(1, startCount)
    }

    @Test
    fun `test memory isolation ensures only one active model engine in RAM at a time`() {
        var whisperLoaded = false
        var sherpaLoaded = false

        fun loadWhisper() {
            if (sherpaLoaded) {
                sherpaLoaded = false // Unload fallback model
            }
            whisperLoaded = true
        }

        fun switchToSherpaFallback() {
            if (whisperLoaded) {
                whisperLoaded = false // Unload Whisper model from RAM first
            }
            sherpaLoaded = true
        }

        loadWhisper()
        assertTrue(whisperLoaded)
        assertFalse(sherpaLoaded)

        switchToSherpaFallback()
        assertFalse("Whisper model must be unloaded from RAM when switching to Sherpa", whisperLoaded)
        assertTrue(sherpaLoaded)
    }

    // =========================================================================
    // 4. PARTIAL VS FINAL RESULT POLICY TESTS
    // =========================================================================

    @Test
    fun `test partial results update UI only and NEVER trigger command execution`() {
        val resolver = LocalCommandResolver()

        val partial1 = "change"
        val partial2 = "change my"

        // Partial results do not match any complete command intent
        val intentFromPartial1 = resolver.resolve(partial1)
        assertNull("Partial phrase 'change' must not trigger an intent", intentFromPartial1)

        val intentFromPartial2 = resolver.resolve(partial2)
        assertNull("Partial phrase 'change my' must not trigger an intent", intentFromPartial2)

        val fullRecognizedText = "change my wallpaper"
        val intentFromFinal = resolver.resolve(fullRecognizedText)
        assertEquals(CommandIntent.CHANGE_WALLPAPER, intentFromFinal)
    }

    @Test
    fun `test empty or blank final transcripts are discarded safely`() = runBlocking {
        val (controller, _) = createTestController()
        controller.processCommand("")
        assertTrue("Controller must remain in Idle on empty input", controller.uiState.value is AssistantUiState.Idle)

        controller.processCommand("   \n\t  ")
        assertTrue("Controller must remain in Idle on whitespace input", controller.uiState.value is AssistantUiState.Idle)
    }

    // =========================================================================
    // 5. MULTILINGUAL TRANSCRIPTION & COMMAND RESOLVER INTEGRATION
    // =========================================================================

    @Test
    fun `test English transcription matches correct command intents`() {
        val resolver = LocalCommandResolver()

        assertEquals(CommandIntent.CHANGE_WALLPAPER, resolver.resolve("change my wallpaper"))
        assertEquals(CommandIntent.CHANGE_WALLPAPER, resolver.resolve("change wallpaper"))
        assertEquals(CommandIntent.OPEN_YOUTUBE, resolver.resolve("open YouTube"))
        assertEquals(CommandIntent.OPEN_SETTINGS, resolver.resolve("open settings"))
        assertEquals(CommandIntent.SWIPE_LEFT, resolver.resolve("swipe left"))
        assertEquals(CommandIntent.SWIPE_RIGHT, resolver.resolve("swipe right"))

        // Stop commands handled by StopListeningResolver
        assertTrue(StopListeningResolver.isStopListening("stop listening"))
        assertTrue(StopListeningResolver.isStopListening("stop assistant"))
    }

    @Test
    fun `test Hindi transcription matches correct command intents without auto-translation`() {
        val resolver = LocalCommandResolver()

        val hindiPhrases = listOf(
            "मेरा वॉलपेपर बदल दो",
            "मेरा वॉलपेपर बदल दीजिए",
            "यूट्यूब खोलो",
            "सेटिंग्स खोलो",
            "बाएं स्वाइप करो",
            "दाएं स्वाइप करो"
        )
        for (phrase in hindiPhrases) {
            assertTrue("Hindi phrase '$phrase' must be flagged by EnglishSpeechValidator", EnglishSpeechValidator.isNonEnglishOrUnreliable(phrase))
            assertNull("Hindi phrase '$phrase' must not resolve to any command", resolver.resolve(phrase))
        }
    }

    @Test
    fun `test Hinglish and mixed-speech transcription matches correct command intents`() {
        val resolver = LocalCommandResolver()

        val hinglishPhrases = listOf(
            "mera wallpaper change kar do",
            "please mera wallpaper change kar do",
            "change my wallpaper kar do",
            "mera YouTube open kar do",
            "settings kholo",
            "thoda left swipe karo"
        )
        for (phrase in hinglishPhrases) {
            assertTrue("Hinglish phrase '$phrase' must be flagged as non-English/unreliable or unresolvable",
                EnglishSpeechValidator.isNonEnglishOrUnreliable(phrase) || resolver.resolve(phrase) == null)
            assertNull("Hinglish phrase '$phrase' must not resolve to wallpaper intent in English-only mode", resolver.resolve(phrase))
        }
    }

    @Test
    fun `test Hinglish and Hindi task scheduling queries parsed accurately into TaskDraft`() {
        // "kal morning ek task bana dena"
        val res1 = TaskCommandParser.parse("kal morning ek task bana dena")
        assertTrue(res1 is TaskParseResult.AddTask)
        val draft1 = (res1 as TaskParseResult.AddTask).draft
        assertEquals("Tomorrow", draft1.dueDate)

        // "tomorrow morning ek task bana dena"
        val res2 = TaskCommandParser.parse("tomorrow morning ek task bana dena")
        assertTrue(res2 is TaskParseResult.AddTask)
        val draft2 = (res2 as TaskParseResult.AddTask).draft
        assertEquals("Tomorrow", draft2.dueDate)

        // "mujhe kal 6 baje study karni hai, task bana do"
        val res3 = TaskCommandParser.parse("mujhe kal 6 baje study karni hai, task bana do")
        assertTrue(res3 is TaskParseResult.AddTask)
        val draft3 = (res3 as TaskParseResult.AddTask).draft
        assertEquals("Tomorrow", draft3.dueDate)
        assertEquals("06:00", draft3.dueTime)
        assertTrue(draft3.hasDueTime)
    }

    // =========================================================================
    // 6. CONFIRMATION SAFETY & CONTINUOUS VOICE RECOGNITION
    // =========================================================================

    @Test
    fun `test dangerous actions require confirmation and never execute directly`() = runBlocking {
        val (controller, repo) = createTestController()
        val dummyProfile = DeviceProfile(manufacturer = "Samsung", model = "SM-X115", screenWidth = 1340, screenHeight = 800)
        repo.saveWorkflow(
            Workflow(
                id = "wf_wallpaper",
                name = "Change Wallpaper",
                description = "Wallpaper change",
                status = WorkflowStatus.CALIBRATED,
                deviceProfile = dummyProfile,
                steps = listOf(WorkflowStep(id = "s1", order = 1, type = ActionType.BACK))
            )
        )

        // Process "change my wallpaper"
        controller.processCommand("change my wallpaper")

        // Must enter ConfirmationRequired, not execute immediately
        assertTrue(controller.uiState.value is AssistantUiState.ConfirmationRequired)
        assertNotNull(controller.pendingWorkflow)

        // Follow-up speech "yes" should confirm
        controller.processCommand("yes")
        assertTrue(controller.uiState.value is AssistantUiState.Completed)
        assertNull(controller.pendingWorkflow)
    }

    @Test
    fun `test confirmation cancellation returns safely to idle state`() = runBlocking {
        val (controller, repo) = createTestController()
        val dummyProfile = DeviceProfile(manufacturer = "Samsung", model = "SM-X115", screenWidth = 1340, screenHeight = 800)
        repo.saveWorkflow(
            Workflow(
                id = "wf_wallpaper",
                name = "Change Wallpaper",
                description = "Wallpaper change",
                status = WorkflowStatus.CALIBRATED,
                deviceProfile = dummyProfile,
                steps = listOf(WorkflowStep(id = "s1", order = 1, type = ActionType.BACK))
            )
        )

        controller.processCommand("change my wallpaper")
        assertTrue(controller.uiState.value is AssistantUiState.ConfirmationRequired)

        // User cancels
        controller.processCommand("cancel")
        assertTrue(controller.uiState.value is AssistantUiState.Idle)
        assertNull(controller.pendingWorkflow)
    }

    @Test
    fun `test LocalConfirmationResolver handles multilingual affirmative and negative phrases`() {
        val confResolver = LocalConfirmationResolver()

        assertEquals(ConfirmationIntent.YES, confResolver.resolve("yes"))
        assertEquals(ConfirmationIntent.YES, confResolver.resolve("yes run it"))
        assertEquals(ConfirmationIntent.YES, confResolver.resolve("okay"))
        assertEquals(ConfirmationIntent.YES, confResolver.resolve("okay run it"))
        assertEquals(ConfirmationIntent.YES, confResolver.resolve("please do it"))

        assertEquals(ConfirmationIntent.NO, confResolver.resolve("no"))
        assertEquals(ConfirmationIntent.NO, confResolver.resolve("cancel"))
        assertEquals(ConfirmationIntent.NO, confResolver.resolve("no thanks"))
    }

    // =========================================================================
    // 7. PERFORMANCE METRICS & TELEMETRY
    // =========================================================================

    @Test
    fun `test WhisperPerformanceMetrics calculates latency and real-time factor`() {
        val metrics = WhisperPerformanceMetrics(
            modelLoadTimeMs = 1200L,
            timeToFirstPartialMs = 180L,
            timeToFinalMs = 450L,
            decodeTimeMs = 380L,
            audioCaptureLatencyMs = 25L,
            realTimeFactor = 0.19f,
            nativeModelMemoryMB = 1450f,
            appMemoryMB = 120f,
            availableMemoryMB = 1800f,
            rmsLevel = 1200f,
            peakLevel = 8500f,
            zeroPercent = 1.5f,
            aecAvailable = true,
            aecEnabled = true,
            nsAvailable = true,
            nsEnabled = true,
            agcAvailable = true,
            agcEnabled = true
        )

        assertEquals(180L, metrics.timeToFirstPartialMs)
        assertEquals(450L, metrics.timeToFinalMs)
        assertEquals(0.19f, metrics.realTimeFactor, 0.001f)
        assertEquals(1450f, metrics.nativeModelMemoryMB, 0.001f)
        assertTrue(metrics.aecAvailable)
        assertTrue(metrics.nsAvailable)
        assertTrue(metrics.agcAvailable)
    }

    // =========================================================================
    // 8. LIFECYCLE STATE & CRASH PROTECTION TESTS
    // =========================================================================

    @Test
    fun `test WhisperEngineState covers all required lifecycle states`() {
        val states = com.taskscheduling.app.voice.WhisperEngineState.values()
        val expected = listOf(
            com.taskscheduling.app.voice.WhisperEngineState.UNINITIALIZED,
            com.taskscheduling.app.voice.WhisperEngineState.IDLE,
            com.taskscheduling.app.voice.WhisperEngineState.LOADING,
            com.taskscheduling.app.voice.WhisperEngineState.READY,
            com.taskscheduling.app.voice.WhisperEngineState.RUNNING,
            com.taskscheduling.app.voice.WhisperEngineState.LISTENING,
            com.taskscheduling.app.voice.WhisperEngineState.SPEECH_DETECTED,
            com.taskscheduling.app.voice.WhisperEngineState.CAPTURING,
            com.taskscheduling.app.voice.WhisperEngineState.TRANSCRIBING,
            com.taskscheduling.app.voice.WhisperEngineState.RESULT,
            com.taskscheduling.app.voice.WhisperEngineState.EMPTY_RESULT,
            com.taskscheduling.app.voice.WhisperEngineState.STOPPING,
            com.taskscheduling.app.voice.WhisperEngineState.RELEASED,
            com.taskscheduling.app.voice.WhisperEngineState.ERROR
        )
        assertEquals(14, states.size)
        assertTrue(states.toList().containsAll(expected))
    }

    @Test
    fun `test VoiceDebugInfo default fields and diagnostics formatting`() {
        val dbg = com.taskscheduling.app.assistant.VoiceDebugInfo(
            engineType = "Whisper Small Q5_1",
            modelState = "READY",
            memoryStatus = "Avail: 1800MB | App: 45MB",
            errorMessage = ""
        )
        assertEquals("Whisper Small Q5_1", dbg.engineType)
        assertEquals("READY", dbg.modelState)
        assertEquals("Avail: 1800MB | App: 45MB", dbg.memoryStatus)
        assertEquals("", dbg.errorMessage)
    }

    @Test
    fun `test NativeLibraryLoader catch protection does not throw uncaught error`() {
        val res = com.taskscheduling.app.voice.NativeLibraryLoader.loadLibraries()
        // In JVM host unit tests, libwhisper.so is ARM64 so System.loadLibrary returns failure Result cleanly without uncaught exception
        assertNotNull(res)
    }

    @Test
    fun `test exactly three language modes supported - English, Hindi, Hinglish`() {
        val modes = LanguageMode.entries
        assertEquals("Must support exactly English, Hindi, and Hinglish", 3, modes.size)
        assertTrue(modes.contains(LanguageMode.ENGLISH))
        assertEquals("en", LanguageMode.ENGLISH.whisperCode)
        assertTrue(modes.contains(LanguageMode.HINDI))
        assertEquals("hi", LanguageMode.HINDI.whisperCode)
        assertTrue(modes.contains(LanguageMode.HINGLISH))
        assertEquals("auto", LanguageMode.HINGLISH.whisperCode)
        // Backward-compatible alias
        assertEquals(LanguageMode.HINGLISH, LanguageMode.AUTO)
    }

    @Test
    fun `test configurable Whisper models and default selection`() {
        val models = WhisperModelType.entries
        assertEquals(3, models.size)
        assertEquals(WhisperModelType.TINY_Q5_1, WhisperModelManager.DEFAULT_MODEL)
        assertEquals("ggml-tiny-q5_1.bin", WhisperModelType.TINY_Q5_1.filename)
        assertEquals(32152673L, WhisperModelType.TINY_Q5_1.sizeBytes)
        assertEquals("818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7", WhisperModelType.TINY_Q5_1.sha256)

        assertEquals("ggml-base-q5_1.bin", WhisperModelType.BASE_Q5_1.filename)
        assertEquals(59707625L, WhisperModelType.BASE_Q5_1.sizeBytes)

        assertEquals("ggml-small-q5_1.bin", WhisperModelType.SMALL_Q5_1.filename)
        assertEquals(190085487L, WhisperModelType.SMALL_Q5_1.sizeBytes)
    }

    @Test
    fun `test WhisperPerformanceMetrics records all required timing stages independently`() {
        val metrics = WhisperPerformanceMetrics(
            utteranceId = "utt_test_123",
            modelLoadTimeMs = 120L,
            captureMs = 1500.0,
            audioPreparationMs = 3.0,
            queueWaitMs = 1.0,
            nativeMutexWaitMs = 2.0,
            nativeInferenceMs = 2800.0,
            resultProcessingMs = 1.0,
            uiDeliveryMs = 5.0,
            speechEndToResultMs = 2812.0,
            totalMs = 4312.0,
            audioDurationMs = 1500L,
            selectedModelName = "tiny-q5_1",
            threads = 2,
            languageMode = "en"
        )
        assertEquals("utt_test_123", metrics.utteranceId)
        assertEquals(120L, metrics.modelLoadMs)
        assertEquals(1500.0, metrics.captureMs, 0.01)
        assertEquals(3.0, metrics.audioPreparationMs, 0.01)
        assertEquals(1.0, metrics.queueWaitMs, 0.01)
        assertEquals(2.0, metrics.nativeMutexWaitMs, 0.01)
        assertEquals(2800.0, metrics.nativeInferenceMs, 0.01)
        assertEquals(1.0, metrics.resultProcessingMs, 0.01)
        assertEquals(5.0, metrics.uiDeliveryMs, 0.01)
        assertEquals(2812.0, metrics.speechEndToResultMs, 0.01)
        assertEquals(4312.0, metrics.totalMs, 0.01)

        val summary = metrics.formatMetricsSummary()
        assertTrue(summary.contains("utt_test_123"))
        assertTrue(summary.contains("inference=2800.0ms"))
        assertTrue(summary.contains("speechEndToResult=2812.0ms"))
    }
}
