plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.taskscheduling.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.taskscheduling.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        noCompress += listOf("onnx", "txt", "wav", "bin")
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Embedded Offline Speech Recognition (Whisper.cpp, Sherpa-ONNX & Vosk)
    implementation(files("libs/whisper-android-1.0.0.aar"))
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))
    implementation("com.alphacephei:vosk-android:0.3.47@aar")
    implementation("net.java.dev.jna:jna:5.13.0@aar")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    debugImplementation(libs.androidx.ui.tooling)
}

// =========================================================================
// Whisper Model Verification & Deployment Tasks
// =========================================================================

val WHISPER_MODEL_FILENAME = "ggml-tiny-q5_1.bin"
val WHISPER_EXPECTED_SIZE_BYTES = 32152673L // ~30.66 MiB (32,152,673 bytes)
val WHISPER_EXPECTED_SHA256 = "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7"
val APP_PACKAGE_NAME = "com.taskscheduling.app"
val APP_LAUNCH_ACTIVITY = "com.taskscheduling.app/.MainActivity"

fun resolveAdb(project: Project): File {
    try {
        val androidExt = project.extensions.findByType(com.android.build.gradle.BaseExtension::class.java)
        if (androidExt?.adbExecutable?.exists() == true) {
            return androidExt.adbExecutable
        }
    } catch (_: Exception) {}

    val sdkDir = project.extensions.findByType(com.android.build.gradle.BaseExtension::class.java)?.sdkDirectory
        ?: File(System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: "")
    val isWin = System.getProperty("os.name").lowercase().contains("windows")
    val adbName = if (isWin) "adb.exe" else "adb"
    val candidate = File(sdkDir, "platform-tools/$adbName")
    if (candidate.exists()) return candidate

    return File(adbName)
}

fun runAdbCommand(adb: File, vararg args: String): Pair<Int, String> {
    val cmd = mutableListOf(adb.absolutePath).apply { addAll(args) }
    val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText().trim()
    val exit = process.waitFor()
    return Pair(exit, output)
}

fun getConnectedDevices(adb: File): List<String> {
    val (exit, out) = runAdbCommand(adb, "devices")
    if (exit != 0) return emptyList()
    val devices = mutableListOf<String>()
    out.lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("List of devices") && trimmed.endsWith("device")) {
            val serial = trimmed.split("\\s+".toRegex()).firstOrNull()
            if (!serial.isNullOrBlank()) devices.add(serial)
        }
    }
    return devices
}

tasks.register("verifyWhisperModel") {
    group = "verification"
    description = "Verifies the on-device Whisper model integrity (size and SHA-256)."

    doLast {
        val adb = resolveAdb(project)
        val devices = getConnectedDevices(adb)
        if (devices.isEmpty()) {
            throw GradleException("[WhisperVerify][ERROR] No connected Android device found via ADB. Connect your device and enable USB debugging.")
        }
        val targetDevice = devices.first()
        println("[WhisperVerify] Verifying model on device: $targetDevice")

        val (_, sizeOut) = runAdbCommand(adb, "-s", targetDevice, "shell", "run-as $APP_PACKAGE_NAME wc -c files/models/whisper/$WHISPER_MODEL_FILENAME 2>/dev/null")
        val size = sizeOut.trim().split("\\s+".toRegex()).firstOrNull()?.toLongOrNull() ?: 0L

        println("[WhisperVerify] Device filename: $WHISPER_MODEL_FILENAME")
        println("[WhisperVerify] Device size: $size bytes (Expected: $WHISPER_EXPECTED_SIZE_BYTES)")

        val (_, hashOut) = runAdbCommand(adb, "-s", targetDevice, "shell", "run-as $APP_PACKAGE_NAME sha256sum files/models/whisper/$WHISPER_MODEL_FILENAME 2>/dev/null")
        val hash = hashOut.trim().split("\\s+".toRegex()).firstOrNull() ?: "UNKNOWN"
        println("[WhisperVerify] Device SHA-256: $hash")
        println("[WhisperVerify] Expected SHA-256: $WHISPER_EXPECTED_SHA256")

        if (size == WHISPER_EXPECTED_SIZE_BYTES && hash.equals(WHISPER_EXPECTED_SHA256, ignoreCase = true)) {
            println("[WhisperVerify] STATUS: READY (Verified)")
        } else {
            println("[WhisperVerify] STATUS: INVALID / MISMATCH")
        }
    }
}

tasks.register("deployWhisperModel") {
    group = "deployment"
    description = "Deploys the Whisper Tiny Q5_1 model to the connected Android device via ADB."

    doLast {
        val adb = resolveAdb(project)
        val devices = getConnectedDevices(adb)
        if (devices.isEmpty()) {
            throw GradleException("[WhisperDeploy][ERROR] No connected Android device found via ADB.")
        }
        val targetDevice = devices.first()
        val localModel = File(project.rootDir, "dev-assets/whisper/$WHISPER_MODEL_FILENAME")
        if (!localModel.exists() || localModel.length() != WHISPER_EXPECTED_SIZE_BYTES) {
            throw GradleException("[WhisperDeploy][ERROR] Local model file not found at ${localModel.absolutePath}")
        }
        println("[WhisperDeploy] Pushing $WHISPER_MODEL_FILENAME to device $targetDevice...")
        runAdbCommand(adb, "-s", targetDevice, "push", localModel.absolutePath, "/data/local/tmp/$WHISPER_MODEL_FILENAME")
        runAdbCommand(adb, "-s", targetDevice, "shell", "run-as $APP_PACKAGE_NAME mkdir -p files/models/whisper")
        runAdbCommand(adb, "-s", targetDevice, "shell", "run-as $APP_PACKAGE_NAME cp /data/local/tmp/$WHISPER_MODEL_FILENAME files/models/whisper/$WHISPER_MODEL_FILENAME")
        println("[WhisperDeploy] Deployed $WHISPER_MODEL_FILENAME successfully.")
    }
}


