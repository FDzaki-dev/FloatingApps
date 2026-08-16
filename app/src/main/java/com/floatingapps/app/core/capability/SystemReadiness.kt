package com.floatingapps.app.core.capability

/**
 * Unified health state for "can Floating Apps actually produce a floating
 * window right now" - single source of truth replacing the scattered
 * per-permission booleans the UI used to check ad-hoc (overlayGranted &&
 * shizukuReady, etc). See [CapabilityManager].
 */
enum class SystemReadiness {
    /** All prerequisites met - launching should work. */
    READY,

    /** Prerequisites met, but something non-blocking is missing (e.g. no
     *  battery exemption yet) - floating still works, may get killed in
     *  background by the OS. */
    DEGRADED,

    /** Overlay and/or Shizuku permission not granted yet - nothing has
     *  failed, user action is pending. */
    ACTION_REQUIRED,

    /** Best-effort detection indicates this device/ROM likely does not
     *  support freeform windowing. See [CapabilityManager] for how this is
     *  decided and why it can never be 100% certain up front. */
    UNSUPPORTED,

    /** Unexpected error while probing capability. */
    ERROR
}

data class CapabilitySnapshot(
    val readiness: SystemReadiness,
    val overlayGranted: Boolean,
    val shizukuReady: Boolean,
    val batteryExempt: Boolean,
    /** null = not yet known either way (no static signal, no empirical
     *  launch result yet). true/false once we have real evidence. */
    val freeformSupported: Boolean?
)
