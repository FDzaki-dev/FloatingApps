package com.floatingapps.app.core.session

import com.floatingapps.app.FloatableApp
import com.floatingapps.app.ShizukuShellManager
import com.floatingapps.app.core.capability.CapabilityManager
import kotlinx.coroutines.delay

/**
 * Best-effort async check that an `am start --windowingMode 5` which
 * reported command-level success actually resulted in a freeform window -
 * not just a fullscreen launch. Polls `dumpsys activity activities` a few
 * times (some OEMs settle the windowing mode a beat after the activity
 * resumes) and looks for a `windowingMode=` token near the target package.
 *
 * KNOWN LIMITATION (documented, not hidden - see PROJECT_STATE.md
 * "Keterbatasan Jujur"): dumpsys text output is not a stable,
 * OEM-independent contract, so this is intentionally a heuristic signal,
 * not a certainty oracle. It still strictly improves on v2.2.0's behavior
 * of treating every non-erroring shell command as success.
 */
object LaunchVerification {

    private const val POLL_INTERVAL_MS = 400L
    private const val MAX_ATTEMPTS = 5

    private val FREEFORM_TOKENS = listOf("windowingmode=freeform", "windowingmode=5", "windowing_mode=5")
    private val FULLSCREEN_TOKENS = listOf("windowingmode=fullscreen", "windowingmode=1", "windowing_mode=1")

    suspend fun verify(app: FloatableApp) {
        val key = app.sessionKey
        repeat(MAX_ATTEMPTS) { attempt ->
            delay(POLL_INTERVAL_MS)
            val dump = try {
                ShizukuShellManager.dumpActivityState(app.packageName).lowercase()
            } catch (e: Exception) {
                ""
            }
            val isLastAttempt = attempt == MAX_ATTEMPTS - 1
            val containsPackage = dump.contains(app.packageName.lowercase())
            val isFreeform = containsPackage && FREEFORM_TOKENS.any { dump.contains(it) }
            val isFullscreen = containsPackage && FULLSCREEN_TOKENS.any { dump.contains(it) }

            when {
                isFreeform -> {
                    FloatingSessionManager.onVerified(key)
                    CapabilityManager.recordEmpiricalResult(true)
                    return
                }
                isFullscreen && isLastAttempt -> {
                    FloatingSessionManager.onFailedNotFloating(key)
                    CapabilityManager.recordEmpiricalResult(false)
                    return
                }
                isFullscreen -> {
                    // Retry - some OEMs report fullscreen briefly before
                    // settling into freeform on a later frame.
                }
                isLastAttempt -> {
                    // Inconclusive after all retries (task not found, or
                    // this OEM's dumpsys shape differs from what we parse).
                    // Fall back to a soft not-floating verdict instead of
                    // leaving the session stuck on LAUNCHING forever - but
                    // don't touch CapabilityManager, since "we couldn't
                    // tell" is not evidence of "it doesn't work".
                    FloatingSessionManager.onFailedNotFloating(key)
                }
            }
        }
    }
}
