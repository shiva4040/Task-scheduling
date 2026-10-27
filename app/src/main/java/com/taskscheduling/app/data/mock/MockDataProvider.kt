package com.taskscheduling.app.data.mock

import com.taskscheduling.app.domain.model.AccessibilitySettings
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.ActivityRecord
import com.taskscheduling.app.domain.model.ActivityStatus
import com.taskscheduling.app.domain.model.AppSettings
import com.taskscheduling.app.domain.model.ChildProfile
import com.taskscheduling.app.domain.model.CoordinateAction
import com.taskscheduling.app.domain.model.ExecutionLogEntry
import com.taskscheduling.app.domain.model.RepeatOption
import com.taskscheduling.app.domain.model.ScheduleItem
import com.taskscheduling.app.domain.model.ScheduleWindow
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.domain.model.TaskCategory
import com.taskscheduling.app.domain.model.TaskType
import com.taskscheduling.app.domain.model.ThemeMode
import com.taskscheduling.app.domain.model.UseCaseItem
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.domain.model.WorkflowStep

object MockDataProvider {

    val tasks: List<Task> by lazy {
        listOf(
            Task(
                id = "task_dark_mode",
                title = "Turn On Dark Mode",
                description = "Pulls down notification shade and toggles Dark Theme quick switch.",
                category = TaskCategory.ACCESSIBILITY,
                type = TaskType.COORDINATE_WORKFLOW,
                iconName = "dark_mode",
                voiceTriggers = listOf("turn on dark mode", "dark theme", "night mode", "dark mode"),
                isFavorite = true,
                workflow = Workflow(
                    id = "wf_dark_mode",
                    name = "Enable Dark Mode",
                    steps = listOf(
                        WorkflowStep(
                            id = "dm_1",
                            stepNumber = 1,
                            label = "Pull Down Notification Shade",
                            actionType = ActionType.SWIPE,
                            coordinate = CoordinateAction(x = 540, y = 60, endX = 540, endY = 850),
                            durationMs = 600L,
                            explanation = "Swipe down from top bar to open Quick Settings",
                            targetScreen = "QuickSettings"
                        ),
                        WorkflowStep(
                            id = "dm_2",
                            stepNumber = 2,
                            label = "Tap Dark Theme Tile",
                            actionType = ActionType.TAP,
                            coordinate = CoordinateAction(x = 200, y = 480),
                            durationMs = 400L,
                            explanation = "Toggle the Dark Theme tile coordinate",
                            targetScreen = "QuickSettings"
                        ),
                        WorkflowStep(
                            id = "dm_3",
                            stepNumber = 3,
                            label = "Close Notification Shade",
                            actionType = ActionType.SWIPE,
                            coordinate = CoordinateAction(x = 540, y = 850, endX = 540, endY = 50),
                            durationMs = 500L,
                            explanation = "Swipe up to return to previous screen",
                            targetScreen = "Home"
                        )
                    )
                )
            ),
            Task(
                id = "task_text_size",
                title = "Increase Text Size",
                description = "Opens Display settings and increases system font size to Large for high readability.",
                category = TaskCategory.ACCESSIBILITY,
                type = TaskType.COORDINATE_WORKFLOW,
                iconName = "format_size",
                voiceTriggers = listOf("make text bigger", "increase font size", "bigger text", "larger font"),
                isFavorite = true,
                workflow = Workflow(
                    id = "wf_text_size",
                    name = "Increase Text Size",
                    steps = listOf(
                        WorkflowStep(
                            id = "ts_1",
                            stepNumber = 1,
                            label = "Open Settings App",
                            actionType = ActionType.TAP,
                            coordinate = CoordinateAction(x = 880, y = 1600),
                            durationMs = 400L,
                            explanation = "Tap Settings on home grid",
                            targetScreen = "Settings"
                        ),
                        WorkflowStep(
                            id = "ts_2",
                            stepNumber = 2,
                            label = "Tap Display",
                            actionType = ActionType.TAP,
                            coordinate = CoordinateAction(x = 540, y = 600),
                            durationMs = 400L,
                            explanation = "Open Display and Text settings",
                            targetScreen = "Display"
                        ),
                        WorkflowStep(
                            id = "ts_3",
                            stepNumber = 3,
                            label = "Drag Font Slider to Right",
                            actionType = ActionType.SWIPE,
                            coordinate = CoordinateAction(x = 450, y = 680, endX = 820, endY = 680),
                            durationMs = 650L,
                            explanation = "Drag font scaling coordinate to Large",
                            targetScreen = "Display"
                        ),
                        WorkflowStep(
                            id = "ts_4",
                            stepNumber = 4,
                            label = "Return to Home",
                            actionType = ActionType.HOME,
                            coordinate = CoordinateAction(x = 540, y = 2300),
                            durationMs = 350L,
                            explanation = "Return to home screen with enlarged text",
                            targetScreen = "Home"
                        )
                    )
                )
            ),
            Task(
                id = "task_wifi",
                title = "Open Wi-Fi",
                description = "Navigates to Network settings and connects to preferred wireless network.",
                category = TaskCategory.SETTINGS,
                type = TaskType.COORDINATE_WORKFLOW,
                iconName = "wifi",
                voiceTriggers = listOf("open wifi", "connect wifi", "wifi", "connect to wifi"),
                workflow = Workflow(
                    id = "wf_wifi",
                    name = "Connect Wi-Fi",
                    steps = listOf(
                        WorkflowStep(
                            id = "wf_1",
                            stepNumber = 1,
                            label = "Open Settings App",
                            actionType = ActionType.TAP,
                            coordinate = CoordinateAction(x = 880, y = 1600),
                            durationMs = 400L,
                            explanation = "Open settings menu",
                            targetScreen = "Settings"
                        ),
                        WorkflowStep(
                            id = "wf_2",
                            stepNumber = 2,
                            label = "Tap Network & Internet",
                            actionType = ActionType.TAP,
                            coordinate = CoordinateAction(x = 540, y = 430),
                            durationMs = 400L,
                            explanation = "Open Network & Internet",
                            targetScreen = "Wifi"
                        ),
                        WorkflowStep(
                            id = "wf_3",
                            stepNumber = 3,
                            label = "Select Home 5G Network",
                            actionType = ActionType.TAP,
                            coordinate = CoordinateAction(x = 540, y = 480),
                            durationMs = 500L,
                            explanation = "Tap Home_HighSpeed_5G network",
                            targetScreen = "Wifi"
                        ),
                        WorkflowStep(
                            id = "wf_4",
                            stepNumber = 4,
                            label = "Return Home",
                            actionType = ActionType.HOME,
                            coordinate = CoordinateAction(x = 540, y = 2300),
                            durationMs = 350L,
                            explanation = "Return home with active connection",
                            targetScreen = "Home"
                        )
                    )
                )
            ),
            Task(
                id = "task_bluetooth",
                title = "Enable Bluetooth",
                description = "Pulls down quick settings and turns on Bluetooth radio.",
                category = TaskCategory.SETTINGS,
                type = TaskType.QUICK_ACTION,
                iconName = "bluetooth",
                voiceTriggers = listOf("enable bluetooth", "turn on bluetooth", "bluetooth"),
                workflow = Workflow(
                    id = "wf_bluetooth",
                    name = "Enable Bluetooth",
                    steps = listOf(
                        WorkflowStep(
                            id = "bt_1",
                            stepNumber = 1,
                            label = "Pull Down Quick Settings",
                            actionType = ActionType.SWIPE,
                            coordinate = CoordinateAction(x = 540, y = 50, endX = 540, endY = 800),
                            durationMs = 600L,
                            targetScreen = "QuickSettings"
                        ),
                        WorkflowStep(
                            id = "bt_2",
                            stepNumber = 2,
                            label = "Tap Bluetooth Tile",
                            actionType = ActionType.TAP,
                            coordinate = CoordinateAction(x = 540, y = 320),
                            durationMs = 400L,
                            targetScreen = "QuickSettings"
                        ),
                        WorkflowStep(
                            id = "bt_3",
                            stepNumber = 3,
                            label = "Close Shade",
                            actionType = ActionType.SWIPE,
                            coordinate = CoordinateAction(x = 540, y = 800, endX = 540, endY = 50),
                            durationMs = 500L,
                            targetScreen = "Home"
                        )
                    )
                )
            ),
            Task(
                id = "task_morning",
                title = "Morning Mode",
                description = "Disables night light, sets volume to 70%, and checks weather.",
                category = TaskCategory.PRODUCTIVITY,
                type = TaskType.SCHEDULED_TASK,
                iconName = "wb_sunny",
                voiceTriggers = listOf("morning mode", "good morning", "start morning routine"),
                workflow = Workflow(
                    id = "wf_morning",
                    name = "Morning Mode Routine",
                    steps = listOf(
                        WorkflowStep(
                            id = "m_1",
                            stepNumber = 1,
                            label = "Tap Weather Widget",
                            actionType = ActionType.TAP,
                            coordinate = CoordinateAction(x = 540, y = 450),
                            durationMs = 400L,
                            targetScreen = "Home"
                        ),
                        WorkflowStep(
                            id = "m_2",
                            stepNumber = 2,
                            label = "Wait 1 Second",
                            actionType = ActionType.WAIT,
                            durationMs = 1000L,
                            targetScreen = "Home"
                        ),
                        WorkflowStep(
                            id = "m_3",
                            stepNumber = 3,
                            label = "Return Home",
                            actionType = ActionType.HOME,
                            coordinate = CoordinateAction(x = 540, y = 2300),
                            durationMs = 350L,
                            targetScreen = "Home"
                        )
                    )
                )
            ),
            Task(
                id = "task_study",
                title = "Study Mode",
                description = "Enables Do Not Disturb, mutes notification chime, and launches clock timer.",
                category = TaskCategory.FAMILY,
                type = TaskType.COORDINATE_WORKFLOW,
                iconName = "school",
                voiceTriggers = listOf("study mode", "start study", "focus mode"),
                workflow = Workflow(
                    id = "wf_study",
                    name = "Study Mode Focus",
                    steps = listOf(
                        WorkflowStep(
                            id = "st_1",
                            stepNumber = 1,
                            label = "Pull Down Quick Settings",
                            actionType = ActionType.SWIPE,
                            coordinate = CoordinateAction(x = 540, y = 50, endX = 540, endY = 800),
                            durationMs = 500L,
                            targetScreen = "QuickSettings"
                        ),
                        WorkflowStep(
                            id = "st_2",
                            stepNumber = 2,
                            label = "Tap Do Not Disturb",
                            actionType = ActionType.TAP,
                            coordinate = CoordinateAction(x = 880, y = 480),
                            durationMs = 400L,
                            targetScreen = "QuickSettings"
                        ),
                        WorkflowStep(
                            id = "st_3",
                            stepNumber = 3,
                            label = "Close Shade",
                            actionType = ActionType.SWIPE,
                            coordinate = CoordinateAction(x = 540, y = 800, endX = 540, endY = 50),
                            durationMs = 450L,
                            targetScreen = "Home"
                        )
                    )
                )
            ),
            Task(
                id = "task_night",
                title = "Night Mode",
                description = "Activates dark theme, lowers screen brightness, and turns on bedtime DND.",
                category = TaskCategory.ACCESSIBILITY,
                type = TaskType.SCHEDULED_TASK,
                iconName = "bedtime",
                voiceTriggers = listOf("night mode", "goodnight", "bedtime mode"),
                workflow = Workflow(
                    id = "wf_night",
                    name = "Night Mode",
                    steps = listOf(
                        WorkflowStep(
                            id = "nm_1",
                            stepNumber = 1,
                            label = "Pull Down Settings",
                            actionType = ActionType.SWIPE,
                            coordinate = CoordinateAction(x = 540, y = 50, endX = 540, endY = 800),
                            durationMs = 500L,
                            targetScreen = "QuickSettings"
                        ),
                        WorkflowStep(
                            id = "nm_2",
                            stepNumber = 2,
                            label = "Tap Dark Theme",
                            actionType = ActionType.TAP,
                            coordinate = CoordinateAction(x = 200, y = 480),
                            durationMs = 400L,
                            targetScreen = "QuickSettings"
                        ),
                        WorkflowStep(
                            id = "nm_3",
                            stepNumber = 3,
                            label = "Close Shade",
                            actionType = ActionType.SWIPE,
                            coordinate = CoordinateAction(x = 540, y = 800, endX = 540, endY = 50),
                            durationMs = 450L,
                            targetScreen = "Home"
                        )
                    )
                )
            ),
            Task(
                id = "task_camera",
                title = "Open Camera",
                description = "Taps camera icon on home dock to quickly access lens.",
                category = TaskCategory.PRODUCTIVITY,
                type = TaskType.QUICK_ACTION,
                iconName = "photo_camera",
                voiceTriggers = listOf("open camera", "take a picture", "camera"),
                workflow = Workflow(
                    id = "wf_camera",
                    name = "Launch Camera",
                    steps = listOf(
                        WorkflowStep(
                            id = "cam_1",
                            stepNumber = 1,
                            label = "Tap Camera Icon",
                            actionType = ActionType.TAP,
                            coordinate = CoordinateAction(x = 660, y = 1600),
                            durationMs = 400L,
                            targetScreen = "Home"
                        )
                    )
                )
            ),
            Task(
                id = "task_volume",
                title = "Increase Volume",
                description = "Simulates hardware volume up interaction to increase ringtone volume.",
                category = TaskCategory.SETTINGS,
                type = TaskType.QUICK_ACTION,
                iconName = "volume_up",
                voiceTriggers = listOf("increase volume", "volume up", "louder"),
                workflow = Workflow(
                    id = "wf_vol",
                    name = "Volume Up",
                    steps = listOf(
                        WorkflowStep(
                            id = "vol_1",
                            stepNumber = 1,
                            label = "Hardware Key Volume Up",
                            actionType = ActionType.CUSTOM_GESTURE,
                            coordinate = CoordinateAction(x = 340, y = 300),
                            durationMs = 300L,
                            explanation = "Press volume up key",
                            targetScreen = "Home"
                        )
                    )
                )
            ),
            Task(
                id = "task_call_mom",
                title = "Call Family (Mom)",
                description = "Opens phone app, taps Mom's speed dial card, and initiates cellular call.",
                category = TaskCategory.COMMUNICATION,
                type = TaskType.COORDINATE_WORKFLOW,
                iconName = "call",
                voiceTriggers = listOf("call mom", "call family", "dial mom", "phone mom"),
                isFavorite = true,
                workflow = Workflow(
                    id = "wf_call_mom",
                    name = "Call Mom",
                    steps = listOf(
                        WorkflowStep(
                            id = "call_1",
                            stepNumber = 1,
                            label = "Open Phone App",
                            actionType = ActionType.TAP,
                            coordinate = CoordinateAction(x = 200, y = 1600),
                            durationMs = 400L,
                            targetScreen = "Phone"
                        ),
                        WorkflowStep(
                            id = "call_2",
                            stepNumber = 2,
                            label = "Tap Mom in Favorites",
                            actionType = ActionType.TAP,
                            coordinate = CoordinateAction(x = 320, y = 480),
                            durationMs = 450L,
                            targetScreen = "Phone"
                        ),
                        WorkflowStep(
                            id = "call_3",
                            stepNumber = 3,
                            label = "Wait in Connected Call",
                            actionType = ActionType.WAIT,
                            durationMs = 1200L,
                            targetScreen = "Phone"
                        ),
                        WorkflowStep(
                            id = "call_4",
                            stepNumber = 4,
                            label = "Hang Up Call",
                            actionType = ActionType.TAP,
                            coordinate = CoordinateAction(x = 540, y = 1800),
                            durationMs = 400L,
                            targetScreen = "Phone"
                        ),
                        WorkflowStep(
                            id = "call_5",
                            stepNumber = 5,
                            label = "Return Home",
                            actionType = ActionType.HOME,
                            coordinate = CoordinateAction(x = 540, y = 2300),
                            durationMs = 350L,
                            targetScreen = "Home"
                        )
                    )
                )
            )
        )
    }

    val activities: List<ActivityRecord> by lazy {
        listOf(
            ActivityRecord(
                id = "act_1",
                taskTitle = "Turn On Wi-Fi",
                timestamp = "Today, 10:32 AM",
                durationText = "1.5s",
                status = ActivityStatus.COMPLETED,
                stepCount = 3,
                logs = listOf(
                    ExecutionLogEntry("10:32:01", 1, ActionType.SWIPE, "Swipe down quick settings", "SUCCESS", 420),
                    ExecutionLogEntry("10:32:02", 2, ActionType.TAP, "Tap Wi-Fi toggle", "SUCCESS", 390),
                    ExecutionLogEntry("10:32:02", 3, ActionType.HOME, "Home", "SUCCESS", 320)
                )
            ),
            ActivityRecord(
                id = "act_2",
                taskTitle = "Turn On Dark Mode",
                timestamp = "Yesterday, 8:45 PM",
                durationText = "1.8s",
                status = ActivityStatus.COMPLETED,
                stepCount = 3,
                logs = listOf(
                    ExecutionLogEntry("20:45:00", 1, ActionType.SWIPE, "Swipe down shade", "SUCCESS", 580),
                    ExecutionLogEntry("20:45:01", 2, ActionType.TAP, "Tap Dark Theme", "SUCCESS", 400),
                    ExecutionLogEntry("20:45:02", 3, ActionType.SWIPE, "Swipe up shade", "SUCCESS", 490)
                )
            ),
            ActivityRecord(
                id = "act_3",
                taskTitle = "Increase Text Size",
                timestamp = "Sep 28, 3:15 PM",
                durationText = "3.1s",
                status = ActivityStatus.COMPLETED,
                stepCount = 4
            )
        )
    }

    val childProfile: ChildProfile by lazy {
        ChildProfile(
            id = "child_1",
            name = "Alex",
            age = 12,
            avatarEmoji = "👦",
            screenTimeLimitMinutes = 120,
            gamingTimeLimitMinutes = 60,
            studyModeActive = false,
            schedules = listOf(
                ScheduleWindow(name = "Study Window", startTime = "4:00 PM", endTime = "7:00 PM"),
                ScheduleWindow(name = "Gaming Window", startTime = "7:00 PM", endTime = "8:00 PM"),
                ScheduleWindow(name = "Bedtime Window", startTime = "9:30 PM", endTime = "7:00 AM")
            ),
            allowedApps = listOf("Calculator", "Dictionary", "School Portal", "Audiobooks"),
            blockedApps = listOf("Arcade Games", "Social Media", "Video Streaming")
        )
    }

    val schedules: List<ScheduleItem> by lazy {
        listOf(
            ScheduleItem(
                id = "sch_1",
                taskTitle = "Night Mode",
                taskId = "task_night",
                time = "10:00 PM",
                repeatOption = RepeatOption.DAILY,
                isEnabled = true
            ),
            ScheduleItem(
                id = "sch_2",
                taskTitle = "Morning Mode",
                taskId = "task_morning",
                time = "07:00 AM",
                repeatOption = RepeatOption.WEEKDAYS,
                isEnabled = true
            ),
            ScheduleItem(
                id = "sch_3",
                taskTitle = "Study Mode",
                taskId = "task_study",
                time = "04:00 PM",
                repeatOption = RepeatOption.WEEKDAYS,
                isEnabled = false
            )
        )
    }

    val useCases: List<UseCaseItem> by lazy {
        listOf(
            UseCaseItem("uc_1", "Increase Text Size", "Makes all text large for comfortable reading without straining eyes.", "Accessibility", "format_size", "task_text_size"),
            UseCaseItem("uc_2", "Turn On Dark Mode", "Reduces glaring white light and battery consumption with high contrast.", "Accessibility", "dark_mode", "task_dark_mode"),
            UseCaseItem("uc_3", "Call Family Member", "Single-command speed dial to connect with Mom or loved ones.", "Elderly", "call", "task_call_mom"),
            UseCaseItem("uc_4", "Connect to Wi-Fi", "Automates navigating confusing network menus to connect instantly.", "Elderly", "wifi", "task_wifi"),
            UseCaseItem("uc_5", "Study Mode Lock", "Silences notifications and blocks distractions during homework.", "Family", "school", "task_study"),
            UseCaseItem("uc_6", "Night Bedtime Routine", "Turns off bright screens, activates DND, and dims phone for sleep.", "Daily Routine", "bedtime", "task_night")
        )
    }

    val defaultAccessibilitySettings = AccessibilitySettings()
    val defaultAppSettings = AppSettings()
}
