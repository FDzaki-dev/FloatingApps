package com.floatingapps.app.core.session

import com.floatingapps.app.FloatableApp
import com.floatingapps.app.ShizukuShellManager
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
 */
object FloatingLaunchCoordinator {

    sealed class LaunchOutcome {
        object CommandSucceeded : LaunchOutcome()
        data class CommandFailed(val error: String) : LaunchOutcome()
        object NotReady : LaunchOutcome()
    }

    fun launch(app: FloatableApp, scope: CoroutineScope): LaunchOutcome {
        if (!ShizukuShellManager.isReady()) return LaunchOutcome.NotReady

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
