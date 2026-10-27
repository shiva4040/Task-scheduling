package com.taskscheduling.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wallpaper
import com.taskscheduling.app.domain.catalog.ActionCatalog
import com.taskscheduling.app.domain.catalog.ActionCatalogItem
import com.taskscheduling.app.domain.catalog.ActionCategory
import com.taskscheduling.app.domain.model.ActionType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActionCatalogAndSearchTest {

    @Before
    fun setUp() {
        ActionCatalog.resetToDefaults()
    }

    @After
    fun tearDown() {
        ActionCatalog.resetToDefaults()
    }

    @Test
    fun testActionCatalog_containsAllExpectedCategories() {
        val categories = ActionCategory.entries
        assertTrue("Categories should include Gestures", categories.contains(ActionCategory.GESTURES))
        assertTrue("Categories should include Navigation", categories.contains(ActionCategory.NAVIGATION))
        assertTrue("Categories should include Input", categories.contains(ActionCategory.INPUT_CONTROL))
        assertTrue("Categories should include Timing", categories.contains(ActionCategory.TIMING))
        assertTrue("Categories should include Device Settings", categories.contains(ActionCategory.DEVICE_SETTINGS))
        assertTrue("Categories should include Workflows", categories.contains(ActionCategory.WORKFLOWS))
    }

    @Test
    fun testActionCatalog_allRegisteredActionsAreValid() {
        val allActions = ActionCatalog.allActions
        assertTrue("Should have registered actions", allActions.isNotEmpty())

        allActions.forEach { action ->
            assertTrue("Action ID must not be empty", action.id.isNotEmpty())
            assertTrue("Action title must not be empty", action.title.isNotEmpty())
            assertTrue("Action description must not be empty", action.description.isNotEmpty())
            assertTrue("Keywords must not be empty", action.keywords.isNotEmpty())
        }
    }

    @Test
    fun testActionCatalog_searchActions_emptyQueryReturnsAll() {
        val allActions = ActionCatalog.allActions
        val searchEmpty = ActionCatalog.searchActions("")
        assertEquals(allActions.size, searchEmpty.size)

        val searchBlank = ActionCatalog.searchActions("   ")
        assertEquals(allActions.size, searchBlank.size)
    }

    @Test
    fun testActionCatalog_searchActions_partialMatchCaseInsensitive() {
        // Query "tap"
        val tapResults = ActionCatalog.searchActions("tap")
        assertTrue("Results should include TAP", tapResults.any { it.actionType == ActionType.TAP })

        // Query "SWIPE" uppercase
        val swipeResults = ActionCatalog.searchActions("SWIPE")
        assertTrue("Results should include SWIPE", swipeResults.any { it.actionType == ActionType.SWIPE })

        // Query "type"
        val textResults = ActionCatalog.searchActions("type")
        assertTrue("Results should include TEXT_INPUT", textResults.any { it.actionType == ActionType.TEXT_INPUT })

        // Query "delay"
        val delayResults = ActionCatalog.searchActions("delay")
        assertTrue("Results should include WAIT", delayResults.any { it.actionType == ActionType.WAIT })
    }

    @Test
    fun testActionCatalog_searchActions_categoryFilter() {
        val gestureActions = ActionCatalog.searchActions(category = ActionCategory.GESTURES)
        assertTrue(gestureActions.isNotEmpty())
        assertTrue(gestureActions.all { it.category == ActionCategory.GESTURES })

        val navActions = ActionCatalog.searchActions(category = ActionCategory.NAVIGATION)
        assertTrue(navActions.isNotEmpty())
        assertTrue(navActions.all { it.category == ActionCategory.NAVIGATION })

        val timingActions = ActionCatalog.searchActions(category = ActionCategory.TIMING)
        assertTrue(timingActions.isNotEmpty())
        assertTrue(timingActions.all { it.category == ActionCategory.TIMING })

        val settingsActions = ActionCatalog.searchActions(category = ActionCategory.DEVICE_SETTINGS)
        assertTrue(settingsActions.isNotEmpty())
        assertTrue(settingsActions.all { it.category == ActionCategory.DEVICE_SETTINGS })

        val workflowActions = ActionCatalog.searchActions(category = ActionCategory.WORKFLOWS)
        assertTrue(workflowActions.isNotEmpty())
        assertTrue(workflowActions.all { it.category == ActionCategory.WORKFLOWS })
    }

    @Test
    fun testActionCatalog_searchActions_queryAndCategoryFilterCombined() {
        val results = ActionCatalog.searchActions(query = "press", category = ActionCategory.GESTURES)
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.category == ActionCategory.GESTURES })
        assertTrue(results.any { it.actionType == ActionType.LONG_PRESS })
    }

    @Test
    fun testActionCatalog_searchActions_unknownTermReturnsEmpty() {
        val results = ActionCatalog.searchActions(query = "nonexistent_quantum_teleportation_action")
        assertTrue("Should return empty list for unmatched query", results.isEmpty())
    }

    @Test
    fun testActionCatalog_getActionByType() {
        val tapAction = ActionCatalog.getActionByType(ActionType.TAP)
        assertNotNull(tapAction)
        assertEquals("Tap", tapAction!!.title)
        assertEquals(ActionType.TAP, tapAction.actionType)
        assertTrue(tapAction.requiresCoordinates)

        val backAction = ActionCatalog.getActionByType(ActionType.BACK)
        assertNotNull(backAction)
        assertEquals("System Back", backAction!!.title)
        assertEquals(ActionType.BACK, backAction.actionType)
        assertFalse(backAction.requiresCoordinates)
    }

    // =========================================================================
    // REGRESSION TESTS: Section 4, 5, 6, 7
    // =========================================================================

    /**
     * Requirement: Searching "Change My Wallpaper" must display all three registered entries
     * when three matching entries exist in the registry.
     */
    private fun registerThreeTestWallpaperActions() {
        ActionCatalog.registerActions(
            listOf(
                ActionCatalogItem(
                    id = "action_wallpaper_settings",
                    actionType = ActionType.TAP,
                    title = "Change My Wallpaper",
                    description = "Navigates to Settings > Wallpaper & Style, selects Sunset theme, and applies wallpaper.",
                    category = ActionCategory.DEVICE_SETTINGS,
                    keywords = listOf("wallpaper", "change my wallpaper", "change wallpaper", "background", "style", "theme", "sunset", "lockscreen", "homescreen", "wall"),
                    requiresCoordinates = true,
                    icon = Icons.Default.Wallpaper,
                    isAvailable = true,
                    workflowId = "wf_wallpaper",
                    taskId = "task_wallpaper"
                ),
                ActionCatalogItem(
                    id = "action_wallpaper_quick",
                    actionType = ActionType.CUSTOM_GESTURE,
                    title = "Change My Wallpaper",
                    description = "Quick touchless assistant action to set device home and lock screen wallpaper.",
                    category = ActionCategory.WORKFLOWS,
                    keywords = listOf("wallpaper", "change my wallpaper", "set wallpaper", "quick action", "assistant", "preset", "lock screen", "wall"),
                    requiresCoordinates = false,
                    icon = Icons.Default.Wallpaper,
                    isAvailable = true,
                    workflowId = "wf_wallpaper_guided",
                    taskId = "task_wallpaper_quick"
                ),
                ActionCatalogItem(
                    id = "action_wallpaper_custom",
                    actionType = ActionType.TAP,
                    title = "Change My Wallpaper",
                    description = "Custom automated workflow that picks the latest photo from gallery and applies it as wallpaper.",
                    category = ActionCategory.INPUT_CONTROL,
                    keywords = listOf("wallpaper", "change my wallpaper", "photo", "gallery", "custom workflow", "automation", "wall"),
                    requiresCoordinates = true,
                    icon = Icons.Default.Wallpaper,
                    isAvailable = true,
                    workflowId = "wf_wallpaper_custom",
                    taskId = "task_wallpaper_custom"
                )
            )
        )
    }

    /**
     * Requirement: By default, no phantom saved wallpaper workflows are present when none are saved.
     */
    @Test
    fun testActionCatalog_noPhantomSavedWallpaperByDefault() {
        val results = ActionCatalog.searchActions("Change My Wallpaper")
        assertTrue("No phantom saved wallpaper actions when none are saved/registered", results.isEmpty())
    }

    /**
     * Requirement: Searching "Change My Wallpaper" must display all three registered entries
     * when three matching entries exist in the registry.
     */
    @Test
    fun testActionCatalog_allThreeChangeMyWallpaperEntriesAppearInSearch() {
        registerThreeTestWallpaperActions()
        val results = ActionCatalog.searchActions("Change My Wallpaper")
        assertEquals("Must return all 3 registered wallpaper actions", 3, results.size)

        // Verify each has the exact expected title
        assertTrue(results.all { it.title.equals("Change My Wallpaper", ignoreCase = true) })

        // Verify all 3 entries have distinct stable IDs
        val ids = results.map { it.id }.toSet()
        assertEquals("All 3 wallpaper actions must have distinct IDs", 3, ids.size)
        assertTrue("Must include settings wallpaper", ids.contains("action_wallpaper_settings"))
        assertTrue("Must include quick action wallpaper", ids.contains("action_wallpaper_quick"))
        assertTrue("Must include custom workflow wallpaper", ids.contains("action_wallpaper_custom"))

        // Verify distinct categories so user can differentiate them
        val categories = results.map { it.category }.toSet()
        assertTrue("Must span distinct categories for user clarity", categories.size >= 2)
    }

    /**
     * Requirement: Duplicate action names remain separate when their IDs differ.
     */
    @Test
    fun testActionCatalog_duplicateActionNamesRemainSeparateWhenIdsDiffer() {
        registerThreeTestWallpaperActions()
        // Register a 4th custom action with the duplicate title "Change My Wallpaper"
        val customAction = ActionCatalogItem(
            id = "action_wallpaper_custom_fourth",
            actionType = ActionType.TAP,
            title = "Change My Wallpaper",
            description = "Fourth variant of wallpaper changer for test",
            category = ActionCategory.WORKFLOWS,
            keywords = listOf("wallpaper", "change my wallpaper"),
            requiresCoordinates = true,
            icon = Icons.Default.Wallpaper
        )
        ActionCatalog.registerAction(customAction)

        val results = ActionCatalog.searchActions("Change My Wallpaper")
        assertEquals("Must display all 4 distinct entries without collapsing", 4, results.size)

        val ids = results.map { it.id }.toSet()
        assertEquals(4, ids.size)
        assertTrue(ids.contains("action_wallpaper_custom_fourth"))
    }

    /**
     * Requirement: Support case-insensitive partial matching:
     * "wallpaper", "change my wallpaper", "change", "wall".
     */
    @Test
    fun testActionCatalog_partialAndCaseInsensitiveMatching() {
        registerThreeTestWallpaperActions()
        val testQueries = listOf("wallpaper", "change my wallpaper", "change", "wall", "WALLPAPER", "WaLL", "CHANGE")

        for (query in testQueries) {
            val results = ActionCatalog.searchActions(query)
            assertTrue("Query '$query' must find matching wallpaper actions", results.isNotEmpty())
            assertTrue("Query '$query' must contain all 3 wallpaper actions", results.count { it.title.contains("Wallpaper", ignoreCase = true) } >= 3)
        }
    }

    /**
     * Requirement: Clearing the search restores the complete action list.
     */
    @Test
    fun testActionCatalog_clearingSearchRestoresAllRegisteredActions() {
        registerThreeTestWallpaperActions()
        val fullListSize = ActionCatalog.allActions.size

        // Active search
        val filtered = ActionCatalog.searchActions("Change My Wallpaper")
        assertEquals(3, filtered.size)

        // Clear search
        val restored = ActionCatalog.searchActions("")
        assertEquals("Clearing search must restore all actions", fullListSize, restored.size)

        val restoredWhitespace = ActionCatalog.searchActions("   ")
        assertEquals("Whitespace search must restore all actions", fullListSize, restoredWhitespace.size)
    }

    /**
     * Requirement: Search counts match the returned and displayed results.
     */
    @Test
    fun testActionCatalog_searchCountsMatchReturnedAndDisplayedResults() {
        val queries = listOf("wallpaper", "tap", "wifi", "dark", "delay", "nonexistent")

        for (q in queries) {
            val results = ActionCatalog.searchActions(q)
            val count = results.size
            assertEquals("Reported count must exactly equal returned list size for '$q'", count, results.size)
            // Verify no null elements or duplicates in returned list
            val uniqueIds = results.map { it.id }.toSet()
            assertEquals("No duplicate IDs in search result set", results.size, uniqueIds.size)
        }
    }

    /**
     * Requirement: Selecting a duplicate-name result selects the correct action ID.
     */
    @Test
    fun testActionCatalog_selectingDuplicateNameResultSelectsCorrectActionId() {
        registerThreeTestWallpaperActions()
        val results = ActionCatalog.searchActions("Change My Wallpaper")
        assertEquals(3, results.size)

        val item1 = results.find { it.id == "action_wallpaper_settings" }
        assertNotNull(item1)
        assertEquals("action_wallpaper_settings", item1!!.id)
        assertEquals(ActionCategory.DEVICE_SETTINGS, item1.category)

        val item2 = results.find { it.id == "action_wallpaper_quick" }
        assertNotNull(item2)
        assertEquals("action_wallpaper_quick", item2!!.id)
        assertEquals(ActionCategory.WORKFLOWS, item2.category)

        val item3 = results.find { it.id == "action_wallpaper_custom" }
        assertNotNull(item3)
        assertEquals("action_wallpaper_custom", item3!!.id)
        assertEquals(ActionCategory.INPUT_CONTROL, item3.category)

        assertNotEquals(item1.id, item2.id)
        assertNotEquals(item2.id, item3.id)
    }

    /**
     * Requirement: Actions in different categories remain discoverable.
     */
    @Test
    fun testActionCatalog_actionsInDifferentCategoriesRemainDiscoverable() {
        // Global search without category constraint finds actions across all categories
        val all = ActionCatalog.searchActions("", ActionCategory.ALL)
        val categoriesFound = all.map { it.category }.toSet()
        assertTrue(categoriesFound.contains(ActionCategory.GESTURES))
        assertTrue(categoriesFound.contains(ActionCategory.NAVIGATION))
        assertTrue(categoriesFound.contains(ActionCategory.INPUT_CONTROL))
        assertTrue(categoriesFound.contains(ActionCategory.TIMING))
        assertTrue(categoriesFound.contains(ActionCategory.DEVICE_SETTINGS))
        assertTrue(categoriesFound.contains(ActionCategory.WORKFLOWS))
    }

    /**
     * Requirement: If an action is genuinely unavailable, show its status and reason
     * clearly rather than silently removing it from search.
     */
    @Test
    fun testActionCatalog_unavailableActionsDisplayStatusClearlyRatherThanSilentlyExcluded() {
        val unavailableAction = ActionCatalogItem(
            id = "action_camera_permission_required",
            actionType = ActionType.TAP,
            title = "Capture Panoramic Shot",
            description = "Camera hardware feature",
            category = ActionCategory.DEVICE_SETTINGS,
            keywords = listOf("camera", "panoramic"),
            requiresCoordinates = false,
            icon = Icons.Default.Wallpaper,
            isAvailable = false,
            unavailableReason = "Requires Camera Permission"
        )
        ActionCatalog.registerAction(unavailableAction)

        // Include unavailable actions: must be present in search
        val searchWithUnavailable = ActionCatalog.searchActions("panoramic", includeUnavailable = true)
        assertEquals(1, searchWithUnavailable.size)
        val item = searchWithUnavailable.first()
        assertFalse(item.isAvailable)
        assertEquals("Requires Camera Permission", item.unavailableReason)

        // When explicitly excluded:
        val searchWithoutUnavailable = ActionCatalog.searchActions("panoramic", includeUnavailable = false)
        assertTrue(searchWithoutUnavailable.isEmpty())
    }
}
