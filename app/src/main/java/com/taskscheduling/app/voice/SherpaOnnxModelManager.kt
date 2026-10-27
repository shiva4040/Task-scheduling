package com.taskscheduling.app.voice

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class SherpaModelFiles(
    val tokensPath: String,
    val encoderPath: String,
    val decoderPath: String,
    val joinerPath: String,
    val sampleRate: Int = 16000
)

/**
 * Manages on-device offline models for Sherpa-ONNX streaming ASR.
 * Handles deterministic model verification, asset extraction, and recognizer instantiation.
 */
class SherpaOnnxModelManager(
    private val context: Context,
    private val modelAssetFolder: String = "sherpa/model"
) {
    companion object {
        const val TAG = "SherpaModelManager"
        const val MODEL_DIR_NAME = "sherpa-model"
        const val DEFAULT_SAMPLE_RATE = 16000
    }

    private var cachedRecognizer: OnlineRecognizer? = null
    private val lock = Any()

    /**
     * Extracts (if necessary), verifies, and loads the streaming Zipformer model.
     */
    suspend fun loadRecognizer(): Result<OnlineRecognizer> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            cachedRecognizer?.let { return@withContext Result.success(it) }

            Log.i(TAG, "[SHERPA_MODEL] Loading...")
            Log.i(TAG, "[SHERPA_SAMPLE_RATE] $DEFAULT_SAMPLE_RATE")

            val modelFilesResult = prepareModelFiles()
            if (modelFilesResult.isFailure) {
                val ex = modelFilesResult.exceptionOrNull() ?: IllegalStateException("Failed to prepare Sherpa model files")
                Log.e(TAG, "[SHERPA_MODEL] Model preparation failed: ${ex.message}", ex)
                return@withContext Result.failure(ex)
            }

            val modelFiles = modelFilesResult.getOrThrow()
            Log.i(TAG, "[SHERPA_MODEL] Model files verified")

            try {
                val config = OnlineRecognizerConfig(
                    featConfig = FeatureConfig(
                        sampleRate = modelFiles.sampleRate,
                        featureDim = 80
                    ),
                    modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(
                            encoder = modelFiles.encoderPath,
                            decoder = modelFiles.decoderPath,
                            joiner = modelFiles.joinerPath
                        ),
                        tokens = modelFiles.tokensPath,
                        numThreads = 2,
                        provider = "cpu",
                        modelType = "zipformer2"
                    ),
                    endpointConfig = EndpointConfig(
                        rule1 = EndpointRule(mustContainNonSilence = true, minTrailingSilence = 2.4f, minUtteranceLength = 0.0f),
                        rule2 = EndpointRule(mustContainNonSilence = true, minTrailingSilence = 1.2f, minUtteranceLength = 0.0f),
                        rule3 = EndpointRule(mustContainNonSilence = false, minTrailingSilence = 0.0f, minUtteranceLength = 20.0f)
                    ),
                    enableEndpoint = true,
                    decodingMethod = "greedy_search"
                )

                val recognizer = OnlineRecognizer(assetManager = null, config = config)
                cachedRecognizer = recognizer
                Log.i(TAG, "[SHERPA_MODEL] Recognizer created successfully (zipformer2)")
                Result.success(recognizer)
            } catch (e: Throwable) {
                Log.e(TAG, "[SHERPA_ERROR] type=init message=Failed to instantiate OnlineRecognizer: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    data class ModelSpec(
        val fileName: String,
        val expectedSize: Long,
        val expectedSha256: String
    )

    /**
     * Verifies existence and extracts required model files to internal storage.
     * Compares exact file size and SHA-256 against bundled assets.
     */
    fun prepareModelFiles(): Result<SherpaModelFiles> {
        val targetDir = File(context.filesDir, MODEL_DIR_NAME)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val requiredModels = listOf(
            ModelSpec("tokens.txt", 5048L, "49E3C2646595FD907228B3C6787069658F67B17377C60AEB8619C4551B2316FB"),
            ModelSpec("encoder.onnx", 71083163L, "563FDE436D16CF7607CF408CD6B30909819D03162652EF389C2450CED3F45AC1"),
            ModelSpec("decoder.onnx", 2092621L, "7BF787F90B194B307E5A4AD6A34FADB4E748304C35F78A8D66358A05B13EE6EF"),
            ModelSpec("joiner.onnx", 259335L, "D944208D660D67C8D72CD2ACAEAC971FA5CEB8C80E76C1968148846FEDD6E297")
        )

        try {
            val assets = context.assets

            for (spec in requiredModels) {
                val destFile = File(targetDir, spec.fileName)
                val assetPath = "$modelAssetFolder/${spec.fileName}"

                var needsExtraction = !destFile.exists() || destFile.length() != spec.expectedSize

                if (!needsExtraction) {
                    val currentSha = computeSha256(destFile)
                    if (!currentSha.equals(spec.expectedSha256, ignoreCase = true)) {
                        Log.w(TAG, "SHA-256 mismatch for ${spec.fileName}: got $currentSha, expected ${spec.expectedSha256}. Re-extracting...")
                        needsExtraction = true
                    }
                }

                if (needsExtraction) {
                    if (destFile.exists()) {
                        destFile.delete()
                    }
                    try {
                        copyAssetFile(assets, assetPath, destFile)
                    } catch (e: Exception) {
                        Log.e(TAG, "[SHERPA_ERROR] type=model_extract message=Failed to extract '$assetPath': ${e.message}", e)
                        return Result.failure(
                            IllegalStateException("Required Sherpa-ONNX asset '$assetPath' could not be extracted: ${e.message}", e)
                        )
                    }

                    // Verify extracted size exactly matches expected asset size
                    if (destFile.length() != spec.expectedSize) {
                        return Result.failure(
                            IllegalStateException("Extracted ${spec.fileName} size mismatch: got ${destFile.length()}, expected ${spec.expectedSize}")
                        )
                    }

                    val postExtractSha = computeSha256(destFile)
                    if (!postExtractSha.equals(spec.expectedSha256, ignoreCase = true)) {
                        return Result.failure(
                            IllegalStateException("Extracted ${spec.fileName} SHA-256 mismatch: got $postExtractSha, expected ${spec.expectedSha256}")
                        )
                    }
                }
            }

            val tokensFile = File(targetDir, "tokens.txt")
            val encoderFile = File(targetDir, "encoder.onnx")
            val decoderFile = File(targetDir, "decoder.onnx")
            val joinerFile = File(targetDir, "joiner.onnx")

            for (file in listOf(tokensFile, encoderFile, decoderFile, joinerFile)) {
                if (!file.exists() || !file.canRead() || file.length() == 0L) {
                    Log.e(TAG, "[SHERPA_ERROR] type=model_verify message=Model file ${file.name} invalid")
                    return Result.failure(IllegalStateException("Sherpa-ONNX model file ${file.name} is missing, empty, or unreadable"))
                }
            }

            Log.i(TAG, "[SHERPA_MODEL_FILES] verified: tokens=${tokensFile.length()}B encoder=${encoderFile.length()}B decoder=${decoderFile.length()}B joiner=${joinerFile.length()}B sha256_verified=true")

            return Result.success(
                SherpaModelFiles(
                    tokensPath = tokensFile.absolutePath,
                    encoderPath = encoderFile.absolutePath,
                    decoderPath = decoderFile.absolutePath,
                    joinerPath = joinerFile.absolutePath,
                    sampleRate = DEFAULT_SAMPLE_RATE
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "[SHERPA_ERROR] type=model_prepare message=${e.message}", e)
            return Result.failure(e)
        }
    }

    /**
     * Executes developer verification test: decodes a known bundled WAV file directly through Sherpa-ONNX
     * without AudioRecord, isolating model/runtime decoding correctness from microphone capture.
     */
    suspend fun testSherpaAudioFile(assetPath: String = "sherpa/test_hello.wav"): Result<String> = withContext(Dispatchers.IO) {
        val recognizerResult = loadRecognizer()
        if (recognizerResult.isFailure) {
            return@withContext Result.failure(recognizerResult.exceptionOrNull() ?: IllegalStateException("Failed to load recognizer"))
        }
        val recognizer = recognizerResult.getOrThrow()

        try {
            val wavBytes = context.assets.open(assetPath).use { it.readBytes() }
            if (wavBytes.size < 44) {
                return@withContext Result.failure(IllegalStateException("WAV file $assetPath too small (${wavBytes.size} bytes)"))
            }

            // Find "data" chunk in WAV
            var dataOffset = -1
            var dataSize = 0
            var i = 12
            while (i + 8 <= wavBytes.size) {
                val chunkId = String(wavBytes, i, 4, Charsets.US_ASCII)
                val chunkSize = (wavBytes[i + 4].toInt() and 0xFF) or
                        ((wavBytes[i + 5].toInt() and 0xFF) shl 8) or
                        ((wavBytes[i + 6].toInt() and 0xFF) shl 16) or
                        ((wavBytes[i + 7].toInt() and 0xFF) shl 24)
                if (chunkId == "data") {
                    dataOffset = i + 8
                    dataSize = minOf(chunkSize, wavBytes.size - dataOffset)
                    break
                }
                i += 8 + chunkSize
            }

            if (dataOffset == -1 || dataSize <= 0) {
                // Fallback to standard 44-byte offset
                dataOffset = 44
                dataSize = wavBytes.size - 44
            }

            val numSamples = dataSize / 2
            val floatSamples = FloatArray(numSamples)
            val byteBuffer = java.nio.ByteBuffer.wrap(wavBytes, dataOffset, dataSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for (idx in 0 until numSamples) {
                val s = byteBuffer.short
                floatSamples[idx] = s.toFloat() / 32768.0f
            }

            val stream = recognizer.createStream()
            val chunkSize = 1024
            for (offset in floatSamples.indices step chunkSize) {
                val end = minOf(offset + chunkSize, floatSamples.size)
                val chunk = floatSamples.copyOfRange(offset, end)
                stream.acceptWaveform(chunk, DEFAULT_SAMPLE_RATE)
                while (recognizer.isReady(stream)) {
                    recognizer.decode(stream)
                }
            }

            // Feed 0.5s trailing silence so the model flushes trailing acoustic context
            val trailingSilence = FloatArray(DEFAULT_SAMPLE_RATE / 2) { 0f }
            stream.acceptWaveform(trailingSilence, DEFAULT_SAMPLE_RATE)
            while (recognizer.isReady(stream)) {
                recognizer.decode(stream)
            }

            val recognized = recognizer.getResult(stream).text.trim()
            try {
                stream.release()
            } catch (_: Throwable) {}

            Log.i(TAG, "[SHERPA_WAV_TEST] asset=$assetPath samples=$numSamples decoded=\"$recognized\"")
            Result.success(recognized)
        } catch (e: Throwable) {
            Log.e(TAG, "[SHERPA_WAV_TEST_ERROR] asset=$assetPath error=${e.message}", e)
            Result.failure(e)
        }
    }

    private fun computeSha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(16384)
            var n: Int
            while (input.read(buf).also { n = it } != -1) {
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02X".format(it) }
    }

    private fun copyAssetFile(assetManager: AssetManager, assetPath: String, destFile: File) {
        val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")
        try {
            assetManager.open(assetPath).use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
            if (destFile.exists()) destFile.delete()
            if (!tempFile.renameTo(destFile)) {
                // Fallback copy if renameTo fails across file boundaries
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
            }
            Log.i(TAG, "Successfully extracted asset $assetPath to ${destFile.absolutePath} (${destFile.length()} bytes)")
        } catch (e: IOException) {
            tempFile.delete()
            Log.e(TAG, "[SHERPA_ERROR] type=asset_copy message=Failed to copy asset $assetPath: ${e.message}", e)
            throw e
        }
    }

    fun isModelLoaded(): Boolean = synchronized(lock) { cachedRecognizer != null }

    fun release() {
        synchronized(lock) {
            cachedRecognizer?.release()
            cachedRecognizer = null
        }
    }
}
