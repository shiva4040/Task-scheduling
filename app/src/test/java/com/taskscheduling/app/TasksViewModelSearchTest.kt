package com.taskscheduling.app

import com.taskscheduling.app.data.mock.MockDataProvider
import com.taskscheduling.app.data.repository.TaskRepository
import com.taskscheduling.app.domain.model.Task
import com.taskscheduling.app.feature.tasks.TaskFilterTab
import com.taskscheduling.app.feature.tasks.TasksViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelSearchTest {

    private class FakeTaskRepository(initialTasks: List<Task> = MockDataProvider.tasks) : TaskRepository {
        val tasksFlow = MutableStateFlow(initialTasks)

        override fun getTasks(): Flow<List<Task>> = tasksFlow
        override suspend fun getTaskById(id: String): Task? = tasksFlow.value.find { it.id == id }
        override suspend fun saveTask(task: Task) {
            val list = tasksFlow.value.toMutableList()
            val idx = list.indexOfFirst { it.id == task.id }
            if (idx >= 0) list[idx] = task else list.add(0, task)
            tasksFlow.value = list
        }
        override suspend fun deleteTask(id: String) {
            tasksFlow.value = tasksFlow.value.filterNot { it.id == id }
        }
        override suspend fun duplicateTask(id: String) {}
        override suspend fun toggleFavorite(id: String) {
            tasksFlow.value = tasksFlow.value.map { if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it }
        }
        override suspend fun toggleEnabled(id: String) {}
        override suspend fun toggleCompleted(id: String): Task? = null
        override suspend fun rescheduleTask(id: String, newDueDate: String?, newDueTime: String?): Task? = null
        override fun findTaskByVoiceIntent(command: String): Task? = null
        override fun findTasksByQuery(query: String): List<Task> = emptyList()
    }

    private lateinit var fakeRepo: FakeTaskRepository
    private lateinit var viewModel: TasksViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeRepo = FakeTaskRepository()
        viewModel = TasksViewModel(fakeRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testGlobalSearch_discoversDarkModeTasksEvenWhenTodayTabSelected() = runTest {
        backgroundScope.launch {
            viewModel.filteredTasks.collect()
        }

        // Default tab is TODAY
        assertEquals(TaskFilterTab.TODAY, viewModel.filterState.value.selectedTab)

        // When searching for "dark mode", the tab date constraint must be bypassed
        viewModel.onSearchChange("dark mode")
        testScheduler.advanceUntilIdle()

        val filtered = viewModel.filteredTasks.value
        assertTrue("Searching for 'dark mode' must return matches regardless of tab", filtered.isNotEmpty())
        assertTrue(filtered.any { it.id == "task_dark_mode" })
        assertEquals("Turn On Dark Mode", filtered.first { it.id == "task_dark_mode" }.title)
    }

    @Test
    fun testGlobalSearch_caseInsensitivePartialMatching() = runTest {
        backgroundScope.launch {
            viewModel.filteredTasks.collect()
        }

        val queries = listOf("DARK", "dark", "Turn On", "dark mode")
        for (q in queries) {
            viewModel.onSearchChange(q)
            testScheduler.advanceUntilIdle()
            val filtered = viewModel.filteredTasks.value
            assertTrue("Query '$q' should discover dark mode task", filtered.any { it.id == "task_dark_mode" })
        }
    }

    @Test
    fun testGlobalSearch_clearingSearchRestoresOriginalTabFilter() = runTest {
        backgroundScope.launch {
            viewModel.filteredTasks.collect()
        }

        // 1. Search for dark mode
        viewModel.onSearchChange("dark mode")
        testScheduler.advanceUntilIdle()
        val searchCount = viewModel.filteredTasks.value.size
        assertTrue(searchCount > 0)

        // 2. Clear search
        viewModel.onSearchChange("")
        testScheduler.advanceUntilIdle()

        assertEquals("", viewModel.filterState.value.searchQuery)
        // Restores today tab filter
        assertEquals(TaskFilterTab.TODAY, viewModel.filterState.value.selectedTab)
    }

    @Test
    fun testGlobalSearch_matchesVoiceTriggersAndTaskIds() = runTest {
        backgroundScope.launch {
            viewModel.filteredTasks.collect()
        }

        // "dark theme" is in task_dark_mode voiceTriggers
        viewModel.onSearchChange("dark theme")
        testScheduler.advanceUntilIdle()
        val filtered = viewModel.filteredTasks.value
        assertTrue("Must match voice triggers", filtered.any { it.id == "task_dark_mode" })

        // "task_dark_mode" matches ID directly
        viewModel.onSearchChange("task_dark_mode")
        testScheduler.advanceUntilIdle()
        val byId = viewModel.filteredTasks.value
        assertTrue("Must match task ID directly", byId.any { it.id == "task_dark_mode" })
    }

    @Test
    fun testGlobalSearch_wallpaperQueriesReturnNoObsoleteTasks() = runTest {
        backgroundScope.launch {
            viewModel.filteredTasks.collect()
        }

        val wallpaperQueries = listOf("wallpaper", "change wallpaper", "change my wallpaper", "task_wallpaper", "new background")
        for (q in wallpaperQueries) {
            viewModel.onSearchChange(q)
            testScheduler.advanceUntilIdle()
            val filtered = viewModel.filteredTasks.value
            assertTrue(
                "Searching for '$q' must not return any wallpaper task",
                filtered.none { it.id.contains("wallpaper", ignoreCase = true) || it.title.contains("wallpaper", ignoreCase = true) }
            )
        }
    }
}
