package com.floatingapps.app.core.window

import android.graphics.Rect
import com.floatingapps.app.FloatableApp
import com.floatingapps.app.ShizukuShellManager
import com.floatingapps.app.core.session.FloatingSessionManager
import com.floatingapps.app.core.session.sessionKey
import kotlinx.coroutines.delay

/**
 * Owns the window-level operations Floating Apps can perform on an
 * ALREADY-tracked session: bring an existing floating window forward,
 * resize/reposition it to a preset, and close it. Answers P0 #4 "True
 * Bring-to-Front" in full, and P0 #3 "True Window Management" for the
 * preset-position slice (Maximize/Snap Left/Snap Right/Restore via
 * [resize] + [WindowGeometry]) plus close/switch - free-drag custom resize
 * chrome remains deliberately deferred (see PROJECT_STATE.md "Sengaja TIDAK
 * dikerjakan" history: it needs a real touch-drag UI subsystem tested on an
 * actual device, which presets sidestep entirely by only ever sending
 * whole, pre-computed rectangles).
 *
 * All operations go through [FloatingSessionManager] so the registry is
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

    sealed class ResizeResult {
        /** Command sent, no error signal in the shell output. Like every
         *  best-effort shell command in this class, this is NOT a second
         *  verification pass (unlike bringToFront's before/after taskId
         *  check) - re-checking bounds via dumpsys is a fair follow-up, not
         *  something this batch claims to already do. */
        object Success : ResizeResult()

        /** Shell command ran but reported an error. */
        object Failed : ResizeResult()

        /** The session has no resolved task ID yet (see
         *  [com.floatingapps.app.core.session.FloatingSession.taskId] doc) -
         *  resize needs a REAL task ID and never guesses one. Caller should
         *  tell the user to bring the window to front once first (that's
         *  what resolves a task ID via [bringToFront] or first launch
         *  verification). */
        object NoTaskId : ResizeResult()
    }

    /**
     * Applies [bounds] (see [WindowGeometry] for preset rectangles) to
     * [app]'s currently-tracked task via `am task resize`. Purely a shell
     * wrapper - resolving what rectangle to send is the caller's job
     * (typically [WindowGeometry.forPreset]), keeping this object
     * context-free like [bringToFront]/[close].
     */
    fun resize(app: FloatableApp, bounds: Rect): ResizeResult {
        val taskId = FloatingSessionManager.sessionForApp(app)?.taskId
            ?: return ResizeResult.NoTaskId
        val result = ShizukuShellManager.resizeTask(
            taskId, bounds.left, bounds.top, bounds.right, bounds.bottom
        )
        return if (result.contains("Error", ignoreCase = true)) ResizeResult.Failed
        else ResizeResult.Success
    }

    /**
     * Closes a floating session via `am force-stop` - blunt (kills the
     * whole app process, not just the one window) but reliable across
     * Android versions/OEMs, unlike task-scoped remove commands whose shell
     * syntax has shifted across the Android 10 multi-window refactor. A
     * softer per-window close (kill just the task, not the process) is a
     * fair follow-up once a task-scoped remove command is verified reliable
     * on-device - same caveat [resize] already carries for `am task resize`.
     */
    fun close(app: FloatableApp): Boolean {
        val result = ShizukuShellManager.forceStop(app.packageName)
        val ok = !result.contains("Error", ignoreCase = true)
        if (ok) FloatingSessionManager.onClosed(app.sessionKey)
        return ok
    }
}
