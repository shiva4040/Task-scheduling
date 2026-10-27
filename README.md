# Task Scheduling — Version 1.0

> **Offline Voice Assistance & Recorded Device Automation for Android**

**Task Scheduling** is a native Kotlin Android application built with Jetpack Compose that empowers users to automate device actions and schedule complex multi-step mobile workflows through physical coordinate recording, accessibility automation, and 100% on-device offline voice recognition.

---

## 🚀 Key Features

* **Abstract Luminous Glass Orb Assistant:**
  * Floating, draggable interactive overlay that pulses with context-aware luminous breathing, listening, thinking, speaking, and error states.
  * Collapsible into a minimal floating bubble that stays accessible over any app.
* **Floating Multi-Action Workflow Recorder:**
  * Record real-world user touch gestures directly on the device: Tap, Long Press, Swipe / Scroll, Node Clicks, Text Input, and Wait delays.
  * Live floating control bar: Pause/Resume, Step Counter, Undo Last Action, and Done/Save.
  * Real-time coordinate translation mapped to physical device display profiles.
* **Clean Create Workflow & Task Builder:**
  * Streamlined creation interface: Workflow Name, Optional Description, Recording Panel, and Step Review.
  * Reorder steps, edit parameters, duplicate, or manually add steps without clutter.
* **Native AccessibilityService Automation:**
  * Accurate, low-latency execution of recorded gestures using `AccessibilityService.dispatchGesture`.
  * Visual touch indicators, animated coordinate ripples, and real-time execution progress.
* **100% Offline Speech Recognition:**
  * Embedded **Whisper.cpp** (`ggml-tiny-q5_1.bin`) via high-performance native JNI.
  * Streaming **Sherpa-ONNX** Zipformer (int8) & **Vosk** fallback engines for instant offline transcription.
  * Complete voice bot diagnostic suite and wake-word / voice command integration.
* **Glassmorphic UI Design System:**
  * Material 3 dynamic theming supporting **Light Mode**, **Dark Mode**, and **System Default**.
  * Restrained indigo, cyan, and glass accents with responsive layouts for phones and tablets.
* **Robust Local Persistence:**
  * Atomic JSON file storage with automatic backup and crash-resilient persistence for tasks, custom workflows, and bot preferences.

---

## 📱 System Requirements

| Requirement | Specification |
| :--- | :--- |
| **Operating System** | Android 8.0 (API Level 26) or higher |
| **Target SDK** | Android 15 (API Level 35) |
| **CPU Architecture** | `arm64-v8a` (required for native Whisper and Sherpa-ONNX inference) |
| **Hardware Permissions** | Microphone (Audio Record), System Alert Window (Overlay), Accessibility Service |
| **Internet Access** | **None required.** Operates entirely offline without cloud dependencies. |

---

## 📥 Download & Installation

### Option 1: Direct APK Download (GitHub Releases)

1. Navigate to the official [Task Scheduling Releases Page](https://github.com/shiva4040/Task-scheduling/releases/tag/v1.0.0).
2. Download the `app-debug.apk` asset.
3. On your Android device, open the downloaded file.
4. If prompted, allow installation of unknown apps from your browser or file manager.
5. Tap **Install** to complete the setup.

### Option 2: Install via ADB

Ensure your device has USB Debugging enabled, then run:

```bash
adb install -r app-debug.apk
```

---

## ⚙️ Initial Setup & Required Permissions

To record and automate tasks across applications, **Task Scheduling** requires two system permissions:

1. **Accessibility Service:**
   * Go to **Settings &rarr; Accessibility &rarr; Installed Apps / Downloaded Services**.
   * Locate **Task Scheduling** and toggle it **ON**.
   * *Purpose:* Allows the app to dispatch recorded tap and swipe gestures.
2. **Display Over Other Apps (Overlay):**
   * Go to **Settings &rarr; Apps &rarr; Special App Access &rarr; Display over other apps**.
   * Locate **Task Scheduling** and grant permission.
   * *Purpose:* Enables the floating luminous orb and floating recorder panel.
3. **Microphone Permission:**
   * Granted when prompted within the app.
   * *Purpose:* Required for local offline voice command processing.

---

## 🛠️ Building from Source

### Prerequisites
* **Android Studio** Ladybug (2024.2) or newer.
* **JDK 17** configured as the Gradle JVM.
* **Android SDK** with Platform API 35 and NDK support.

### Build Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/shiva4040/Task-scheduling.git
   cd Task-scheduling
   ```
2. Build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
3. Run all unit tests:
   ```bash
   ./gradlew testDebugUnitTest
   ```
4. Install directly to a connected device:
   ```bash
   ./gradlew installDebug
   ```

The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🔒 Privacy & Security

* **Zero Telemetry:** The app contains no tracking libraries, analytics SDKs, or third-party advertising modules.
* **Local Audio Processing:** Voice input is processed exclusively on-device via local C++ Whisper and Sherpa-ONNX engines. No audio recordings or transcripts are ever transmitted over the network.
* **Local Data Storage:** All recorded tasks, coordinates, and execution logs reside in encrypted/sandboxed private app storage (`/data/user/0/com.taskscheduling.app/files/`).

---

## ⚠️ Known Limitations & Notes

* **Build Signing:** Version 1.0 is distributed as a verified debug build (`app-debug.apk`) for immediate installation and community testing without requiring custom signing keystores.
* **Screen Resolution Calibration:** Gestures recorded using absolute physical coordinates are calibrated to the recording device's display aspect ratio. Workflows may require recalibration if transferred across drastically different screen aspect ratios.

---

## 📄 License

This project is licensed for personal and open-source use under the repository terms.
