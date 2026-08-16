package com.floatingapps.app.core.session

/**
 * One tracked floating-launch attempt/window. See [FloatingSessionManager]
 * for the registry that owns these, and [FloatingSessionState] for the
 * lifecycle it moves through.
 */
data class FloatingSession(
    val packageName: String,
    val activityName: String,
    val label: String,
    val state: FloatingSessionState,
    val launchedAt: Long,
    val verifiedAt: Long? = null,
    val lastError: String? = null,
    /** Best-effort task ID from the last successful dumpsys parse (see
     *  core.window.TaskIdParser). Null until a verified/bring-to-front
     *  check has actually resolved one - never guessed. Powers
     *  core.window.FloatingWindowController's same-window confirmation. */
    val taskId: Int? = null,
    /** Set when a bring-to-front attempt found a DIFFERENT task ID than
     *  the one on record - likely a second window got created instead of
     *  the original being raised. See FloatingWindowController.bringToFront
     *  doc for why this isn't its own dedicated UI warning yet. */
    val duplicateSuspected: Boolean = false
)
