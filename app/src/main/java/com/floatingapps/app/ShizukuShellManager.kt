package com.floatingapps.app

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku

/**
 * Central place for all Shizuku plumbing: binding the privileged UserService,
 * checking permission/availability, and running the specific shell commands
 * Floating Apps needs (enabling freeform window support, launching an app in
 * a freeform/floating window).
 */
object ShizukuShellManager {

    private const val TARGET_PACKAGE = "com.floatingapps.app"
    private const val SERVICE_CLASS = "com.floatingapps.app.ShellUserService"

    private var service: IShellService? = null
    @Volatile var isBound: Boolean = false
        private set

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(TARGET_PACKAGE, SERVICE_CLASS)
    )
        .daemon(false)
        .processNameSuffix("shell")
        .version(1)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                service = IShellService.Stub.asInterface(binder)
                isBound = true
                enableFreeformSupport()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isBound = false
        }
    }

    fun isShizukuAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Exception) {
        false
    }

    fun hasPermission(): Boolean = try {
        isShizukuAvailable() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }

    fun requestPermission(requestCode: Int) {
        try {
            Shizuku.requestPermission(requestCode)
        } catch (e: Exception) {
            // Shizuku not available; caller UI already guards against this.
        }
    }

    fun bindIfNeeded() {
        if (isBound || !hasPermission()) return
        try {
            Shizuku.bindUserService(userServiceArgs, connection)
        } catch (e: Exception) {
            isBound = false
        }
    }

    fun isReady(): Boolean = isBound && service != null

    /** Makes the OS honor freeform windowing for launched activities, even
     *  ones that declare themselves as non-resizable. Safe to call repeatedly. */
    private fun enableFreeformSupport() {
        exec(arrayOf("settings", "put", "global", "enable_freeform_support", "1"))
        exec(arrayOf("settings", "put", "global", "force_resizable_activities", "1"))
    }

    /** Launches [packageName]/[activityName] in a freeform floating window. */
    fun launchFloating(packageName: String, activityName: String): String {
        val component = "$packageName/$activityName"
        return exec(arrayOf("am", "start", "--windowingMode", "5", "-n", component))
    }

    /** Raw dumpsys text for [packageName]'s activity/task state - used by
     *  core.session.LaunchVerification as a best-effort signal of whether a
     *  launch actually became a freeform window. Never throws; callers
     *  treat an error string like any other inconclusive result. */
    fun dumpActivityState(packageName: String): String =
        exec(arrayOf("dumpsys", "activity", "activities", packageName))

    /** Kills [packageName] outright - used by core.window.FloatingWindowController
     *  as the "close" half of window management. `am force-stop` is blunt
     *  (whole process, not just one window) but is a stable AM command
     *  across Android versions, unlike task-scoped remove commands whose
     *  shell syntax shifted across the Android 10 multi-window refactor. */
    fun forceStop(packageName: String): String =
        exec(arrayOf("am", "force-stop", packageName))

    /** Resizes an EXISTING task's window bounds - the "snap position" half
     *  of P0 #3 Window Management (core.window.WindowGeometry supplies the
     *  preset bounds, core.window.FloatingWindowController calls this).
     *  Uses the legacy `am task resize <id> <L,T,R,B>` bounds command,
     *  confirmed present in AOSP's ActivityManagerShellCommand help text
     *  (documented since Android 6) but - like every task-scoped command in
     *  this app - NOT re-verified against the Android 10+ ActivityStack→
     *  WindowContainer refactor that already forced forceStop()/close() to
     *  avoid task-scoped commands entirely. Best-effort by design: caller
     *  checks the returned string for an error signal, never assumes
     *  success just because this returned. */
    fun resizeTask(taskId: Int, left: Int, top: Int, right: Int, bottom: Int): String =
        exec(arrayOf("am", "task", "resize", taskId.toString(), "$left,$top,$right,$bottom"))

    private fun exec(cmd: Array<String>): String {
        return try {
            service?.execArr(cmd) ?: "Shizuku belum siap"
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }
}
