package com.floatingapps.app.core.session

import android.content.Context
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
 * v2_Batch6 scope: in-memory only, process lifetime.
 * v2_Batch7 adds: [init] wires optional disk persistence (P0 #7, honest
 * partial scope - see SessionPersistence doc for what is/isn't saved) and
 * [sessionForApp] powers True Bring-to-Front (P0 #4) by letting
 * FloatingLaunchCoordinator ask "is this app already floating?" before
 * deciding to relaunch vs raise the existing window.
 */
object FloatingSessionManager {

    private val _sessions = MutableStateFlow<Map<String, FloatingSession>>(emptyMap())
    val sessions: StateFlow<Map<String, FloatingSession>> = _sessions.asStateFlow()

    private var appContext: Context? = null

    /**
     * Call once, from [com.floatingapps.app.App.onCreate] - stores only the
     * Application context (process-lifetime, never leaks an Activity/
     * Service) and restores yesterday's session history as [
     * FloatingSessionState.RESTORED] entries. Safe to call more than once
     * (e.g. tests); later calls just refresh the stored context reference.
     */
    fun init(applicationContext: Context) {
        appContext = applicationContext
        if (_sessions.value.isEmpty()) {
            val restored = SessionPersistence.load(applicationContext).associate { entry ->
                val key = "${entry.packageName}/${entry.activityName}"
                key to FloatingSession(
                    packageName = entry.packageName,
                    activityName = entry.activityName,
                    label = entry.label,
                    state = FloatingSessionState.RESTORED,
                    launchedAt = entry.timestamp
                )
            }
            if (restored.isNotEmpty()) _sessions.value = restored
        }
    }

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
        persist()
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

    /** Records a best-effort task ID resolved via core.window.TaskIdParser -
     *  keeps state unchanged, this is metadata only. */
    fun onTaskIdResolved(key: String, taskId: Int) {
        _sessions.update { current ->
            val existing = current[key] ?: return@update current
            current + (key to existing.copy(taskId = taskId))
        }
        persist()
    }

    /** Confirmed same-window bring-to-front (core.window.FloatingWindowController) -
     *  stays VERIFIED_FLOATING, just refreshes the timestamp and clears any
     *  stale duplicate flag. */
    fun onBroughtToFront(key: String) = transition(key, FloatingSessionState.VERIFIED_FLOATING) {
        it.copy(verifiedAt = System.currentTimeMillis(), duplicateSuspected = false)
    }

    /** A bring-to-front attempt found a different task ID than expected -
     *  see FloatingSession.duplicateSuspected doc. Stays VERIFIED_FLOATING
     *  since a floating window does genuinely exist, just maybe not the one
     *  the user meant to raise. */
    fun onDuplicateWindowSuspected(key: String) = transition(key, FloatingSessionState.VERIFIED_FLOATING) {
        it.copy(duplicateSuspected = true)
    }

    /** Sessions currently confirmed floating THIS process run. Deliberately
     *  excludes RESTORED - those are unverified history, not live windows;
     *  see FloatingSessionState.RESTORED doc. Used by
     *  FloatingLaunchCoordinator to decide relaunch vs bring-to-front. */
    fun activeFloatingSessions(): List<FloatingSession> =
        _sessions.value.values.filter { it.state == FloatingSessionState.VERIFIED_FLOATING }

    /** Live (this-process, verified) session for [app], if any - the lookup
     *  True Bring-to-Front is built on. */
    fun sessionForApp(app: FloatableApp): FloatingSession? =
        _sessions.value[app.sessionKey]?.takeIf { it.state == FloatingSessionState.VERIFIED_FLOATING }

    private fun transition(
        key: String,
        newState: FloatingSessionState,
        mutate: (FloatingSession) -> FloatingSession
    ) {
        _sessions.update { current ->
            val existing = current[key] ?: return@update current
            current + (key to mutate(existing.copy(state = newState)))
        }
        persist()
    }

    private fun persist() {
        appContext?.let { SessionPersistence.save(it, _sessions.value) }
    }
}
