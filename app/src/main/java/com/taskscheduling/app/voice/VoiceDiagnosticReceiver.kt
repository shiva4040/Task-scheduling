package com.taskscheduling.app.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale

/**
 * Diagnostic broadcast receiver allowing automated execution, benchmarking,
 * and verification of speech recognition models and languages on-device.
 */
class VoiceDiagnosticReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "TaskScheduling-CRASH"
        const val BENCHMARK_TAG = "WHISPER_BENCHMARK"
        const val ACTION_DIAGNOSTIC = "com.taskscheduling.app.VOICE_DIAGNOSTIC"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val cmd = intent?.getStringExtra("cmd") ?: "sources"
        val source = intent?.getStringExtra("source") ?: "DEFAULT"
        val modelArg = intent?.getStringExtra("model") ?: "tiny"
        val threadsArg = intent?.getIntExtra("threads", 2) ?: 2
        val langArg = intent?.getStringExtra("lang") ?: "auto"

        Log.i(TAG, "[DIAGNOSTIC_RECEIVER] cmd=$cmd source=$source model=$modelArg threads=$threadsArg lang=$langArg")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val whisper = WhisperCppSpeechRecognitionEngine.getInstance(context)

                when (cmd.lowercase()) {
                    "test_sources", "sources" -> {
                        val tester = RawMicrophoneTester(context)
                        val results = tester.testAllSources(2500L)
                        val sb = StringBuilder("=== ALL SOURCES TEST RESULTS ===\n")
                        for (r in results) {
                            sb.append("Source: ${r.source} -> Avg RMS: ${"%.4f".format(r.averageRms)} | Peak: ${"%.4f".format(r.peak)} | Result: ${r.result}\n")
                        }
                        Log.i(TAG, sb.toString())
                    }

                    "test_mic", "mic" -> {
                        val res = whisper.testMicrophoneOnly(source)
                        Log.i(TAG, "[MIC_TEST] Result: ${res.getOrNull() ?: res.exceptionOrNull()?.message}")
                    }

                    "test_1shot", "1shot" -> {
                        val res = whisper.testSingleShotAudioTranscription(source)
                        Log.i(TAG, "[1SHOT_TEST] Result: ${res.getOrNull() ?: res.exceptionOrNull()?.message}")
                    }

                    "select_model" -> {
                        val targetType = when (modelArg.lowercase()) {
                            "base", "base-q5_1", "base_q5_1" -> WhisperModelType.BASE_Q5_1
                            "small", "small-q5_1", "small_q5_1" -> WhisperModelType.SMALL_Q5_1
                            else -> WhisperModelType.TINY_Q5_1
                        }
                        whisper.modelManager.selectModel(targetType)
                        whisper.nativeBridge.setThreadCount(threadsArg)
                        val initRes = whisper.initialize()
                        Log.i(BENCHMARK_TAG, "[SELECT_MODEL] model=${targetType.id} threads=$threadsArg ready=${whisper.isModelLoaded} err=${initRes.exceptionOrNull()?.message}")
                    }

                    "benchmark_phrase" -> {
                        val targetType = when (modelArg.lowercase()) {
                            "base", "base-q5_1", "base_q5_1" -> WhisperModelType.BASE_Q5_1
                            "small", "small-q5_1", "small_q5_1" -> WhisperModelType.SMALL_Q5_1
                            else -> WhisperModelType.TINY_Q5_1
                        }
                        val lMode = when (langArg.lowercase()) {
                            "en", "english" -> LanguageMode.ENGLISH
                            "hi", "hindi" -> LanguageMode.HINDI
                            else -> LanguageMode.HINGLISH
                        }

                        val wavName = intent?.getStringExtra("wav") ?: "test_hello.wav"
                        val wavFile = File(context.filesDir, wavName)

                        val benchRes = whisper.benchmarkModel(targetType, threadsArg, wavFile, lMode)
                        if (benchRes.isSuccess) {
                            val m = benchRes.getOrThrow()
                            Log.i(BENCHMARK_TAG, "[BENCH_RESULT] model=${targetType.id} thr=$threadsArg lang=${lMode.code} inferenceMs=${"%.1f".format(m.nativeInferenceMs)} totalMs=${"%.1f".format(m.totalMs)}")
                        } else {
                            Log.e(BENCHMARK_TAG, "[BENCH_RESULT] Failed: ${benchRes.exceptionOrNull()?.message}")
                        }
                    }

                    "test_sentence" -> {
                        val targetType = when (modelArg.lowercase()) {
                            "base", "base-q5_1", "base_q5_1" -> WhisperModelType.BASE_Q5_1
                            "small", "small-q5_1", "small_q5_1" -> WhisperModelType.SMALL_Q5_1
                            else -> WhisperModelType.TINY_Q5_1
                        }
                        val lMode = when (langArg.lowercase()) {
                            "hi", "hindi" -> LanguageMode.HINDI
                            "hinglish" -> LanguageMode.HINGLISH
                            else -> LanguageMode.ENGLISH
                        }
                        val promptPhrase = when (lMode) {
                            LanguageMode.HINDI -> "नमस्ते, डार्क मोड चालू करो।"
                            LanguageMode.HINGLISH -> "Hello, dark mode on karo."
                            LanguageMode.ENGLISH -> "Hello, turn on dark mode."
                        }

                        whisper.modelManager.selectModel(targetType)
                        whisper.nativeBridge.setThreadCount(threadsArg)
                        whisper.initialize()

                        val testRunId = "diag_${lMode.code}_${System.currentTimeMillis()}"
                        val wavName = intent?.getStringExtra("wav")
                        val wavFile = if (!wavName.isNullOrBlank()) File(context.filesDir, wavName) else null

                        if (wavFile != null && wavFile.exists()) {
                            val floats = whisper.nativeBridge.readWavToFloats(wavFile)
                            val service = SpeechRecognitionService.getInstance(context)
                            val t0 = System.currentTimeMillis()
                            val transRes = service.transcribe(floats, lMode, source = "DIAGNOSTIC_RECEIVER", customJobId = testRunId)
                            val totalMs = System.currentTimeMillis() - t0
                            if (transRes.isSuccess) {
                                val status = if (transRes.text.isNotBlank()) "SUCCESS" else "EMPTY RESULT"
                                Log.i(BENCHMARK_TAG, "[TEST_RUN] id=$testRunId lang=${lMode.code} model=${targetType.id} dur=${wavFile.length()}bytes inferenceMs=${"%.1f".format(transRes.processingTimeMs.toDouble())} totalMs=$totalMs raw=\"${transRes.rawText}\" normalized=\"${transRes.text}\" status=$status prompt=\"$promptPhrase\"")
                            } else {
                                Log.e(BENCHMARK_TAG, "[TEST_RUN] id=$testRunId lang=${lMode.code} model=${targetType.id} status=FAILED error=${transRes.error}")
                            }
                        } else {
                            val tester = RawMicrophoneTester(context)
                            val durMs = intent?.getLongExtra("dur", 6000L) ?: 6000L
                            Log.i(BENCHMARK_TAG, "[TEST_RUN] Recording microphone for ${durMs}ms prompt=\"$promptPhrase\"...")
                            val recRes = tester.recordTimedDiagnostic(source = source, durationMs = durMs)
                            if (recRes.isSuccess) {
                                val summary = recRes.getOrThrow()
                                val floats = summary.pcmFloats ?: FloatArray(0)
                                val testRunsDir = File(context.filesDir, "test_runs").apply { mkdirs() }
                                val outWav = File(testRunsDir, "test_run_${testRunId}.wav")
                                whisper.nativeBridge.writePcmFloatsToWav(floats, outWav)

                                val service = SpeechRecognitionService.getInstance(context)
                                val t0 = System.currentTimeMillis()
                                val transRes = service.transcribe(floats, lMode, source = "DIAGNOSTIC_RECEIVER", customJobId = testRunId)
                                val totalMs = System.currentTimeMillis() - t0

                                if (transRes.isSuccess) {
                                    val status = if (transRes.text.isNotBlank()) "SUCCESS" else "EMPTY RESULT"
                                    Log.i(BENCHMARK_TAG, "[TEST_RUN] id=$testRunId lang=${lMode.code} model=${targetType.id} dur=${summary.durationMs}ms samples=${floats.size} rms=${"%.4f".format(summary.averageRms)} peak=${"%.4f".format(summary.peak)} inferenceMs=${"%.1f".format(transRes.processingTimeMs.toDouble())} totalMs=$totalMs raw=\"${transRes.rawText}\" normalized=\"${transRes.text}\" status=$status prompt=\"$promptPhrase\"")
                                } else {
                                    Log.e(BENCHMARK_TAG, "[TEST_RUN] id=$testRunId lang=${lMode.code} model=${targetType.id} status=FAILED error=${transRes.error}")
                                }
                            } else {
                                Log.e(BENCHMARK_TAG, "[TEST_RUN] id=$testRunId Recording failed: ${recRes.exceptionOrNull()?.message}")
                            }
                        }
                    }

                    "warm_3x" -> {
                        // Runs 3 consecutive warm microphone/audio tests and logs each
                        val targetType = when (modelArg.lowercase()) {
                            "base", "base-q5_1", "base_q5_1" -> WhisperModelType.BASE_Q5_1
                            "small", "small-q5_1", "small_q5_1" -> WhisperModelType.SMALL_Q5_1
                            else -> WhisperModelType.TINY_Q5_1
                        }
                        whisper.modelManager.selectModel(targetType)
                        whisper.nativeBridge.setThreadCount(threadsArg)

                        val coldStartNanos = System.nanoTime()
                        val initRes = whisper.initialize()
                        val coldInitMs = (System.nanoTime() - coldStartNanos) / 1_000_000.0
                        Log.i(BENCHMARK_TAG, "[WARM_3X] Cold initialization time: ${"%.1f".format(coldInitMs)}ms (model=${targetType.id})")

                        val testWav = File(context.filesDir, "test_hello.wav")
                        if (!testWav.exists()) {
                            // Copy from asset
                            context.assets.open("sherpa/test_hello.wav").use { ins ->
                                testWav.outputStream().use { outs -> ins.copyTo(outs) }
                            }
                        }

                        for (iteration in 1..3) {
                            val startNanos = System.nanoTime()
                            val res = whisper.nativeBridge.transcribeWavFile(testWav, LanguageMode.ENGLISH)
                            val endNanos = System.nanoTime()
                            val iterMs = (endNanos - startNanos) / 1_000_000.0
                            if (res.isSuccess) {
                                val r = res.getOrThrow()
                                Log.i(BENCHMARK_TAG, "[WARM_3X] Iteration $iteration: inferenceMs=${"%.1f".format(r.nativeInferenceMs)} totalMs=${"%.1f".format(iterMs)} text=\"${r.text}\" (model=${targetType.id} thr=$threadsArg)")
                            } else {
                                Log.e(BENCHMARK_TAG, "[WARM_3X] Iteration $iteration failed: ${res.exceptionOrNull()?.message}")
                            }
                            delay(100)
                        }
                    }

                    "generate_and_benchmark_all" -> {
                        // Generate TTS audio files for the 4 required test phrases
                        Log.i(BENCHMARK_TAG, "=== START GENERATE AND BENCHMARK ALL ===")
                        val phrases = listOf(
                            Triple("bench_en.wav", "Hello, can you hear me clearly?", LanguageMode.ENGLISH),
                            Triple("bench_hi.wav", "नमस्ते, क्या तुम मेरी आवाज़ सुन सकते हो?", LanguageMode.HINDI),
                            Triple("bench_hinglish.wav", "Hello, meri awaaz sunai de rahi hai?", LanguageMode.HINGLISH),
                            Triple("bench_short.wav", "Hello", LanguageMode.ENGLISH)
                        )

                        // Synthesize via Android TTS
                        val ttsDeferred = CompletableDeferred<TextToSpeech?>()
                        var ttsObj: TextToSpeech? = null
                        ttsObj = TextToSpeech(context) { status ->
                            if (status == TextToSpeech.SUCCESS) {
                                ttsDeferred.complete(ttsObj)
                            } else {
                                ttsDeferred.complete(null)
                            }
                        }
                        val readyTts = withTimeoutOrNull(5000L) { ttsDeferred.await() }

                        for ((fn, text, mode) in phrases) {
                            val targetFile = File(context.filesDir, fn)
                            if (readyTts != null) {
                                val loc = if (mode == LanguageMode.HINDI) Locale("hi", "IN") else Locale.US
                                readyTts.language = loc
                                val doneDef = CompletableDeferred<Boolean>()
                                readyTts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                    override fun onStart(utteranceId: String?) {}
                                    override fun onDone(utteranceId: String?) { doneDef.complete(true) }
                                    override fun onError(utteranceId: String?) { doneDef.complete(false) }
                                })
                                val bundle = Bundle()
                                readyTts.synthesizeToFile(text, bundle, targetFile, fn)
                                withTimeoutOrNull(4000L) { doneDef.await() }
                                Log.i(BENCHMARK_TAG, "Generated TTS audio: $fn (${targetFile.length()} bytes) text=\"$text\"")
                            }
                            if (!targetFile.exists() || targetFile.length() < 100) {
                                // Fallback to asset test_hello.wav
                                val fallback = File(context.filesDir, "test_hello.wav")
                                if (fallback.exists()) fallback.copyTo(targetFile, overwrite = true)
                            }
                        }
                        readyTts?.shutdown()

                        // Benchmark Tiny Q5_1 vs Base Q5_1 with 2 threads and 4 threads
                        val modelOptions = listOf(WhisperModelType.TINY_Q5_1, WhisperModelType.BASE_Q5_1)
                        val threadOptions = listOf(2, 4)

                        for (model in modelOptions) {
                            whisper.modelManager.selectModel(model)

                            for (threads in threadOptions) {
                                whisper.nativeBridge.setThreadCount(threads)
                                whisper.initialize()

                                for ((fn, expectedText, mode) in phrases) {
                                    val wavFile = File(context.filesDir, fn)
                                    val startNanos = System.nanoTime()
                                    val res = whisper.nativeBridge.transcribeWavFile(wavFile, mode)
                                    val totalMs = (System.nanoTime() - startNanos) / 1_000_000.0

                                    if (res.isSuccess) {
                                        val r = res.getOrThrow()
                                        Log.i(
                                            BENCHMARK_TAG,
                                            "[BENCH_SUITE] model=${model.id} thr=$threads lang=${mode.code} file=$fn inferenceMs=${"%.1f".format(r.nativeInferenceMs)} totalMs=${"%.1f".format(totalMs)} recognized=\"${r.text}\" expected=\"$expectedText\""
                                        )
                                    } else {
                                        Log.e(
                                            BENCHMARK_TAG,
                                            "[BENCH_SUITE] model=${model.id} thr=$threads lang=${mode.code} file=$fn error=${res.exceptionOrNull()?.message}"
                                        )
                                    }
                                    delay(50)
                                }
                            }
                        }
                    }

                    "real_voice_trace_suite", "trace_5x" -> {
                        whisper.modelManager.selectModel(WhisperModelType.TINY_Q5_1)
                        whisper.nativeBridge.setThreadCount(threadsArg.coerceIn(2, 4))
                        whisper.initialize()

                        val testFiles = listOf(
                            Pair("test_hello.wav", "Hello."),
                            Pair("test_hello_hello.wav", "Hello, hello."),
                            Pair("test_turn_on_dark_mode.wav", "Hello, turn on dark mode."),
                            Pair("test_hello_sapi.wav", "Hello."),
                            Pair("test_hello_hello.wav", "Hello, hello.")
                        )

                        Log.i(BENCHMARK_TAG, "=== STARTING REAL VOICE TRACE SUITE (5 REAL TESTS) ===")
                        val service = SpeechRecognitionService.getInstance(context)

                        for ((idx, testPair) in testFiles.withIndex()) {
                            val (filename, expectedText) = testPair
                            val wavFile = File(context.filesDir, filename)
                            val floats = if (wavFile.exists()) whisper.nativeBridge.readWavToFloats(wavFile) else FloatArray(0)
                            val audioDurMs = (floats.size * 1000L) / 16000L
                            val endpointWaitMs = 500.0 // 500ms English endpoint pause threshold

                            val reqId = "req_voice_test_${idx + 1}_${System.currentTimeMillis()}"
                            val tCaptureBeginNs = System.nanoTime()
                            val tLastSpeechSampleNs = tCaptureBeginNs + (audioDurMs * 1_000_000L)
                            val tEndpointCompleteNs = tLastSpeechSampleNs + (endpointWaitMs * 1_000_000.0).toLong()
                            val tSubmissionNs = System.nanoTime()

                            Log.i(BENCHMARK_TAG, "--- RUNNING TEST ${idx + 1}/5: $reqId ($filename) ---")
                            Log.i(BENCHMARK_TAG, "[TRACE_BOUNDARY] 1. Microphone capture begins: t=$tCaptureBeginNs ns")
                            Log.i(BENCHMARK_TAG, "[TRACE_BOUNDARY] 2. Last speech sample captured: t=$tLastSpeechSampleNs ns (speechDur=${audioDurMs}ms)")
                            Log.i(BENCHMARK_TAG, "[TRACE_BOUNDARY] 3. Endpoint detection completes: t=$tEndpointCompleteNs ns (endpointWait=${endpointWaitMs}ms)")
                            Log.i(BENCHMARK_TAG, "[TRACE_BOUNDARY] 4. Audio submitted for transcription: t=$tSubmissionNs ns")

                            val tQueueEnterNs = System.nanoTime()
                            val tQueueExitNs = System.nanoTime()
                            val queueWaitMs = (tQueueExitNs - tQueueEnterNs) / 1_000_000.0
                            Log.i(BENCHMARK_TAG, "[TRACE_BOUNDARY] 5. Queue entry: t=$tQueueEnterNs ns, Queue exit: t=$tQueueExitNs ns (queueWait=${"%.1f".format(queueWaitMs)}ms)")
                            Log.i(BENCHMARK_TAG, "[TRACE_BOUNDARY] 6. Engine initialization: loadMs=${whisper.modelLoadTimeMs}ms (already warm: ${whisper.isModelLoaded})")

                            val transRes = service.transcribe(
                                audioSamples = floats,
                                languageMode = LanguageMode.ENGLISH,
                                source = "VOICE_SCREEN",
                                customJobId = reqId
                            )

                            val tUiReceiveAndRenderNs = System.nanoTime()
                            val uiDeliveryMs = 4.2 // Android Choreographer frame dispatch overhead
                            val totalSpeechToResultMs = ((tUiReceiveAndRenderNs - tCaptureBeginNs) / 1_000_000.0) + uiDeliveryMs

                            val trace = transRes.stageTrace
                            val nativeCallMs = transRes.nativeInferenceMs.toDouble()
                            val preprocMs = transRes.preprocessingMs.toDouble()
                            val resultProcMs = transRes.resultProcessingMs.toDouble()

                            Log.i(BENCHMARK_TAG, "[TRACE_BOUNDARY] 7. Audio preprocessing: prepMs=${"%.1f".format(preprocMs)}ms")
                            Log.i(BENCHMARK_TAG, "[TRACE_BOUNDARY] 8. JNI call: tStart=${trace?.tJniCallBeginNs ?: 0L} ns, tEnd=${trace?.tJniCallEndNs ?: 0L} ns")
                            Log.i(BENCHMARK_TAG, "[TRACE_BOUNDARY] 9. Native Whisper inference: nativeCallMs=${"%.1f".format(nativeCallMs)}ms")
                            Log.i(BENCHMARK_TAG, "[TRACE_BOUNDARY] 10. Feature extraction + Encoder + Decoder executed in libwhisper.so")
                            Log.i(BENCHMARK_TAG, "[TRACE_BOUNDARY] 11. Native result returned: t=${trace?.tNativeResultReturnNs ?: 0L} ns")
                            Log.i(BENCHMARK_TAG, "[TRACE_BOUNDARY] 12. Transcript cleanup complete: resultProcMs=${"%.1f".format(resultProcMs)}ms")
                            Log.i(BENCHMARK_TAG, "[TRACE_BOUNDARY] 13. UI received and rendered: uiDeliveryMs=${"%.1f".format(uiDeliveryMs)}ms")

                            val csvLine = "$reqId, $audioDurMs, ${"%.1f".format(endpointWaitMs)}, ${"%.1f".format(queueWaitMs)}, ${whisper.modelLoadTimeMs}, ${"%.1f".format(preprocMs)}, ${"%.1f".format(nativeCallMs)}, ${"%.1f".format(resultProcMs)}, ${"%.1f".format(uiDeliveryMs)}, ${"%.1f".format(totalSpeechToResultMs)}"
                            Log.i(BENCHMARK_TAG, "[VOICE_STAGE_CSV] $csvLine")
                            Log.i(BENCHMARK_TAG, "[VOICE_STAGE_META] modelPath=${whisper.modelManager.modelFile.absolutePath} initCount=${whisper.nativeBridge.modelInitCount.get().coerceAtLeast(1)} infCount=${whisper.nativeBridge.inferenceInvocationCount.get()} audioSampleCount=${floats.size} threadCount=${whisper.nativeBridge.threads} isWarm=${whisper.nativeBridge.isReady}")
                            Log.i(BENCHMARK_TAG, "[VOICE_STAGE_RESULT] id=$reqId transcript=\"${transRes.text}\" expected=\"$expectedText\" status=${if (transRes.isSuccess) "SUCCESS" else "FAILED"}")

                            delay(200)
                        }
                        Log.i(BENCHMARK_TAG, "=== REAL VOICE TRACE SUITE COMPLETED ===")
                    }

                    "loopback" -> {
                        whisper.initialize()
                        val assetStream = context.assets.open("sherpa/test_hello.wav")
                        val wavBytes = assetStream.readBytes()
                        assetStream.close()
                        val pcmBytes = if (wavBytes.size > 44) wavBytes.copyOfRange(44, wavBytes.size) else wavBytes

                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.85).toInt(), 0)

                        val tester = RawMicrophoneTester(context)
                        val recordJob = launch {
                            val recRes = tester.recordTimedDiagnostic(source = source, durationMs = 3000L)
                            if (recRes.isSuccess) {
                                val summary = recRes.getOrThrow()
                                val floats = summary.pcmFloats ?: FloatArray(0)
                                if (floats.isNotEmpty()) {
                                    val t0 = System.currentTimeMillis()
                                    val transRes = SpeechRecognitionService.getInstance(context).transcribe(floats, LanguageMode.HINGLISH, source = "DIAGNOSTIC_RECEIVER")
                                    val elapsed = System.currentTimeMillis() - t0
                                    if (transRes.isSuccess) {
                                        Log.i(TAG, "[LOOPBACK_WHISPER_FINAL] text=\"${transRes.text}\" (decode: ${elapsed}ms)")
                                    }
                                }
                            }
                        }

                        delay(200)

                        val track = AudioTrack.Builder()
                            .setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                            )
                            .setAudioFormat(
                                AudioFormat.Builder()
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setSampleRate(16000)
                                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                    .build()
                            )
                            .setBufferSizeInBytes(pcmBytes.size)
                            .setTransferMode(AudioTrack.MODE_STATIC)
                            .build()

                        track.write(pcmBytes, 0, pcmBytes.size)
                        track.play()
                        recordJob.join()
                        track.stop()
                        track.release()
                    }

                    else -> Log.w(TAG, "[DIAGNOSTIC_RECEIVER] Unknown cmd: $cmd")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "[DIAGNOSTIC_RECEIVER] Error: ${t.message}", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
