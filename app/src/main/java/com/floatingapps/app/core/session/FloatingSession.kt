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
    val lastError: String? = null
)
