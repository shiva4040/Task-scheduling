package com.taskscheduling.app.assistant

/**
 * Enumeration of discrete intents recognized by the offline assistant.
 * Additional intents can be added here cleanly without changing UI files.
 */
enum class CommandIntent(val displayName: String) {
    GREETING("Greeting"),
    CHANGE_WALLPAPER("Change Wallpaper"),
    GO_BACK("Go Back"),
    GO_HOME("Go Home"),
    SWIPE_LEFT("Swipe Left"),
    SWIPE_RIGHT("Swipe Right"),
    SWIPE_UP("Swipe Up"),
    SWIPE_DOWN("Swipe Down"),
    SCROLL_UP("Scroll Up"),
    SCROLL_DOWN("Scroll Down"),
    OPEN_YOUTUBE("Open YouTube"),
    OPEN_SETTINGS("Open Settings"),

    // Task Management Intents
    ADD_TASK("Add Task"),
    SHOW_TASKS("Show Tasks"),
    SHOW_TODAY_TASKS("Show Today's Tasks"),
    SHOW_COMPLETED_TASKS("Show Completed Tasks"),
    SHOW_OVERDUE_TASKS("Show Overdue Tasks"),
    COMPLETE_TASK("Complete Task"),
    DELETE_TASK("Delete Task"),
    EDIT_TASK("Edit Task"),
    RESCHEDULE_TASK("Reschedule Task"),
    SEARCH_TASKS("Search Tasks")
}
