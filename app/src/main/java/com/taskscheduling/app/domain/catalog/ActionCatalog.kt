package com.taskscheduling.app.domain.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.taskscheduling.app.domain.model.ActionType
import java.util.concurrent.CopyOnWriteArrayList

enum class ActionCategory(val displayName: String) {
    ALL("All Actions"),
    GESTURES("Touch & Gestures"),
    NAVIGATION("System Navigation"),
    INPUT_CONTROL("Input & Verification"),
    TIMING("Delays & Timing"),
    DEVICE_SETTINGS("Device & Settings"),
    WORKFLOWS("Automations & Workflows")
}

data class ActionCatalogItem(
    val id: String,
    val actionType: ActionType,
    val title: String,
    val description: String,
    val category: ActionCategory,
    val keywords: List<String> = emptyList(),
    val requiresCoordinates: Boolean = false,
    val icon: ImageVector,
    val isAvailable: Boolean = true,
    val unavailableReason: String? = null,
    val workflowId: String? = null,
    val taskId: String? = null,
    val executionTarget: String? = null
) {
    /**
     * Backward-compatible constructor for existing callers and tests
     */
    constructor(
        actionType: ActionType,
        title: String,
        description: String,
        category: ActionCategory,
        keywords: List<String>,
        requiresCoordinates: Boolean,
        icon: ImageVector
    ) : this(
        id = "action_${actionType.name.lowercase()}",
        actionType = actionType,
        title = title,
        description = description,
        category = category,
        keywords = keywords,
        requiresCoordinates = requiresCoordinates,
        icon = icon,
        isAvailable = true,
        unavailableReason = null,
        workflowId = null,
        taskId = null,
        executionTarget = null
    )
}

/**
 * Centralized global action registry.
 * Maintains all native primitive actions, composite workflows, and registered device tasks.
 * Preserves distinct action IDs for actions with duplicate names.
 */
object ActionCatalog {

    private val baseActions: List<ActionCatalogItem> = listOf(
        ActionCatalogItem(
            id = "action_tap",
            actionType = ActionType.TAP,
            title = "Tap",
            description = "Click or tap an exact button, icon, or coordinate target",
            category = ActionCategory.GESTURES,
            keywords = listOf("click", "press", "touch", "button", "target", "select", "activate", "point"),
            requiresCoordinates = true,
            icon = Icons.Default.TouchApp
        ),
        ActionCatalogItem(
            id = "action_double_tap",
            actionType = ActionType.DOUBLE_TAP,
            title = "Double Tap",
            description = "Quickly tap twice at coordinates to zoom or activate secondary actions",
            category = ActionCategory.GESTURES,
            keywords = listOf("double", "tap", "zoom", "photo", "like", "click"),
            requiresCoordinates = true,
            icon = Icons.Default.TouchApp
        ),
        ActionCatalogItem(
            id = "action_long_press",
            actionType = ActionType.LONG_PRESS,
            title = "Long Press",
            description = "Touch and hold target coordinate for specified duration (context menu)",
            category = ActionCategory.GESTURES,
            keywords = listOf("hold", "long click", "long press", "menu", "context", "drag start", "select"),
            requiresCoordinates = true,
            icon = Icons.Default.Schedule
        ),
        ActionCatalogItem(
            id = "action_swipe",
            actionType = ActionType.SWIPE,
            title = "Swipe",
            description = "Scroll up, down, left, or right between two screen coordinates",
            category = ActionCategory.GESTURES,
            keywords = listOf("scroll", "drag", "slide", "fling", "up", "down", "left", "right", "page"),
            requiresCoordinates = true,
            icon = Icons.Default.Swipe
        ),
        ActionCatalogItem(
            id = "action_drag",
            actionType = ActionType.DRAG,
            title = "Drag and Drop",
            description = "Press at start coordinate, move across screen, and release at target",
            category = ActionCategory.GESTURES,
            keywords = listOf("drag", "drop", "move", "reorder", "slider", "pull"),
            requiresCoordinates = true,
            icon = Icons.Default.DragHandle
        ),
        ActionCatalogItem(
            id = "action_custom_gesture",
            actionType = ActionType.CUSTOM_GESTURE,
            title = "Custom Gesture",
            description = "Multi-point curved gesture or custom continuous motion path",
            category = ActionCategory.GESTURES,
            keywords = listOf("custom", "gesture", "curve", "path", "multi-touch", "pattern"),
            requiresCoordinates = true,
            icon = Icons.Default.Gesture
        ),
        ActionCatalogItem(
            id = "action_back",
            actionType = ActionType.BACK,
            title = "System Back",
            description = "Trigger native Android back navigation to return to previous screen",
            category = ActionCategory.NAVIGATION,
            keywords = listOf("back", "return", "previous", "close", "dismiss", "exit"),
            requiresCoordinates = false,
            icon = Icons.AutoMirrored.Filled.ArrowBack
        ),
        ActionCatalogItem(
            id = "action_home",
            actionType = ActionType.HOME,
            title = "System Home",
            description = "Navigate directly to the Android home screen launcher",
            category = ActionCategory.NAVIGATION,
            keywords = listOf("home", "launcher", "desktop", "minimize", "main"),
            requiresCoordinates = false,
            icon = Icons.Default.Home
        ),
        ActionCatalogItem(
            id = "action_recents",
            actionType = ActionType.RECENTS,
            title = "Recent Apps",
            description = "Open the Android system task switcher / overview screen",
            category = ActionCategory.NAVIGATION,
            keywords = listOf("recents", "overview", "multitask", "switch", "tasks", "apps"),
            requiresCoordinates = false,
            icon = Icons.Default.Layers
        ),
        ActionCatalogItem(
            id = "action_text_input",
            actionType = ActionType.TEXT_INPUT,
            title = "Type Text Input",
            description = "Type automated string into currently focused text box or search field",
            category = ActionCategory.INPUT_CONTROL,
            keywords = listOf("type", "text", "input", "write", "search", "keyboard", "fill", "query"),
            requiresCoordinates = false,
            icon = Icons.Default.Edit
        ),
        ActionCatalogItem(
            id = "action_wait",
            actionType = ActionType.WAIT,
            title = "Wait Delay",
            description = "Pause workflow execution for a fixed duration in milliseconds",
            category = ActionCategory.TIMING,
            keywords = listOf("sleep", "wait", "delay", "pause", "timer", "hold", "rest"),
            requiresCoordinates = false,
            icon = Icons.Default.HourglassEmpty
        )
    )

    /**
     * Pre-registered application actions, workflows, and presets.
     */
    private val defaultRegisteredActions: List<ActionCatalogItem> = listOf(
        // Audited application actions
        ActionCatalogItem(
            id = "action_dark_mode",
            actionType = ActionType.SWIPE,
            title = "Turn On Dark Mode",
            description = "Pulls down notification shade and toggles Dark Theme quick switch.",
            category = ActionCategory.DEVICE_SETTINGS,
            keywords = listOf("dark mode", "dark theme", "night mode", "theme", "display", "contrast"),
            requiresCoordinates = true,
            icon = Icons.Default.DarkMode,
            isAvailable = true,
            workflowId = "wf_dark_mode",
            taskId = "task_dark_mode"
        ),
        ActionCatalogItem(
            id = "action_text_size",
            actionType = ActionType.TAP,
            title = "Increase Text Size",
            description = "Opens Display settings and increases system font size to Large for high readability.",
            category = ActionCategory.DEVICE_SETTINGS,
            keywords = listOf("font", "text", "size", "bigger text", "larger font", "display", "accessibility"),
            requiresCoordinates = true,
            icon = Icons.Default.FormatSize,
            isAvailable = true,
            workflowId = "wf_text_size",
            taskId = "task_text_size"
        ),
        ActionCatalogItem(
            id = "action_wifi",
            actionType = ActionType.TAP,
            title = "Open Wi-Fi",
            description = "Navigates to Network settings and connects to preferred wireless network.",
            category = ActionCategory.DEVICE_SETTINGS,
            keywords = listOf("wifi", "wi-fi", "internet", "network", "connect", "wireless"),
            requiresCoordinates = true,
            icon = Icons.Default.Wifi,
            isAvailable = true,
            workflowId = "wf_wifi",
            taskId = "task_wifi"
        ),
        ActionCatalogItem(
            id = "action_bluetooth",
            actionType = ActionType.SWIPE,
            title = "Enable Bluetooth",
            description = "Pulls down quick settings and turns on Bluetooth radio.",
            category = ActionCategory.DEVICE_SETTINGS,
            keywords = listOf("bluetooth", "wireless", "devices", "pair", "connect"),
            requiresCoordinates = true,
            icon = Icons.Default.Bluetooth,
            isAvailable = true,
            workflowId = "wf_bluetooth",
            taskId = "task_bluetooth"
        ),
        ActionCatalogItem(
            id = "action_morning_mode",
            actionType = ActionType.TAP,
            title = "Morning Mode",
            description = "Disables night light, sets volume to 70%, and checks weather.",
            category = ActionCategory.WORKFLOWS,
            keywords = listOf("morning mode", "good morning", "routine", "weather", "wake up"),
            requiresCoordinates = true,
            icon = Icons.Default.WbSunny,
            isAvailable = true,
            workflowId = "wf_morning",
            taskId = "task_morning"
        ),
        ActionCatalogItem(
            id = "action_study_mode",
            actionType = ActionType.SWIPE,
            title = "Study Mode",
            description = "Enables Do Not Disturb, mutes notification chime, and launches clock timer.",
            category = ActionCategory.WORKFLOWS,
            keywords = listOf("study mode", "focus", "dnd", "mute", "school", "timer"),
            requiresCoordinates = true,
            icon = Icons.Default.School,
            isAvailable = true,
            workflowId = "wf_study",
            taskId = "task_study"
        ),
        ActionCatalogItem(
            id = "action_night_mode",
            actionType = ActionType.SWIPE,
            title = "Night Mode",
            description = "Activates dark theme, lowers screen brightness, and turns on bedtime DND.",
            category = ActionCategory.WORKFLOWS,
            keywords = listOf("night mode", "bedtime", "goodnight", "sleep", "dark"),
            requiresCoordinates = true,
            icon = Icons.Default.Bedtime,
            isAvailable = true,
            workflowId = "wf_night",
            taskId = "task_night"
        ),
        ActionCatalogItem(
            id = "action_camera",
            actionType = ActionType.TAP,
            title = "Open Camera",
            description = "Taps camera icon on home dock to quickly access lens.",
            category = ActionCategory.DEVICE_SETTINGS,
            keywords = listOf("camera", "photo", "picture", "lens", "take a picture"),
            requiresCoordinates = true,
            icon = Icons.Default.CameraAlt,
            isAvailable = true,
            workflowId = "wf_camera",
            taskId = "task_camera"
        ),
        ActionCatalogItem(
            id = "action_volume",
            actionType = ActionType.CUSTOM_GESTURE,
            title = "Increase Volume",
            description = "Simulates hardware volume up interaction to increase ringtone volume.",
            category = ActionCategory.DEVICE_SETTINGS,
            keywords = listOf("volume", "volume up", "sound", "louder", "audio"),
            requiresCoordinates = true,
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            isAvailable = true,
            workflowId = "wf_vol",
            taskId = "task_volume"
        ),
        ActionCatalogItem(
            id = "action_call_mom",
            actionType = ActionType.TAP,
            title = "Call Family (Mom)",
            description = "Opens phone app, taps Mom's speed dial card, and initiates cellular call.",
            category = ActionCategory.WORKFLOWS,
            keywords = listOf("call", "call mom", "family", "phone", "dial"),
            requiresCoordinates = true,
            icon = Icons.Default.Call,
            isAvailable = true,
            workflowId = "wf_call_mom",
            taskId = "task_call_mom"
        )
    )

    private val customActions = CopyOnWriteArrayList<ActionCatalogItem>()

    /**
     * All registered actions in the system.
     * Guaranteed to preserve separate entries for duplicate names when IDs differ.
     */
    val allActions: List<ActionCatalogItem>
        get() {
            val list = mutableListOf<ActionCatalogItem>()
            val ids = mutableSetOf<String>()

            // 1. Base primitive gesture actions
            for (item in baseActions) {
                if (ids.add(item.id)) list.add(item)
            }
            // 2. Default registered workflows & actions
            for (item in defaultRegisteredActions) {
                if (ids.add(item.id)) list.add(item)
            }
            // 3. Dynamic / custom registered actions
            for (item in customActions) {
                if (ids.add(item.id)) list.add(item)
            }
            return list
        }

    fun registerAction(item: ActionCatalogItem) {
        customActions.removeAll { it.id == item.id }
        customActions.add(item)
    }

    fun registerActions(items: List<ActionCatalogItem>) {
        val newIds = items.map { it.id }.toSet()
        customActions.removeAll { it.id in newIds }
        customActions.addAll(items)
    }

    fun unregisterAction(id: String): Boolean {
        return customActions.removeIf { it.id == id }
    }

    fun clearCustomActions() {
        customActions.clear()
    }

    fun resetToDefaults() {
        customActions.clear()
    }

    /**
     * Synchronizes the Action Catalog with valid saved workflows from persistence.
     * Ensures only real saved workflows appear in the catalog as executable saved workflows.
     */
    fun syncWithWorkflows(workflows: List<com.taskscheduling.app.domain.model.Workflow>) {
        val workflowActions = workflows.map { wf ->
            ActionCatalogItem(
                id = "wf_action_${wf.id}",
                actionType = ActionType.CUSTOM_GESTURE,
                title = wf.name,
                description = wf.description.ifBlank { "Automated workflow with ${wf.steps.size} steps" },
                category = ActionCategory.WORKFLOWS,
                keywords = listOf(wf.name.lowercase(), "workflow", "automated", "saved") + wf.name.lowercase().split(" "),
                requiresCoordinates = wf.steps.any { it.requiresCoordinates },
                icon = Icons.Default.AutoAwesome,
                isAvailable = wf.steps.isNotEmpty(),
                workflowId = wf.id,
                taskId = null
            )
        }
        customActions.removeIf { it.id.startsWith("wf_action_") }
        customActions.addAll(workflowActions)
    }

    fun getActionById(id: String): ActionCatalogItem? {
        return allActions.find { it.id == id }
    }

    fun getActionByType(actionType: ActionType): ActionCatalogItem? {
        return allActions.find { it.actionType == actionType }
    }

    /**
     * Searches all registered actions.
     *
     * - Queries every registered action (not only a hardcoded subset).
     * - Supports case-insensitive partial matching (e.g. "dark mode", "wifi", "tap", "swipe").
     * - Returns all matching duplicate-name entries without merging or discarding.
     * - Supports category filtering while allowing ALL.
     * - Keeps unavailable actions visible with their status if includeUnavailable is true.
     */
    fun searchActions(
        query: String = "",
        category: ActionCategory = ActionCategory.ALL,
        includeUnavailable: Boolean = true
    ): List<ActionCatalogItem> {
        val trimmedQuery = query.trim().lowercase()

        return allActions.filter { item ->
            // Availability filter
            if (!includeUnavailable && !item.isAvailable) return@filter false

            // Category filter
            val matchesCategory = (category == ActionCategory.ALL || item.category == category)
            if (!matchesCategory) return@filter false

            // Empty query returns all actions matching the category
            if (trimmedQuery.isEmpty()) return@filter true

            // Substring / partial matching across all action metadata
            val matchesTitle = item.title.lowercase().contains(trimmedQuery)
            val matchesDescription = item.description.lowercase().contains(trimmedQuery)
            val matchesType = item.actionType.name.lowercase().contains(trimmedQuery)
            val matchesTypeLabel = item.actionType.label.lowercase().contains(trimmedQuery)
            val matchesCategoryName = item.category.displayName.lowercase().contains(trimmedQuery)
            val matchesId = item.id.lowercase().contains(trimmedQuery)
            val matchesKeywords = item.keywords.any { it.lowercase().contains(trimmedQuery) }

            matchesTitle || matchesDescription || matchesType || matchesTypeLabel || matchesCategoryName || matchesId || matchesKeywords
        }
    }
}
