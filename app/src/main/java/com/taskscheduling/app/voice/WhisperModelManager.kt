package com.taskscheduling.app.voice

import android.content.Context
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Supported configurable Whisper model variants for offline speech recognition.
 */
enum class WhisperModelType(
    val id: String,
    val filename: String,
    val displayName: String,
    val sizeBytes: Long,
    val sha256: String,
    val parameterCount: String
) {
    TINY_Q5_1(
        id = "tiny-q5_1",
        filename = "ggml-tiny-q5_1.bin",
        displayName = "Whisper Tiny Q5_1 (Fast Offline)",
        sizeBytes = 32152673L,
        sha256 = "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7",
        parameterCount = "39M"
    ),
    BASE_Q5_1(
        id = "base-q5_1",
        filename = "ggml-base-q5_1.bin",
        displayName = "Whisper Base Q5_1 (Balanced)",
        sizeBytes = 59707625L,
        sha256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
        parameterCount = "74M"
    ),
    SMALL_Q5_1(
        id = "small-q5_1",
        filename = "ggml-small-q5_1.bin",
        displayName = "Whisper Small Q5_1 (Heavy)",
        sizeBytes = 190085487L,
        sha256 = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
        parameterCount = "244M"
    );

    companion object {
        fun fromFilename(name: String): WhisperModelType? {
            return entries.firstOrNull { it.filename.equals(name, ignoreCase = true) }
        }
    }
}

/**
 * Expected model metadata descriptor.
 */
data class WhisperModelInfo(
    val filename: String,
    val sizeBytes: Long,
    val sha256: String,
    val modelVersion: String
)

/**
 * State representing the offline Whisper model lifecycle on disk.
 */
sealed class WhisperModelStatus {
    object Missing : WhisperModelStatus()
    object NotInstalled : WhisperModelStatus()
    data class Installing(val progress: Float, val bytesCopied: Long, val totalBytes: Long) : WhisperModelStatus()
    object Verifying : WhisperModelStatus()
    data class Ready(val file: File, val sizeBytes: Long, val sha256: String) : WhisperModelStatus()
    data class Invalid(val message: String, val actualSha256: String = "") : WhisperModelStatus()
    data class Corrupted(val message: String, val actualSha256: String = "") : WhisperModelStatus()
    data class InsufficientStorage(val message: String, val requiredBytes: Long, val availableBytes: Long) : WhisperModelStatus()
    data class Error(val message: String) : WhisperModelStatus()

    val isReady: Boolean get() = this is Ready
}

/**
 * Robust model manager supporting configurable quantized multilingual OpenAI Whisper GGML models.
 *
 * Responsibilities:
 * 1. Manages model files in app internal storage: `files/models/whisper/<filename>`
 * 2. Supports configurable model switching (TINY_Q5_1 default for fast short utterances, BASE_Q5_1, SMALL_Q5_1)
 * 3. Fast <1ms metadata checks using `files/models/whisper/model_metadata.json`
 * 4. Verifies file integrity with official SHA-256 and expected size bytes
 * 5. Ensures atomic writes via `.tmp` file and rename
 * 6. Checks available storage before copying/installing
 * 7. Cleans up obsolete legacy models (e.g. `ggml-large-v3-turbo-q5_0.bin`) to reclaim storage
 * 8. Never downloads from the internet unexpectedly at runtime
 */
class WhisperModelManager(
    private val context: Context? = null,
    private val overrideModelDir: File? = null
) {
    companion object {
        const val TAG = "WhisperModelManager"
        const val METADATA_FILENAME = "model_metadata.json"
        val DEFAULT_MODEL = WhisperModelType.TINY_Q5_1

        const val MIN_FREE_STORAGE_BYTES = 300L * 1024 * 1024

        // Backward compatibility constants (Small Q5_1)
        const val MODEL_FILENAME = "ggml-small-q5_1.bin"
        const val OFFICIAL_SHA256 = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb"
        const val EXPECTED_SIZE_BYTES = 190085487L
    }

    var selectedModel: WhisperModelType = DEFAULT_MODEL
        private set

    fun selectModel(type: WhisperModelType) {
        selectedModel = type
        Log.i(TAG, "[WhisperModelManager] Selected model: ${type.displayName} (${type.filename})")
    }

    val modelDir: File
        get() = overrideModelDir ?: run {
            val base = try { context?.filesDir } catch (_: Throwable) { null }
                ?: File(System.getProperty("java.io.tmpdir"), "app_files")
            File(base, "models/whisper").apply {
                if (!exists()) mkdirs()
            }
        }

    val modelFile: File
        get() = File(modelDir, selectedModel.filename)

    val metadataFile: File
        get() = File(modelDir, METADATA_FILENAME)

    fun getModelPath(): String = modelFile.absolutePath

    fun isModelInstalled(): Boolean = modelFile.exists() && modelFile.length() == selectedModel.sizeBytes

    fun getExpectedModelInfo(): WhisperModelInfo = WhisperModelInfo(
        filename = selectedModel.filename,
        sizeBytes = selectedModel.sizeBytes,
        sha256 = selectedModel.sha256,
        modelVersion = selectedModel.id
    )

    /**
     * Checks available storage bytes on the model storage partition.
     */
    fun getAvailableStorageBytes(): Long {
        return try {
            val free = overrideModelDir?.usableSpace ?: 0L
            if (free > 0L) return free
            val stat = StatFs(modelDir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Throwable) {
            try {
                context?.filesDir?.freeSpace ?: (10L * 1024 * 1024 * 1024)
            } catch (_: Throwable) {
                10L * 1024 * 1024 * 1024
            }
        }
    }

    /**
     * Verifies deployment metadata rapidly (<1ms) without re-hashing.
     */
    fun verifyModelMetadata(modelType: WhisperModelType = selectedModel): Boolean {
        val targetFile = File(modelDir, modelType.filename)
        if (!targetFile.exists() || targetFile.length() != modelType.sizeBytes) return false
        if (!metadataFile.exists() || metadataFile.length() == 0L) return false

        return try {
            val content = metadataFile.readText()
            val json = JSONObject(content)
            val fn = json.optString("filename", "")
            val size = json.optLong("sizeBytes", 0L)
            val hash = json.optString("sha256", "")
            val ver = json.optString("modelVersion", "")

            fn == modelType.filename &&
                    size == modelType.sizeBytes &&
                    hash.equals(modelType.sha256, ignoreCase = true) &&
                    ver == modelType.id
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing model metadata: ${e.message}")
            false
        }
    }

    /**
     * Writes or updates model metadata JSON.
     */
    fun writeMetadata(
        modelType: WhisperModelType = selectedModel,
        sha256: String = modelType.sha256,
        sizeBytes: Long = modelType.sizeBytes
    ) {
        try {
            val json = JSONObject().apply {
                put("filename", modelType.filename)
                put("sizeBytes", sizeBytes)
                put("sha256", sha256)
                put("modelVersion", modelType.id)
            }
            metadataFile.writeText(json.toString(2))
            Log.i(TAG, "[WhisperDeploy] Metadata written for ${modelType.filename}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write model metadata: ${e.message}", e)
        }
    }

    /**
     * Cleans up obsolete legacy models from internal storage to reclaim disk space.
     */
    fun cleanupObsoleteModels() {
        val obsoleteFilenames = listOf(
            "ggml-large-v3-turbo-q5_0.bin",
            "ggml-large-v3-turbo-q5_0.bin.tmp",
            "ggml-large-v3-turbo.bin",
            "ggml-large-v3-turbo.bin.tmp"
        )
        for (obsolete in obsoleteFilenames) {
            val f = File(modelDir, obsolete)
            if (f.exists()) {
                try {
                    f.delete()
                    Log.i(TAG, "[WhisperDeploy] Cleaned up obsolete model file: ${f.name}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete obsolete model file ${f.name}: ${e.message}")
                }
            }
        }
    }

    /**
     * Evaluates current model installation status without blocking the UI.
     * Reuses valid existing model and metadata if current, avoiding expensive re-hashes.
     */
    suspend fun checkStatus(targetModel: WhisperModelType = selectedModel): WhisperModelStatus = withContext(Dispatchers.IO) {
        deleteInvalidTemporaryModel()
        cleanupObsoleteModels()

        val file = File(modelDir, targetModel.filename)
        if (!file.exists()) {
            // Check if model exists in /data/local/tmp (pushed via adb)
            val tmpCandidate = File("/data/local/tmp", targetModel.filename)
            if (tmpCandidate.exists() && tmpCandidate.length() == targetModel.sizeBytes) {
                Log.i(TAG, "[WhisperDeploy] Importing model from /data/local/tmp/${targetModel.filename}...")
                val res = installFromLocalFile(tmpCandidate, targetModel)
                if (res.isSuccess) {
                    return@withContext WhisperModelStatus.Ready(file, file.length(), targetModel.sha256)
                }
            }

            // Check if model exists in assets
            if (context != null) {
                val assetPath = "models/${targetModel.filename}"
                val assetExists = try {
                    context.assets.open(assetPath).use { true }
                } catch (_: Exception) {
                    false
                }

                if (assetExists) {
                    val available = getAvailableStorageBytes()
                    val minFree = targetModel.sizeBytes + (50L * 1024 * 1024)
                    if (available < minFree) {
                        return@withContext WhisperModelStatus.InsufficientStorage(
                            message = "Not enough storage for ${targetModel.displayName}. Free at least ${minFree / (1024 * 1024)} MB.",
                            requiredBytes = minFree,
                            availableBytes = available
                        )
                    }

                    Log.i(TAG, "[WhisperDeploy] Installing model from APK assets ($assetPath)...")
                    val installResult = installFromAsset(assetPath, targetModel)
                    return@withContext if (installResult.isSuccess) {
                        val installedFile = installResult.getOrThrow()
                        WhisperModelStatus.Ready(installedFile, installedFile.length(), targetModel.sha256)
                    } else {
                        val err = installResult.exceptionOrNull()?.message ?: "Asset extraction failed"
                        WhisperModelStatus.Error("Failed to install bundled speech model from APK: $err")
                    }
                }
            }

            return@withContext WhisperModelStatus.Missing
        }

        // Fast metadata check (<1ms)
        if (file.length() == targetModel.sizeBytes && verifyModelMetadata(targetModel)) {
            Log.i(TAG, "[WHISPER_MODEL] path=${file.absolutePath} size=${file.length()} status=READY (verified by metadata)")
            return@withContext WhisperModelStatus.Ready(file, file.length(), targetModel.sha256)
        }

        if (file.length() <= 0L) {
            return@withContext WhisperModelStatus.Invalid("Model file is empty.")
        }

        if (file.length() != targetModel.sizeBytes) {
            return@withContext WhisperModelStatus.Invalid(
                "Model size mismatch: expected ${targetModel.sizeBytes} bytes, found ${file.length()} bytes."
            )
        }

        // Compute and verify full cryptographic checksum if metadata is missing or mismatch
        val sha256 = computeSha256(file)
        if (sha256.equals(targetModel.sha256, ignoreCase = true)) {
            writeMetadata(targetModel, sha256, file.length())
            Log.i(TAG, "[WHISPER_MODEL] path=${file.absolutePath} size=${file.length()} sha256=$sha256 status=READY")
            WhisperModelStatus.Ready(file, file.length(), sha256)
        } else {
            Log.e(TAG, "[WHISPER_MODEL] Checksum mismatch: expected=${targetModel.sha256} actual=$sha256")
            WhisperModelStatus.Corrupted(
                message = "Model checksum verification failed for ${targetModel.filename}.",
                actualSha256 = sha256
            )
        }
    }

    /**
     * Cleans up any invalid temporary `.tmp` model files.
     */
    fun deleteInvalidTemporaryModel() {
        try {
            modelDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".tmp")) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Copies and installs bundled model from APK assets to internal storage.
     */
    suspend fun installFromAsset(
        assetPath: String = "models/${selectedModel.filename}",
        targetModel: WhisperModelType = selectedModel,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext Result.failure(IllegalStateException("Context is required to access APK assets"))
        try {
            ctx.assets.open(assetPath).use { input ->
                installFromInputStream(
                    inputStream = input,
                    targetModel = targetModel,
                    onProgress = onProgress
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Installs from a local source file with atomic rename and integrity verification.
     */
    suspend fun installFromLocalFile(
        sourceFile: File,
        targetModel: WhisperModelType = selectedModel,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || !sourceFile.canRead()) {
            return@withContext Result.failure(IllegalArgumentException("Source model file not found or not readable: ${sourceFile.absolutePath}"))
        }
        FileInputStream(sourceFile).use { input ->
            installFromInputStream(
                inputStream = input,
                targetModel = targetModel,
                onProgress = onProgress
            )
        }
    }

    /**
     * Installs model stream into final target file using atomic temporary file write and SHA-256 verification.
     */
    suspend fun installFromInputStream(
        inputStream: InputStream,
        expectedSha256: String = selectedModel.sha256,
        expectedLength: Long = selectedModel.sizeBytes,
        targetModel: WhisperModelType = selectedModel,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val targetSize = if (expectedLength > 0L) expectedLength else targetModel.sizeBytes
        val targetHash = if (expectedSha256.isNotBlank()) expectedSha256 else targetModel.sha256
        val minFree = targetSize + (50L * 1024 * 1024)
        val available = getAvailableStorageBytes()
        if (available < minFree) {
            val err = "Not enough storage for ${targetModel.displayName}. Required: ~${minFree / (1024 * 1024)} MB, available: ${available / (1024 * 1024)} MB"
            Log.e(TAG, "[WHISPER_ERROR] $err")
            return@withContext Result.failure(IllegalStateException(err))
        }

        modelDir.mkdirs()
        val tempFile = File(modelDir, "${targetModel.filename}.tmp")
        if (tempFile.exists()) tempFile.delete()

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            var totalCopied = 0L

            FileOutputStream(tempFile).use { output ->
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    digest.update(buffer, 0, bytesRead)
                    totalCopied += bytesRead
                    if (targetSize > 0) {
                        val progress = (totalCopied.toFloat() / targetSize.toFloat()).coerceIn(0f, 1f)
                        onProgress(progress)
                    }
                }
                output.flush()
            }

            val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (targetSize > 0 && totalCopied != targetSize) {
                tempFile.delete()
                val err = "Model length mismatch for ${targetModel.filename}: expected $targetSize bytes, got $totalCopied bytes"
                Log.e(TAG, "[WHISPER_ERROR] $err")
                return@withContext Result.failure(IllegalStateException(err))
            }
            if (targetHash.isNotBlank() && !actualSha256.equals(targetHash, ignoreCase = true)) {
                tempFile.delete()
                val err = "SHA-256 check failed for ${targetModel.filename}: expected=$targetHash, actual=$actualSha256"
                Log.e(TAG, "[WHISPER_ERROR] $err")
                return@withContext Result.failure(SecurityException(err))
            }

            val finalFile = File(modelDir, targetModel.filename)
            if (finalFile.exists()) finalFile.delete()
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }

            writeMetadata(targetModel, actualSha256, finalFile.length())
            Log.i(TAG, "[WHISPER_MODEL] path=${finalFile.absolutePath} size=${finalFile.length()} sha256=$actualSha256 status=INSTALLED_READY")
            Result.success(finalFile)
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            Log.e(TAG, "[WHISPER_ERROR] Installation failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Computes the SHA-256 hash of a file.
     */
    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        FileInputStream(file).use { fis ->
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
