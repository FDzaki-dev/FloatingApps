package com.floatingapps.app.core.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Single source of truth for the SYSTEM_ALERT_WINDOW ("draw over other apps")
 * permission check + settings intent. Kept as a standalone object so both
 * MainActivity and FloatingBubbleService check it identically.
 */
object OverlayPermissionHelper {

    fun isGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun settingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )
}
