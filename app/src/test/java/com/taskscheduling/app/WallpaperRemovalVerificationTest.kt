package com.taskscheduling.app

import com.taskscheduling.app.assistant.CommandIntent
import com.taskscheduling.app.assistant.CommandResolutionResult
import com.taskscheduling.app.assistant.LocalCommandProcessor
import com.taskscheduling.app.assistant.LocalCommandResolver
import com.taskscheduling.app.assistant.LocalWorkflowResolver
import com.taskscheduling.app.assistant.WorkflowResolution
import com.taskscheduling.app.data.mock.MockDataProvider
import com.taskscheduling.app.data.repository.WorkflowPersistenceRepository
import com.taskscheduling.app.domain.catalog.ActionCatalog
import com.taskscheduling.app.domain.model.ActionType
import com.taskscheduling.app.domain.model.Workflow
import com.taskscheduling.app.domain.model.WorkflowStep
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Verification test suite for Version 1.0 Wallpaper Removal:
 * 1. The built-in wallpaper action registry contains no wallpaper action.
 * 2. Searching for wallpaper returns no obsolete built-in action.
 * 3. Task Assistant does not report a nonexistent wallpaper workflow.
 * 4. Deleted built-in wallpaper templates are not recreated on restart.
 * 5. Unrelated saved workflows remain discoverable and executable.
 * 6. Existing user-created data is not silently destroyed.
 */
class WallpaperRemovalVerificationTest {

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
    fun test1_builtinWallpaperActionRegistryContainsNoWallpaperAction() {
        // Built-in ActionCatalog items must not include template_wallpaper_settings or any wallpaper action
        val allActions = ActionCatalog.allActions
        assertNull(
            "template_wallpaper_settings must not exist in ActionCatalog",
            ActionCatalog.getActionById("template_wallpaper_settings")
        )
        val wallpaperActions = allActions.filter { item ->
            item.id.contains("wallpaper", ignoreCase = true) ||
            item.title.contains("wallpaper", ignoreCase = true) ||
            item.description.contains("wallpaper", ignoreCase = true) ||
            item.keywords.any { it.contains("wallpaper", ignoreCase = true) }
        }
        assertTrue("Built-in action catalog must contain zero wallpaper actions, found: $wallpaperActions", wallpaperActions.isEmpty())

        // MockDataProvider tasks must not contain task_wallpaper
        assertFalse(
            "MockDataProvider tasks must not contain task_wallpaper",
            MockDataProvider.tasks.any { it.id == "task_wallpaper" }
        )
    }

    @Test
    fun test2_searchingForWallpaperReturnsNoObsoleteBuiltInAction() {
        val testQueries = listOf("wallpaper", "change wallpaper", "change my wallpaper", "new background", "wall paper")
        for (q in testQueries) {
            val results = ActionCatalog.searchActions(q)
            assertTrue("Searching for '$q' in ActionCatalog must return 0 actions", results.isEmpty())
        }
    }

    @Test
    fun test3_taskAssistantDoesNotReportNonexistentWallpaperWorkflow() {
        // Given no wallpaper workflow saved by user
        assertTrue(
            "No wallpaper workflow should be reported by resolver",
            workflowResolver.getWallpaperWorkflows().isEmpty()
        )
        assertNull(
            "Resolving CHANGE_WALLPAPER must return null when none saved",
            workflowResolver.resolveWorkflow(CommandIntent.CHANGE_WALLPAPER)
        )

        val result = commandProcessor.process("change my wallpaper")
        assertTrue(
            "Processor must return WorkflowNotFound for 'change my wallpaper'",
            result is CommandResolutionResult.WorkflowNotFound
        )
        val notFound = result as CommandResolutionResult.WorkflowNotFound
        assertEquals(CommandIntent.CHANGE_WALLPAPER, notFound.intent)
        assertEquals("Change Wallpaper", notFound.workflowName)
    }

    @Test
    fun test4_deletedBuiltInWallpaperTemplatesAreNotRecreatedOnRestart() {
        // 1. Initial boot with clean directory
        val repo1 = WorkflowPersistenceRepository(tempFolder.root)
        assertTrue(
            "Fresh repository must not initialize any default wallpaper workflows",
            repo1.getAllWorkflows().none { it.id.contains("wallpaper") }
        )

        // 2. Restart simulation: re-instantiate repo over existing folder
        val repo2 = WorkflowPersistenceRepository(tempFolder.root)
        assertTrue(
            "Restarted repository must not recreate deleted wallpaper templates",
            repo2.getAllWorkflows().none { it.id.contains("wallpaper") }
        )
        assertTrue(
            "ActionCatalog after restart must not have any wallpaper actions",
            ActionCatalog.searchActions("wallpaper").isEmpty()
        )
    }

    @Test
    fun test5_unrelatedSavedWorkflowsRemainDiscoverableAndExecutable() = runBlocking {
        val unrelatedWf = Workflow(
            id = "wf_daily_standup",
            name = "Daily Standup Routine",
            description = "Opens notes and messaging",
            steps = listOf(
                WorkflowStep(
                    id = "step_1",
                    order = 1,
                    type = ActionType.SWIPE,
                    startX = 400,
                    startY = 100,
                    endX = 400,
                    endY = 600,
                    description = "Swipe up"
                ),
                WorkflowStep(
                    id = "step_2",
                    order = 2,
                    type = ActionType.TAP,
                    startX = 200,
                    startY = 350,
                    description = "Tap Notes"
                )
            )
        )
        persistenceRepo.saveWorkflow(unrelatedWf)

        // Must be in persistence
        val saved = persistenceRepo.getWorkflow("wf_daily_standup")
        assertNotNull("Saved workflow must be retrievable", saved)
        assertEquals(2, saved!!.steps.size)

        // Must be synchronized with ActionCatalog
        val catalogResults = ActionCatalog.searchActions("Daily Standup")
        assertTrue("Unrelated workflow must be discoverable in ActionCatalog", catalogResults.isNotEmpty())
        assertEquals("wf_action_wf_daily_standup", catalogResults.first().id)

        // Must be resolvable by commandProcessor
        val resolution = workflowResolver.resolveByName("daily standup routine")
        assertTrue("Workflow must be resolved by name", resolution is WorkflowResolution.Found)
        val foundWf = (resolution as WorkflowResolution.Found).workflow
        assertEquals("wf_daily_standup", foundWf.id)

        val procResult = commandProcessor.process("daily standup routine")
        assertTrue("Command processor must return Success for unrelated workflow", procResult is CommandResolutionResult.Success)
    }

    @Test
    fun test6_existingUserCreatedDataIsNotSilentlyDestroyed() = runBlocking {
        // User created a custom workflow with custom steps
        val userWf = Workflow(
            id = "user_workflow_123",
            name = "My Personal Morning Routine",
            description = "Opens news and weather",
            steps = listOf(
                WorkflowStep(id = "us1", order = 1, type = ActionType.TAP, startX = 300, startY = 500, description = "Tap News"),
                WorkflowStep(id = "us2", order = 2, type = ActionType.TAP, startX = 600, startY = 800, description = "Tap Weather")
            )
        )
        persistenceRepo.saveWorkflow(userWf)

        // Reload from disk to simulate app killed and reopened
        val reloadedRepo = WorkflowPersistenceRepository(tempFolder.root)
        val loadedWf = reloadedRepo.getWorkflow("user_workflow_123")

        assertNotNull("User data must not be destroyed on reload", loadedWf)
        assertEquals("My Personal Morning Routine", loadedWf!!.name)
        assertEquals(2, loadedWf.steps.size)
        assertEquals(300, loadedWf.steps[0].startX)
        assertEquals(500, loadedWf.steps[0].startY)
    }
}
