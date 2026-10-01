# Task Scheduling — Native Android Frontend V1

> **“Tell the phone what you want to do, instead of learning how to navigate the phone.”**

Task Scheduling V1 has been built as a **Native Android Application** in Android Studio using **Kotlin** and **Jetpack Compose**. It provides touchless phone automation and accessibility via predefined screen coordinates, visual gesture execution, a realistic phone simulator, voice/chat commands, family profiles, accessibility settings, and task scheduling.

---

## 📱 Tech Stack & Android Architecture

* **Platform:** Native Android (Min SDK 26, Target SDK 35)
* **IDE:** Android Studio
* **Language:** Kotlin 2.0+
* **UI Toolkit:** Jetpack Compose (Material 3 with custom high-tech glass/slate design system)
* **Architecture Pattern:** Clean Architecture (UI &rarr; ViewModel &rarr; Domain &rarr; Repository &rarr; Automation Engine)
* **State Management:** Kotlin Coroutines & `StateFlow`
* **Navigation:** Jetpack Navigation Compose with type-safe route patterns

---

## 🏛️ Project Directory Structure

```text
app/
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/taskscheduling/app/
│   │   ├── MainActivity.kt               # Single Activity host with edge-to-edge Compose
│   │   ├── TaskSchedulingApp.kt          # Master Scaffold, Bottom Bar & Theme provider
│   │   │
│   │   ├── domain/model/                 # Pure domain models (independent from UI)
│   │   │   ├── Task.kt                   # Task, TaskCategory, TaskType
│   │   │   ├── Workflow.kt               # Workflow, WorkflowStep, ActionType, CoordinateAction
│   │   │   ├── Execution.kt              # ExecutionState, ExecutionSession, ExecutionLogEntry
│   │   │   └── AdditionalModels.kt       # ActivityRecord, ChildProfile, ScheduleItem, etc.
│   │   │
│   │   ├── data/
│   │   │   ├── repository/               # Task, Settings, Activity, Family repositories
│   │   │   └── mock/MockDataProvider.kt  # Predefined realistic tasks, use cases, schedules
│   │   │
│   │   ├── automation/                   # Automation Architecture & Interfaces
│   │   │   ├── AutomationInterfaces.kt   # AutomationEngine, DeviceController, VoiceEngine, ScreenAnalyzer
│   │   │   └── mock/
│   │   │       ├── MockAutomationEngine.kt  # StateFlow-driven execution simulator
│   │   │       └── MockEngines.kt           # MockVoiceEngine and MockScreenAnalyzer
│   │   │
│   │   ├── ui/
│   │   │   ├── theme/                    # Custom Color, Typography, and TaskSchedulingTheme
│   │   │   ├── navigation/               # Screen definitions and AppNavHost
│   │   │   └── components/
│   │   │       ├── PhonePreview.kt       # Realistic Android smartphone simulator frame
│   │   │       ├── CoordinateOverlay.kt  # Animated ripples, swipe vectors, and badges
│   │   │       ├── BottomNavigationBar.kt# 5 primary bottom tabs
│   │   │       └── TaskCard.kt           # Reusable task card with quick actions
│   │   │
│   │   └── feature/                      # Modular feature screens & ViewModels
│   │       ├── home/                     # Command area, quick actions, favorites
│   │       ├── tasks/                    # Task list, search, filters & 5-step creation flow
│   │       ├── builder/                  # Interactive coordinate workflow builder
│   │       ├── execution/                # Real-time simulation, progress & execution log
│   │       ├── assistant/                # Native chat UI assistant with action buttons
│   │       ├── voice/                    # Large pulse mic UI with simulated recognition
│   │       ├── accessibility/            # Simplification, contrast, touch visualizers
│   │       ├── family/                   # Child profile (Alex, 12), time limits, bedtime
│   │       ├── activity/                 # Execution history with detailed log inspection
│   │       ├── scheduler/                # Time-based scheduling and repeat rules
│   │       ├── usecases/                 # Real-life accessibility & elderly use case library
│   │       ├── settings/                 # App themes (Dark/Light/System), speed slider
│   │       ├── onboarding/               # 5-step introductory walkthrough
│   │       └── aipreview/                # Vision engine Observe-Understand-Act preview
│   │
│   └── res/                              # Android vector drawables, mipmaps, strings, colors
│
├── build.gradle.kts                      # App module build script (Compose enabled)
├── settings.gradle.kts                   # Project repositories and module include
└── gradle.properties                     # JVM configuration and AndroidX settings
```

---

## ⚡ Key Features Implemented

1. **Home Screen (`feature/home`):**
   * Personalized greeting (`"Good evening, Shiva"`).
   * Natural command prompt bar with voice and execute triggers.
   * Suggested command chips (`"Change wallpaper"`, `"Turn on dark mode"`, `"Increase text size"`, etc.).
   * Quick Actions row (Wallpaper, Dark Mode, Text Size, Wi-Fi, Bluetooth, Flashlight, Volume, Camera).
   * Favorites, Active Tasks, and Recent Activity sections.

2. **Coordinate Workflow Builder (`feature/builder`):**
   * **Realistic Phone Simulator:** Displays simulated screens (Home, Settings, Display, Wallpaper, Wi-Fi, Phone Call, Quick Settings).
   * **Interactive Tap-to-Pick:** Click/tap anywhere on the phone preview to calculate exact virtual `(0..1080, 0..2400)` coordinates and bind them to steps.
   * **Coordinate Radar Grid:** Toggleable crosshair grid for pixel alignment.
   * **Step Timeline:** Reorder steps up/down, duplicate, delete, and add new actions.
   * **Coordinate Inspector:** Panel to fine-tune X, Y, EndX, EndY, Action Type, and Duration.

3. **Live Execution Screen (`feature/execution`):**
   * Real-time visual feedback: radiating touch ripples, swipe trajectory lines, and coordinate pills.
   * Dynamic phone screen transitions as steps execute.
   * Step counter (`"STEP 3 OF 5"`) and progress bar.
   * Playback controls (Pause, Resume, Stop, Restart, Step Next).
   * Speed switcher (`0.5x`, `1.0x`, `2.0x`).
   * Collapsible execution log with timestamps, step numbers, coordinates, and latency.

4. **Task Management & 5-Step Creation Flow (`feature/tasks`):**
   * Search and filter tasks by categories (All, Settings, Accessibility, Family, Calling, Productivity, Custom).
   * Multi-step stateful task creation wizard (Step 1 Info &rarr; Step 2 Type &rarr; Step 3 Workflow &rarr; Step 4 Review &rarr; Step 5 Save).

5. **Voice & Chat Assistant (`feature/voice`, `feature/assistant`):**
   * Large pulsating microphone button with listening & processing animations.
   * Natural chat dialog with matching engine and actionable task buttons (`RUN TASK`, `VIEW`).

6. **Accessibility Center (`feature/accessibility`):**
   * Large Text, Large Controls, High Contrast, Reduce Motion.
   * Automation visualizer toggles (Show Touches, Show Coordinates, Show Execution Path, Step-by-Step Debug Mode).

7. **Family & Child Controls (`feature/family`):**
   * Child Profile (Alex, Age 12).
   * Screen time slider, gaming time slider, study mode override, schedule windows, and app restrictions.

8. **Scheduler & Use Cases (`feature/scheduler`, `feature/usecases`):**
   * Time-based task triggers with repeat rules (Every Day, Weekdays, Weekends, Custom).
   * Real-life use cases with "Run Demo" and "Customize" shortcuts.

9. **Onboarding & AI Vision Preview (`feature/onboarding`, `feature/aipreview`):**
   * 5-slide native onboarding walkthrough.
   * Version 2.0 AI Vision Observe &rarr; Understand &rarr; Act loop architecture preview.

---

## 🚀 How to Open in Android Studio

1. Open **Android Studio** (Ladybug / Hedgehog / Iguana / Koala or newer).
2. Select **File &rarr; Open...** and choose the directory:
   ```text
   d:\Shiva\code\Task scheduling
   ```
3. Allow Android Studio to sync the Gradle project.
4. Select an Android Emulator or connected physical Android device (API 26+).
5. Click **Run 'app'** (`Shift + F10`) to build and launch the native APK!
