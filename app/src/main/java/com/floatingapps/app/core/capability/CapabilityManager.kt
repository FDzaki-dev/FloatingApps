package com.floatingapps.app.core.capability

import android.content.Context
import android.content.pm.PackageManager
import com.floatingapps.app.ShizukuShellManager
import com.floatingapps.app.core.overlay.OverlayPermissionHelper
import com.floatingapps.app.core.power.BatteryOptimizationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Combines Overlay + Shizuku + Battery + Freeform-support signals into one
 * [SystemReadiness]. Freeform support has no reliable static API on stock
 * Android: `PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT` is only
 * declared by a minority of devices, and its ABSENCE does NOT prove
 * freeform is unavailable - many phones support it via
 * `force_resizable_activities` without declaring the formal feature.
 *
 * So [freeformSupported] starts as "unknown" (null) and gets upgraded to
 * real true/false evidence the first time [com.floatingapps.app.core.
 * session.LaunchVerification] actually observes a launch outcome via
 * [recordEmpiricalResult] - empirical evidence beats static guessing, same
 * honesty principle the rest of this codebase already uses for OEM battery
 * quirks (see BatteryOptimizationHelper).
 */
object CapabilityManager {

    private val _snapshot = MutableStateFlow(
        CapabilitySnapshot(SystemReadiness.ACTION_REQUIRED, false, false, false, null)
    )
    val snapshot: StateFlow<CapabilitySnapshot> = _snapshot.asStateFlow()

    private var empiricalFreeform: Boolean? = null

    /**
     * Wrapped defensively (v2_Batch8, same lesson as the v2.4.1 crash
     * hotfix on `TaskIdParser`): an unexpected exception from any of the
     * individual probes here - most plausibly `ShizukuShellManager.isReady()`
     * if the Binder connection dies mid-check - must never crash the
     * caller (MainActivity.onResume/refreshUi). It now surfaces as
     * [SystemReadiness.ERROR], which was previously a defined-but-
     * unreachable enum value; this is what makes it real.
     */
    fun refresh(context: Context) {
        try {
            val overlay = OverlayPermissionHelper.isGranted(context)
            val shizuku = ShizukuShellManager.isReady()
            val battery = BatteryOptimizationHelper.isIgnoringOptimizations(context)
            val freeform = empiricalFreeform ?: staticFreeformSignal(context)

            _snapshot.value = CapabilitySnapshot(readinessFor(overlay, shizuku, battery, freeform), overlay, shizuku, battery, freeform)
        } catch (e: Exception) {
            val prev = _snapshot.value
            _snapshot.value = prev.copy(readiness = SystemReadiness.ERROR)
        }
    }

    private fun readinessFor(overlay: Boolean, shizuku: Boolean, battery: Boolean, freeform: Boolean?): SystemReadiness =
        when {
            !overlay || !shizuku -> SystemReadiness.ACTION_REQUIRED
            freeform == false -> SystemReadiness.UNSUPPORTED
            !battery -> SystemReadiness.DEGRADED
            else -> SystemReadiness.READY
        }

    /**
     * Called with a real, observed launch-verification outcome. One
     * confirmed VERIFIED_FLOATING is treated as conclusive proof (true
     * always wins and sticks). A single failure is weaker evidence - it
     * could be one misbehaving app, not a platform limitation - so it only
     * downgrades to `false` once we have never seen a success.
     *
     * v2_Batch8: now also republishes [snapshot] immediately using the
     * CURRENT snapshot's other fields (no Context needed) - previously
     * this only updated a private var, so the readiness banner wouldn't
     * reflect a fresh UNSUPPORTED/READY verdict until the next explicit
     * [refresh] call (e.g. next onResume). A user watching the banner
     * during their very first launch attempt deserves to see it update
     * live, not on their next app-switch.
     */
    fun recordEmpiricalResult(success: Boolean) {
        empiricalFreeform = when {
            success -> true
            empiricalFreeform == true -> true
            else -> false
        }
        val prev = _snapshot.value
        _snapshot.value = prev.copy(
            readiness = readinessFor(prev.overlayGranted, prev.shizukuReady, prev.batteryExempt, empiricalFreeform),
            freeformSupported = empiricalFreeform
        )
    }

    private fun staticFreeformSignal(context: Context): Boolean? {
        val hasFeature = try {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
        } catch (e: Exception) {
            false
        }
        // Presence proves support; absence proves nothing on most
        // real-world ROMs, so stay "unknown" rather than false-negative.
        return if (hasFeature) true else null
    }
}
