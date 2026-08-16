package com.floatingapps.app.core.window

import com.floatingapps.app.FloatableApp
import com.floatingapps.app.ShizukuShellManager
import com.floatingapps.app.core.session.FloatingSessionManager
import com.floatingapps.app.core.session.sessionKey
import kotlinx.coroutines.delay

/**
 * Owns the two window-level operations Floating Apps can perform on an
 * ALREADY-tracked session: bring an existing floating window forward, and
 * close one. Answers P0 #4 "True Bring-to-Front" in full, and the
 * close/switch slice of P0 #3 "True Window Management" - resize/reposition/
 * maximize are intentionally NOT here yet (see PROJECT_STATE.md "Sengaja
 * TIDAK dikerjakan batch ini" for why: they need a per-window floating
 * control chrome, a real UI subsystem, not a shell-command wrapper).
 *
 * Both operations go through [FloatingSessionManager] so the registry is
 * always the source of truth for what's actually floating, same principle
 * [com.floatingapps.app.core.session.FloatingLaunchCoordinator] established
 * for launches.
 */
object FloatingWindowController {

    private const val VERIFY_DELAY_MS = 400L

    sealed class BringToFrontResult {
        /** Same task ID before/after - genuinely the same window, confirmed
         *  raised instead of a guess. */
        object Confirmed : BringToFrontResult()

        /** A task for this package exists after the attempt, but its ID
         *  differs from the one on record - most likely a NEW window got
         *  created instead of the old one being raised (e.g. an app using
         *  singleInstance/multipleTask). Session is updated to the new task
         *  ID so future attempts target the live window, and the mismatch
         *  is recorded on the session for later debugging - see
         *  PROJECT_STATE.md for why this isn't surfaced as its own toast
         *  yet (deferred P1 UX polish, not a correctness gap). */
        object PossibleDuplicate : BringToFrontResult()

        /** No task found at all after retry - the window is presumed gone. */
        object NotFound : BringToFrontResult()
    }

    /**
     * Re-issues the same freeform launch command for an app that
     * [FloatingSessionManager] already believes is VERIFIED_FLOATING, then
     * re-checks the task ID to confirm it's really the same window being
     * raised - not a blind "launch again and hope", which is exactly the
     * gap the v2.2.0 audit flagged for Favorites.
     */
    suspend fun bringToFront(app: FloatableApp, knownTaskId: Int?): BringToFrontResult {
        val key = app.sessionKey
        ShizukuShellManager.launchFloating(app.packageName, app.activityName)
        delay(VERIFY_DELAY_MS)

        val dump = try {
            ShizukuShellManager.dumpActivityState(app.packageName).lowercase()
        } catch (e: Exception) {
            ""
        }
        // Best-effort, defensively wrapped - see LaunchVerification's
        // matching fix (crash log dd2b3cf4) for why.
        val afterTaskId = try {
            TaskIdParser.findTaskId(dump, app.packageName)
        } catch (e: Exception) {
            null
        }

        return when {
            afterTaskId == null -> {
                FloatingSessionManager.onFailedNotFloating(key)
                BringToFrontResult.NotFound
            }
            knownTaskId == null || afterTaskId == knownTaskId -> {
                FloatingSessionManager.onTaskIdResolved(key, afterTaskId)
                FloatingSessionManager.onBroughtToFront(key)
                BringToFrontResult.Confirmed
            }
            else -> {
                FloatingSessionManager.onTaskIdResolved(key, afterTaskId)
                FloatingSessionManager.onDuplicateWindowSuspected(key)
                BringToFrontResult.PossibleDuplicate
            }
        }
    }

    /**
     * Closes a floating session via `am force-stop` - blunt (kills the
     * whole app process, not just the one window) but reliable across
     * Android versions/OEMs, unlike task-scoped remove commands whose shell
     * syntax has shifted across the Android 10 multi-window refactor. A
     * softer per-window close is a fair follow-up once the resize/reposition
     * control-chrome work lands (see class doc).
     */
    fun close(app: FloatableApp): Boolean {
        val result = ShizukuShellManager.forceStop(app.packageName)
        val ok = !result.contains("Error", ignoreCase = true)
        if (ok) FloatingSessionManager.onClosed(app.sessionKey)
        return ok
    }
}
