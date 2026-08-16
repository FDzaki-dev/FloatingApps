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

    fun refresh(context: Context) {
        val overlay = OverlayPermissionHelper.isGranted(context)
        val shizuku = ShizukuShellManager.isReady()
        val battery = BatteryOptimizationHelper.isIgnoringOptimizations(context)
        val freeform = empiricalFreeform ?: staticFreeformSignal(context)

        val readiness = when {
            !overlay || !shizuku -> SystemReadiness.ACTION_REQUIRED
            freeform == false -> SystemReadiness.UNSUPPORTED
            !battery -> SystemReadiness.DEGRADED
            else -> SystemReadiness.READY
        }
        _snapshot.value = CapabilitySnapshot(readiness, overlay, shizuku, battery, freeform)
    }

    /**
     * Called with a real, observed launch-verification outcome. One
     * confirmed VERIFIED_FLOATING is treated as conclusive proof (true
     * always wins and sticks). A single failure is weaker evidence - it
     * could be one misbehaving app, not a platform limitation - so it only
     * downgrades to `false` once we have never seen a success.
     */
    fun recordEmpiricalResult(success: Boolean) {
        empiricalFreeform = when {
            success -> true
            empiricalFreeform == true -> true
            else -> false
        }
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
