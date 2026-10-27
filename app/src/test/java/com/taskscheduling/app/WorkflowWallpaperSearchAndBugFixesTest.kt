package com.taskscheduling.app

import com.taskscheduling.app.assistant.CommandIntent
import com.taskscheduling.app.assistant.CommandResolutionResult
import com.taskscheduling.app.assistant.LocalCommandProcessor
import com.taskscheduling.app.assistant.LocalCommandResolver
import com.taskscheduling.app.assistant.LocalWorkflowResolver
import com.taskscheduling.app.data.repository.WorkflowPersistenceRepository
import com.taskscheduling.app.domain.catalog.ActionCatalog
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.AppSettings
import com.taskscheduling.app.domain.model.ThemeMode
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.domain.model.WorkflowStep
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkflowWallpaperSearchAndBugFixesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var persistenceRepo: WorkflowPersistenceRepository
    private lateinit var commandResolver: LocalCommandResolver
    private lateinit var workflowResolver: LocalWorkflowResolver
    private lateinit var commandProcessor: LocalCommandProcessor

    @Before
    fun setUp() {
        ActionCatalog.resetToDefaults()
        persistenceRepo = WorkflowPersistenceRepository(tempFolder.root)
        commandResolver = LocalCommandResolver()
        workflowResolver = LocalWorkflowResolver(persistenceRepo)
        commandProcessor = LocalCommandProcessor(commandResolver, workflowResolver)
    }

    @After
    fun tearDown() {
        ActionCatalog.resetToDefaults()
    }

    @Test
    fun testWallpaper_noSavedWorkflowReturnsNullAndNotFound() {
        // Given NO wallpaper workflow is saved
        val savedWallpapers = workflowResolver.getWallpaperWorkflows()
        assertTrue("Must be empty when no workflow is saved", savedWallpapers.isEmpty())

        val resolved = workflowResolver.resolveWorkflow(CommandIntent.CHANGE_WALLPAPER)
        assertNull("Must return null when no valid wallpaper workflow exists", resolved)

        // CommandProcessor must report WorkflowNotFound
        val result = commandProcessor.process("change my wallpaper")
        assertTrue(
            "Must return WorkflowNotFound when no wallpaper workflow exists",
            result is CommandResolutionResult.WorkflowNotFound
        )
    }

    @Test
    fun testWallpaper_workflowWithZeroStepsIsNotTreatedAsValid() = runBlocking {
        // Saved workflow exists but has 0 steps
        val emptyWf = Workflow(
            id = "wf_wallpaper_empty",
            name = "Change Wallpaper",
            description = "Empty steps",
            steps = emptyList()
        )
        persistenceRepo.saveWorkflow(emptyWf)

        val resolved = workflowResolver.resolveWorkflow(CommandIntent.CHANGE_WALLPAPER)
        assertNull("Empty workflow with 0 steps must not resolve as valid executable workflow", resolved)
        assertTrue(workflowResolver.getWallpaperWorkflows().isEmpty())
    }

    @Test
    fun testWallpaper_singleSavedWorkflowResolvesSuccessfully() = runBlocking {
        val validWf = Workflow(
            id = "wf_wallpaper",
            name = "Change Wallpaper",
            description = "Applies sunset wallpaper",
            steps = listOf(
                WorkflowStep(
                    id = "step1",
                    order = 1,
                    type = ActionType.TAP,
                    startX = 500,
                    startY = 800,
                    description = "Wallpaper & Style"
                )
            )
        )
        persistenceRepo.saveWorkflow(validWf)

        val wallpapers = workflowResolver.getWallpaperWorkflows()
        assertEquals(1, wallpapers.size)
        assertEquals("wf_wallpaper", wallpapers.first().id)

        val resolved = workflowResolver.resolveWorkflow(CommandIntent.CHANGE_WALLPAPER)
        assertNotNull("Must find the single saved workflow", resolved)
        assertEquals("wf_wallpaper", resolved!!.id)

        val result = commandProcessor.process("change my wallpaper")
        assertTrue("Must succeed when exactly 1 workflow is saved", result is CommandResolutionResult.Success)
        val success = result as CommandResolutionResult.Success
        assertEquals("wf_wallpaper", success.workflow.id)
    }

    @Test
    fun testWallpaper_multipleSavedWorkflowsReturnAmbiguous() = runBlocking {
        val wf1 = Workflow(
            id = "wf_wallpaper_sunset",
            name = "Change Wallpaper (Sunset)",
            description = "Applies sunset theme",
            steps = listOf(WorkflowStep(id = "s1", order = 1, type = ActionType.TAP, description = "Settings"))
        )
        val wf2 = Workflow(
            id = "wf_wallpaper_nature",
            name = "Change Wallpaper (Nature)",
            description = "Applies nature theme",
            steps = listOf(WorkflowStep(id = "s2", order = 1, type = ActionType.TAP, description = "Gallery"))
        )
        persistenceRepo.saveWorkflow(wf1)
        persistenceRepo.saveWorkflow(wf2)

        val wallpapers = workflowResolver.getWallpaperWorkflows()
        assertEquals(2, wallpapers.size)

        // Multiple matches must trigger Ambiguous resolution so user can pick
        val result = commandProcessor.process("change my wallpaper")
        assertTrue("Must be ambiguous when multiple matches exist", result is CommandResolutionResult.AmbiguousWorkflows)
        val ambiguous = result as CommandResolutionResult.AmbiguousWorkflows
        assertEquals(2, ambiguous.candidates.size)
    }

    @Test
    fun testActionCatalog_syncWithPersistenceRepository() = runBlocking {
        val wf = Workflow(
            id = "wf_custom_test",
            name = "Open Device Settings",
            description = "Navigates to settings",
            steps = listOf(WorkflowStep(id = "s1", order = 1, type = ActionType.TAP, description = "Settings"))
        )
        persistenceRepo.saveWorkflow(wf)

        val search = ActionCatalog.searchActions("Open Device Settings")
        assertEquals(1, search.size)
        assertEquals("wf_action_wf_custom_test", search.first().id)
        assertEquals("wf_custom_test", search.first().workflowId)

        // Deleting workflow removes it from ActionCatalog
        persistenceRepo.deleteWorkflow(wf.id)
        val searchAfterDelete = ActionCatalog.searchActions("Open Device Settings")
        assertTrue("Must remove deleted workflow from catalog", searchAfterDelete.isEmpty())
    }

    @Test
    fun testAppSettings_newFieldsPersistCorrectly() {
        val customSettings = AppSettings(
            themeMode = ThemeMode.DARK,
            executionSpeed = 1.5f,
            stopOnError = true,
            confirmBeforeExecution = true,
            accentColorName = "Emerald",
            fontSizeScale = 1.15f,
            reducedMotion = true,
            overlayOrbEnabled = true,
            floatingOrbSizeDp = 64,
            floatingOrbOpacity = 0.8f,
            hapticFeedbackEnabled = true,
            stepDelayMs = 1000L,
            stepTimeoutSeconds = 30,
            autoClearCompletedTasks = true,
            userName = "Shiva",
            userEmail = "shiva@taskscheduling.local"
        )

        assertEquals(ThemeMode.DARK, customSettings.themeMode)
        assertEquals(1.5f, customSettings.executionSpeed)
        assertEquals("Emerald", customSettings.accentColorName)
        assertEquals(1.15f, customSettings.fontSizeScale)
        assertTrue(customSettings.reducedMotion)
        assertTrue(customSettings.overlayOrbEnabled)
        assertEquals(64, customSettings.floatingOrbSizeDp)
        assertEquals(1000L, customSettings.stepDelayMs)
    }
}
