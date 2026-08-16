package com.floatingapps.app.core.session

import com.floatingapps.app.FloatableApp
import com.floatingapps.app.ShizukuShellManager
import com.floatingapps.app.core.window.FloatingWindowController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Single owner of the launch -> session-registration -> verification
 * pipeline. Both MainActivity's app list/favorites and
 * FloatingBubbleService's picker panel call this instead of hitting
 * [ShizukuShellManager] directly, so the two entry points can never drift
 * out of sync on session bookkeeping again - the audit's core finding was
 * that no single component owned floating-window lifecycle/state.
 *
 * Returns an immediate [LaunchOutcome] for the shell command itself; the
 * real "is it actually floating" answer settles asynchronously and is
 * observable via [FloatingSessionManager.sessions].
 *
 * v2_Batch7: before treating a tap as a brand new launch, this now checks
 * [FloatingSessionManager.sessionForApp] - if the app already has a
 * VERIFIED_FLOATING session THIS process run, the tap is routed to
 * [FloatingWindowController.bringToFront] instead. This is the fix for
 * P0 #4 "True Bring-to-Front": previously Favorites/app-list taps always
 * fired a fresh launch and hoped the OS deduplicated the window; now the
 * existing session is reused and the raise is verified, not assumed.
 */
object FloatingLaunchCoordinator {

    sealed class LaunchOutcome {
        object CommandSucceeded : LaunchOutcome()
        data class CommandFailed(val error: String) : LaunchOutcome()
        object NotReady : LaunchOutcome()
        /** Routed to bring-to-front instead of a fresh launch; the
         *  confirmed/duplicate/not-found verdict settles asynchronously,
         *  same as CommandSucceeded's floating verdict does. */
        object BringingToFront : LaunchOutcome()
    }

    fun launch(app: FloatableApp, scope: CoroutineScope): LaunchOutcome {
        if (!ShizukuShellManager.isReady()) return LaunchOutcome.NotReady

        val existing = FloatingSessionManager.sessionForApp(app)
        if (existing != null) {
            scope.launch { FloatingWindowController.bringToFront(app, existing.taskId) }
            return LaunchOutcome.BringingToFront
        }

        FloatingSessionManager.onLaunchStarted(app)
        val result = ShizukuShellManager.launchFloating(app.packageName, app.activityName)

        return if (result.contains("Error", ignoreCase = true)) {
            FloatingSessionManager.onFailedLaunch(app.sessionKey, result)
            LaunchOutcome.CommandFailed(result)
        } else {
            scope.launch { LaunchVerification.verify(app) }
            LaunchOutcome.CommandSucceeded
        }
    }
}
