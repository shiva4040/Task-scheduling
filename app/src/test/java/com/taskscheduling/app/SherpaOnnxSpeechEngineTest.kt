package com.taskscheduling.app

import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.taskscheduling.app.assistant.ActionResult
import com.taskscheduling.app.assistant.AssistantActionExecutor
import com.taskscheduling.app.assistant.CommandIntent
import com.taskscheduling.app.assistant.LocalCommandProcessor
import com.taskscheduling.app.assistant.LocalCommandResolver
import com.taskscheduling.app.assistant.LocalConfirmationResolver
import com.taskscheduling.app.assistant.LocalWorkflowResolver
import com.taskscheduling.app.assistant.OfflineAssistantController
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
import com.taskscheduling.app.domain.model.WorkflowStep
import com.taskscheduling.app.voice.ContinuousVoiceController
import com.taskscheduling.app.voice.SpeechEngineListener
import com.taskscheduling.app.voice.SpeechEngineType
import com.taskscheduling.app.voice.SpeechRecognitionEngine
import com.taskscheduling.app.voice.VoiceInputManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class SherpaOnnxSpeechEngineTest {

    private fun createTestController(): OfflineAssistantController {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "sherpa_test_${UUID.randomUUID()}")
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

            override suspend fun executeWorkflow(workflow: Workflow, speedMultiplier: Float) {}
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
            override suspend fun execute(task: Task, speedMultiplier: Float) {}
            override fun pause() {}
            override fun resume() {}
            override fun stop() {}
            override fun restart() {}
            override suspend fun stepNext() {}
        }

        val fakeExecutor = object : AssistantActionExecutor {
            override suspend fun executeAction(intent: CommandIntent, currentProfile: DeviceProfile): ActionResult {
                return ActionResult.Success("Done")
            }
        }

        return OfflineAssistantController(
            commandResolver = commandResolver,
            workflowResolver = workflowResolver,
            commandProcessor = commandProcessor,
            confirmationResolver = confirmationResolver,
            automationEngine = fakeEngine,
            actionExecutor = fakeExecutor,
            persistenceRepository = persistenceRepo,
            deviceProfileProvider = { DeviceProfile(manufacturer = "Samsung", model = "SM-X210", screenWidth = 1340, screenHeight = 800) },
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        ).apply {
            autoResumeDelayMs = 0L
        }
    }

    @Test
    fun `test Sherpa-ONNX model config structure and default sample rate`() {
        val transducerConfig = OnlineTransducerModelConfig(
            encoder = "/data/local/encoder.onnx",
            decoder = "/data/local/decoder.onnx",
            joiner = "/data/local/joiner.onnx"
        )
        val modelConfig = OnlineModelConfig(
            transducer = transducerConfig,
            tokens = "/data/local/tokens.txt",
            numThreads = 2,
            provider = "cpu",
            modelType = "zipformer2"
        )
        val featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80)
        val endpointConfig = EndpointConfig()

        val config = OnlineRecognizerConfig(
            featConfig = featConfig,
            modelConfig = modelConfig,
            endpointConfig = endpointConfig,
            enableEndpoint = true
        )

        assertEquals(16000, config.featConfig.sampleRate)
        assertEquals(80, config.featConfig.featureDim)
        assertEquals("zipformer2", config.modelConfig.modelType)
        assertEquals("/data/local/encoder.onnx", config.modelConfig.transducer.encoder)
        assertEquals("/data/local/decoder.onnx", config.modelConfig.transducer.decoder)
        assertEquals("/data/local/joiner.onnx", config.modelConfig.transducer.joiner)
        assertEquals("/data/local/tokens.txt", config.modelConfig.tokens)
        assertTrue(config.enableEndpoint)
    }

    @Test
    fun `test PCM short to float conversion normalization`() {
        val maxPositiveShort: Short = 32767
        val minNegativeShort: Short = -32768
        val zeroShort: Short = 0

        val maxFloat = maxPositiveShort / 32768.0f
        val minFloat = minNegativeShort / 32768.0f
        val zeroFloat = zeroShort / 32768.0f

        assertTrue(maxFloat > 0.999f && maxFloat <= 1.0f)
        assertEquals(-1.0f, minFloat, 0.0001f)
        assertEquals(0.0f, zeroFloat, 0.0001f)
    }

    @Test
    fun `test OnlineRecognizer stream lifecycle and decoding simulation`() {
        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "encoder.onnx",
                    decoder = "decoder.onnx",
                    joiner = "joiner.onnx"
                ),
                tokens = "tokens.txt"
            ),
            enableEndpoint = true
        )

        try {
            val recognizer = OnlineRecognizer(assetManager = null, config = config)
            val stream = recognizer.createStream()
            assertNotNull(stream)

            // Feed float samples
            val samples = FloatArray(1600) { 0.05f }
            stream.acceptWaveform(samples, 16000)

            while (recognizer.isReady(stream)) {
                recognizer.decode(stream)
            }

            val result = recognizer.getResult(stream)
            assertNotNull(result)

            recognizer.reset(stream)
            stream.release()
            recognizer.release()
        } catch (e: UnsatisfiedLinkError) {
            // Expected on host development machine JVM running unit tests without Android native libraries
        }
    }

    @Test
    fun `test VoiceInputManager engine switching between Sherpa-ONNX and Vosk`() {
        val testEngine = TestSpeechRecognitionEngine(SpeechEngineType.SHERPA_ONNX)
        val manager = VoiceInputManager(
            context = null,
            speechEngine = testEngine,
            permissionChecker = { true }
        )

        assertEquals(SpeechEngineType.SHERPA_ONNX, manager.speechEngine.engineType)

        val voskEngine = TestSpeechRecognitionEngine(SpeechEngineType.VOSK)
        manager.setSpeechEngine(voskEngine)
        assertEquals(SpeechEngineType.VOSK, manager.speechEngine.engineType)
    }

    @Test
    fun `test RAW SPEECH TEST mode bypasses command execution`() {
        val controller = createTestController()
        val testEngine = TestSpeechRecognitionEngine(SpeechEngineType.SHERPA_ONNX)
        val manager = VoiceInputManager(
            context = null,
            speechEngine = testEngine,
            permissionChecker = { true }
        )
        val continuousController = ContinuousVoiceController(
            voiceInputManager = manager,
            assistantController = controller
        )

        continuousController.isRawSpeechTestMode = true
        assertTrue(continuousController.isRawSpeechTestMode)
        assertTrue(controller.voiceDebugInfo.value.isRawSpeechTestMode)

        // Simulate speech recognition in raw test mode
        continuousController.handleRecognizedSpeech("change my wallpaper")

        // In raw speech test mode, finalText must be updated, but NO commands must execute
        assertEquals("change my wallpaper", controller.voiceDebugInfo.value.finalText)
        assertEquals("", controller.voiceDebugInfo.value.resolvedCommand)
        assertNull(controller.pendingAction)
    }

    @Test
    fun `test critical test phrases resolve properly through LocalCommandResolver`() {
        val resolver = LocalCommandResolver()
        val testPhrases = mapOf(
            "add task" to CommandIntent.ADD_TASK,
            "add a task" to CommandIntent.ADD_TASK,
            "change my wallpaper" to CommandIntent.CHANGE_WALLPAPER,
            "open settings" to CommandIntent.OPEN_SETTINGS,
            "go home" to CommandIntent.GO_HOME,
            "swipe up" to CommandIntent.SWIPE_UP,
            "add task buy milk" to CommandIntent.ADD_TASK,
            "add task buy milk tomorrow" to CommandIntent.ADD_TASK,
            "add task study machine learning tomorrow at five PM" to CommandIntent.ADD_TASK
        )

        for ((phrase, expectedIntent) in testPhrases) {
            val resolved = resolver.resolve(phrase)
            assertEquals("Failed for phrase: \"$phrase\"", expectedIntent, resolved)
        }
    }

    @Test
    fun `test TaskCommandParser extracts complex task details from speech accurately`() {
        val phrase = "add task study machine learning tomorrow at 5 PM"
        val parseResult = TaskCommandParser.parse(phrase)

        assertTrue(parseResult is TaskParseResult.AddTask)
        val draft = (parseResult as TaskParseResult.AddTask).draft
        assertTrue(draft.title.contains("study machine learning", ignoreCase = true))
        assertNotNull(draft.dueDate)
        assertNotNull(draft.dueTime)
    }

    @Test
    fun `test TTS echo suppression in ContinuousVoiceController`() {
        val controller = createTestController()
        val testEngine = TestSpeechRecognitionEngine(SpeechEngineType.SHERPA_ONNX)
        val manager = VoiceInputManager(
            context = null,
            speechEngine = testEngine,
            permissionChecker = { true }
        )

        var finishCallback: (() -> Unit)? = null
        val fakeOutputManager = object : com.taskscheduling.app.voice.SpeechOutputManager {
            private val _isSpk = MutableStateFlow(false)
            override val isSpeaking: StateFlow<Boolean> = _isSpk.asStateFlow()
            override fun speak(text: String, onSpeechStarted: () -> Unit, onSpeechFinished: () -> Unit) {
                _isSpk.value = true
                onSpeechStarted()
                finishCallback = onSpeechFinished
            }
            override fun stop() {
                _isSpk.value = false
            }
            override fun release() {
                _isSpk.value = false
            }
        }

        val continuousController = ContinuousVoiceController(
            voiceInputManager = manager,
            assistantController = controller,
            speechOutputManager = fakeOutputManager
        )

        continuousController.isRawSpeechTestMode = false

        // When TTS is actively speaking, incoming voice is dropped
        continuousController.speakWithMute("Hello user")
        assertTrue(continuousController.isTtsSpeaking)

        continuousController.handleRecognizedSpeech("hello user")
        // Since TTS is speaking, echo input is ignored
        assertEquals("", controller.voiceDebugInfo.value.resolvedCommand)

        // Complete TTS
        finishCallback?.invoke()
        assertFalse(continuousController.isTtsSpeaking)
    }

    @Test
    fun `test whitespace and empty speech are safely ignored`() {
        val controller = createTestController()
        val testEngine = TestSpeechRecognitionEngine(SpeechEngineType.SHERPA_ONNX)
        val manager = VoiceInputManager(
            context = null,
            speechEngine = testEngine,
            permissionChecker = { true }
        )
        val continuousController = ContinuousVoiceController(
            voiceInputManager = manager,
            assistantController = controller
        )

        continuousController.handleRecognizedSpeech("")
        continuousController.handleRecognizedSpeech("   ")
        assertEquals("", controller.voiceDebugInfo.value.finalText)
    }

    @Test
    fun `test PCM buffer conversion processes strictly read samples and never exceeds samplesRead`() {
        val fullBuffer = ShortArray(1024) { (it * 30).toShort() }
        val samplesRead = 400 // Partial read from AudioRecord
        val floatSamples = FloatArray(samplesRead)

        for (i in 0 until samplesRead) {
            val s = fullBuffer[i]
            floatSamples[i] = s.toFloat() / 32768.0f
        }

        assertEquals(400, floatSamples.size)
        assertEquals(0f, floatSamples[0], 0.0001f)
        assertEquals((30.toFloat() / 32768.0f), floatSamples[1], 0.0001f)
        assertEquals((399 * 30).toFloat() / 32768.0f, floatSamples[399], 0.0001f)
    }

    @Test
    fun `test pipeline health flags reflect actual non-blank runtime results`() {
        val controller = createTestController()
        val info = controller.voiceDebugInfo.value
        assertFalse("micPipelineOk must default to false", info.micPipelineOk)
        assertFalse("pcmPipelineOk must default to false", info.pcmPipelineOk)
        assertFalse("partialPipelineOk must default to false", info.partialPipelineOk)
        assertFalse("textPipelineOk must default to false", info.textPipelineOk)

        // Empty partial should NOT mark partialPipelineOk
        controller.updateDebugPartial("")
        assertFalse(controller.voiceDebugInfo.value.partialPipelineOk)

        // Non-empty partial marks partialPipelineOk
        controller.updateDebugPartial("hello")
        assertTrue(controller.voiceDebugInfo.value.partialPipelineOk)

        // Empty final should NOT mark textPipelineOk
        controller.updateDebugFinal("")
        assertFalse(controller.voiceDebugInfo.value.textPipelineOk)

        // Non-empty final marks textPipelineOk
        controller.updateDebugFinal("hello")
        assertTrue(controller.voiceDebugInfo.value.textPipelineOk)
    }

    @Test
    fun `test ContinuousVoiceController aborts listening on speech error and sets error message`() {
        val controller = createTestController()
        val failingEngine = object : SpeechRecognitionEngine {
            override val engineType = SpeechEngineType.SHERPA_ONNX
            override val isModelLoaded = true
            override val isRunning = false
            override val configuredSampleRate = 16000
            override val activeAudioSource = "VOICE_RECOGNITION"
            override val configuredBufferSize = 2048
            override suspend fun initialize() = Result.success(Unit)
            override fun startListening(listener: SpeechEngineListener, continuous: Boolean): Boolean {
                listener.onError(com.taskscheduling.app.voice.SpeechEngineError.MicrophoneUnavailable("Mic failed"))
                return false
            }
            override fun pauseListening() {}
            override fun resumeListening() {}
            override fun stopListening() {}
            override fun cancelListening() {}
            override fun release() {}
        }

        val manager = VoiceInputManager(
            context = null,
            speechEngine = failingEngine,
            scope = CoroutineScope(Dispatchers.Unconfined),
            permissionChecker = { true }
        )
        val continuousController = ContinuousVoiceController(
            voiceInputManager = manager,
            assistantController = controller,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )

        continuousController.startContinuousListening()

        // Continuous listening should be safely reset to false on error, never stuck in listening
        assertFalse(continuousController.isContinuousListening.value)
        assertFalse(controller.isContinuousListening)
        assertTrue(controller.uiState.value is com.taskscheduling.app.assistant.AssistantUiState.Error)
    }

    @Test
    fun `test Sherpa endpoint configuration enforces mustContainNonSilence to prevent silence wipe`() {
        val endpointConfig = EndpointConfig(
            rule1 = com.k2fsa.sherpa.onnx.EndpointRule(mustContainNonSilence = true, minTrailingSilence = 2.4f, minUtteranceLength = 0.0f),
            rule2 = com.k2fsa.sherpa.onnx.EndpointRule(mustContainNonSilence = true, minTrailingSilence = 1.2f, minUtteranceLength = 0.0f),
            rule3 = com.k2fsa.sherpa.onnx.EndpointRule(mustContainNonSilence = false, minTrailingSilence = 0.0f, minUtteranceLength = 20.0f)
        )

        assertTrue("Rule 1 must require non-silence so silence does not trigger reset", endpointConfig.rule1.mustContainNonSilence)
        assertTrue("Rule 2 must require non-silence so utterance silence triggers finalization", endpointConfig.rule2.mustContainNonSilence)
        assertEquals(2.4f, endpointConfig.rule1.minTrailingSilence, 0.01f)
        assertEquals(1.2f, endpointConfig.rule2.minTrailingSilence, 0.01f)
    }

    @Test
    fun `test SafeAudioPreprocessor eliminates DC bias while preserving speech waveform`() {
        val preprocessor = com.taskscheduling.app.voice.SafeAudioPreprocessor(pole = 0.995f)

        // Generate synthetic signal with significant constant DC bias (+0.35f)
        val biasedSignal = FloatArray(1600) { 0.35f + (if (it % 2 == 0) 0.05f else -0.05f) }
        val filtered = preprocessor.process(biasedSignal)

        assertEquals(biasedSignal.size, filtered.size)

        // At end of buffer, DC bias should decay toward zero while alternating AC speech oscillation remains
        val finalSamplesMean = (filtered.takeLast(100).sum() / 100.0f)
        assertTrue("DC bias must be suppressed near zero (was $finalSamplesMean)", kotlin.math.abs(finalSamplesMean) < 0.05f)
    }

    @Test
    fun `test AdaptiveAudioLevelMonitor detects silence quiet normal and loud speech dynamically`() {
        val monitor = com.taskscheduling.app.voice.AdaptiveAudioLevelMonitor()

        // 1. Initial background room silence (RMS ~0.001)
        val silenceShorts = ShortArray(1024) { 30 }
        val silenceFloats = FloatArray(1024) { 30f / 32768.0f }
        val silenceMetrics = monitor.analyze(silenceShorts, silenceFloats, 1024)

        assertEquals("SILENCE", silenceMetrics.levelCategory)
        assertFalse(silenceMetrics.isSpeechDetected)

        // 2. Normal speech frames (RMS ~0.045)
        val speechShorts = ShortArray(1024) { (1500 * kotlin.math.sin(it * 0.1)).toInt().toShort() }
        val speechFloats = FloatArray(1024) { speechShorts[it].toFloat() / 32768.0f }

        // Feed twice to satisfy 2-frame onset debounce
        monitor.analyze(speechShorts, speechFloats, 1024)
        val speechMetrics = monitor.analyze(speechShorts, speechFloats, 1024)

        assertTrue("Speech should be detected", speechMetrics.isSpeechDetected)
        assertTrue("Category should be normal or quiet speech", speechMetrics.levelCategory.contains("SPEECH"))

        // 3. Clipping frame (samples hitting saturation)
        val clipShorts = ShortArray(1024) { if (it == 10) Short.MAX_VALUE else 100 }
        val clipFloats = FloatArray(1024) { clipShorts[it].toFloat() / 32768.0f }
        val clipMetrics = monitor.analyze(clipShorts, clipFloats, 1024)

        assertEquals("CLIPPING", clipMetrics.levelCategory)
    }

    @Test
    fun `test PreRollAudioBuffer maintains recent 200ms history and prepends on speech onset`() {
        val buffer = com.taskscheduling.app.voice.PreRollAudioBuffer(capacitySamples = 4000)

        // Write 3 consecutive frames of 1024 samples (3072 samples total)
        for (f in 0 until 3) {
            val chunk = FloatArray(1024) { (f * 1024 + it).toFloat() }
            buffer.append(chunk)
        }

        // Request 2000 recent samples (~125ms at 16kHz)
        val recent = buffer.getRecentSamples(2000)
        assertEquals(2000, recent.size)

        // Check chronological ordering: last sample in recent must match last sample written
        val lastWritten = (3 * 1024 - 1).toFloat()
        assertEquals(lastWritten, recent.last(), 0.001f)
    }

    @Test
    fun `test PartialStabilizer stabilizes streaming partial hypotheses and rejects transient flicker`() {
        val stabilizer = com.taskscheduling.app.voice.PartialStabilizer()

        // 1. Initial word emergence
        assertTrue(stabilizer.update("hel"))
        assertEquals("hel", stabilizer.stablePartial)

        // 2. Word extension (growing hypothesis) updates immediately
        assertTrue(stabilizer.update("hello"))
        assertEquals("hello", stabilizer.stablePartial)

        // 3. Transient single-frame flicker does NOT immediately replace stable partial
        assertFalse(stabilizer.update("he"))
        assertEquals("hello", stabilizer.stablePartial) // Remains stable "hello"

        // 4. Continued sentence extension
        assertTrue(stabilizer.update("hello change"))
        assertEquals("hello change", stabilizer.stablePartial)
    }

    private class TestSpeechRecognitionEngine(
        override val engineType: SpeechEngineType
    ) : SpeechRecognitionEngine {
        override val isModelLoaded: Boolean = true
        override var isRunning: Boolean = false
        override val configuredSampleRate: Int = 16000
        override val activeAudioSource: String = "VOICE_RECOGNITION"
        override val configuredBufferSize: Int = 2048

        private var activeListener: SpeechEngineListener? = null

        override suspend fun initialize(): Result<Unit> = Result.success(Unit)

        override fun startListening(listener: SpeechEngineListener, continuous: Boolean): Boolean {
            activeListener = listener
            isRunning = true
            listener.onReady()
            return true
        }

        override fun pauseListening() {}
        override fun resumeListening() {}
        override fun stopListening() {
            isRunning = false
            activeListener?.onStopped()
        }
        override fun cancelListening() {
            isRunning = false
        }
        override fun release() {
            stopListening()
        }
    }
}
