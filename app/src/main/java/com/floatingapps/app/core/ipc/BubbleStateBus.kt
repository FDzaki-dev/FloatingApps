package com.floatingapps.app.core.ipc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process pub/sub between FloatingBubbleService and MainActivity (or any
 * other UI observer).
 *
 * Deliberately NOT a cross-process Binder/AIDL channel: the service and the
 * activity run in the SAME app process (no android:process override on the
 * service in AndroidManifest.xml), so a real IPC channel would add Binder
 * overhead and failure modes for zero benefit. A process-wide StateFlow
 * singleton is the correct, idiomatic "IPC" boundary for same-process
 * Service<->UI communication.
 *
 * True cross-process IPC already exists separately where it's actually
 * required: ShellUserService runs in a Shizuku-spawned shell-UID process,
 * bridged via IShellService.aidl (see ShizukuShellManager).
 *
 * Collectors MUST use lifecycle-aware collection (repeatOnLifecycle) rather
 * than a raw coroutine launch, or they will leak the Activity/View - see
 * MainActivity.observeBubbleState().
 */
object BubbleStateBus {
    private val _isBubbleRunning = MutableStateFlow(false)
    val isBubbleRunning: StateFlow<Boolean> = _isBubbleRunning.asStateFlow()

    fun setBubbleRunning(running: Boolean) {
        _isBubbleRunning.value = running
    }
}
