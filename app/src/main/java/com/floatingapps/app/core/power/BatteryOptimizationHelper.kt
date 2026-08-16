package com.floatingapps.app.core.power

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Handles Doze/App-Standby battery-optimization exemption (the documented,
 * guaranteed API), plus best-effort deep links into OEM-specific
 * "autostart" / background-permission screens that stock Doze APIs don't
 * cover on heavily customized ROMs (MIUI, ColorOS, FuntouchOS, EMUI, Samsung
 * device care). OEM component names are undocumented and change between ROM
 * versions, so every launch attempt is wrapped and silently ignored on
 * failure - the standard Doze exemption above is the one path this class
 * guarantees will work.
 */
object BatteryOptimizationHelper {

    fun isIgnoringOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestExemptionIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    /**
     * Best-effort: opens the OEM autostart/background-activity screen for
     * known manufacturers. Returns true only if an activity was actually
     * found and launched; the caller should treat false as "not available /
     * not needed on this device" rather than an error.
     */
    fun tryOpenOemAutostartSettings(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val candidates = when {
            manufacturer.contains("xiaomi") -> listOf(
                "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            manufacturer.contains("oppo") -> listOf(
                "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity"
            )
            manufacturer.contains("vivo") -> listOf(
                "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
            manufacturer.contains("huawei") -> listOf(
                "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
            manufacturer.contains("samsung") -> listOf(
                "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity"
            )
            else -> emptyList()
        }
        for ((pkg, cls) in candidates) {
            try {
                val intent = Intent().apply {
                    component = ComponentName(pkg, cls)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // Component doesn't exist on this ROM/version - try the next
                // candidate, or fall through to "not available" for this OEM.
            }
        }
        return false
    }
}
