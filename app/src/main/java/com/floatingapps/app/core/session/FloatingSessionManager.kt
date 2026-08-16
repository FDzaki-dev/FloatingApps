package com.floatingapps.app.core.session

import com.floatingapps.app.FloatableApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Stable identity key for session tracking - one entry per app component. */
val FloatableApp.sessionKey: String get() = "$packageName/$activityName"

/**
 * Single source of truth for "what is currently floating, or trying to be".
 * Before this (v2.2.0 and earlier), the app had no registry at all - a
 * launch was a fire-and-forget shell command paired with an optimistic
 * toast. Now every launch attempt is a tracked [FloatingSession] moving
 * through [FloatingSessionState], observable by any screen via [sessions].
 *
 * Scope of this batch (v2_Batch6): in-memory only, process lifetime. Full
 * disk persistence of position/size/session-across-restarts is a separate
 * later P0 item ("Persistent Floating State") - not conflated with this one
 * on purpose, per the audit's "jangan rewrite total" guidance.
 */
object FloatingSessionManager {

    private val _sessions = MutableStateFlow<Map<String, FloatingSession>>(emptyMap())
    val sessions: StateFlow<Map<String, FloatingSession>> = _sessions.asStateFlow()

    fun onLaunchStarted(app: FloatableApp) {
        val key = app.sessionKey
        _sessions.update { current ->
            current + (key to FloatingSession(
                packageName = app.packageName,
                activityName = app.activityName,
                label = app.label,
                state = FloatingSessionState.LAUNCHING,
                launchedAt = System.currentTimeMillis()
            ))
        }
    }

    fun onVerified(key: String) = transition(key, FloatingSessionState.VERIFIED_FLOATING) {
        it.copy(verifiedAt = System.currentTimeMillis())
    }

    fun onFailedNotFloating(key: String) = transition(key, FloatingSessionState.FAILED_NOT_FLOATING) {
        it.copy(verifiedAt = System.currentTimeMillis())
    }

    fun onFailedLaunch(key: String, error: String) = transition(key, FloatingSessionState.FAILED_LAUNCH) {
        it.copy(lastError = error)
    }

    fun onClosed(key: String) = transition(key, FloatingSessionState.CLOSED) { it }

    /** Sessions currently confirmed floating - foundation for the next
     *  batch's True Window Management / Bring-to-Front work. */
    fun activeFloatingSessions(): List<FloatingSession> =
        _sessions.value.values.filter { it.state == FloatingSessionState.VERIFIED_FLOATING }

    private fun transition(
        key: String,
        newState: FloatingSessionState,
        mutate: (FloatingSession) -> FloatingSession
    ) {
        _sessions.update { current ->
            val existing = current[key] ?: return@update current
            current + (key to mutate(existing.copy(state = newState)))
        }
    }
}
