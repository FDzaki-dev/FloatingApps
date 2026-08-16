package com.floatingapps.app.core.session

/**
 * Explicit lifecycle for a single floating-launch attempt. Replaces the old
 * implicit assumption "shell command succeeded = floating succeeded" - the
 * #1 gap flagged in the v2.2.0 Final Gap Audit ("Launched vs Actually
 * Floating" / "Failure & Recovery State").
 */
enum class FloatingSessionState {
    /** `am start` issued, async verification not settled yet. */
    LAUNCHING,

    /** Verification confirmed a real freeform/floating window. */
    VERIFIED_FLOATING,

    /** Command ran, but verification found the app fullscreen/non-floating,
     *  or could not confirm floating after retries - likely a device/ROM
     *  freeform-support gap. See core.capability.CapabilityManager. */
    FAILED_NOT_FLOATING,

    /** The shell command itself errored (Shizuku/exec failure) - never even
     *  got a chance to become a window. */
    FAILED_LAUNCH,

    /** Session ended (app closed, or superseded by a newer launch attempt
     *  for the same app). */
    CLOSED
}
