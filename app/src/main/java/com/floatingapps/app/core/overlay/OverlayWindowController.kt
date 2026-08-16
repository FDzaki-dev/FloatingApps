package com.floatingapps.app.core.overlay

import android.content.Context
import android.view.View
import android.view.WindowManager

/**
 * Thin, crash-safe wrapper around WindowManager.addView/updateViewLayout/
 * removeView. Every overlay window operation in this app goes through here
 * so failure modes (permission revoked mid-session, view already detached,
 * BadTokenException on some OEM skins, duplicate add) are handled in exactly
 * one place instead of being duplicated - and possibly forgotten - at every
 * call site across the service.
 */
class OverlayWindowController(context: Context) {

    private val windowManager: WindowManager =
        context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val addedViews = mutableSetOf<View>()

    /** Returns true if the view was actually added. Never throws. */
    fun add(view: View, params: WindowManager.LayoutParams): Boolean {
        if (view in addedViews) return true
        return try {
            windowManager.addView(view, params)
            addedViews += view
            true
        } catch (e: Exception) {
            // WindowManager.BadTokenException, permission revoked between the
            // caller's check and this call, or an OEM-specific overlay quirk.
            // Callers treat `false` as "not shown" and degrade gracefully.
            false
        }
    }

    fun update(view: View, params: WindowManager.LayoutParams): Boolean = try {
        if (view in addedViews) {
            windowManager.updateViewLayout(view, params)
            true
        } else {
            false
        }
    } catch (e: Exception) {
        // View detached concurrently (service stopping mid-drag/animation).
        false
    }

    fun remove(view: View?) {
        if (view == null) return
        try {
            if (view in addedViews) windowManager.removeView(view)
        } catch (e: Exception) {
            // Already detached - system may force-remove overlay views when
            // the permission is revoked while the service is still alive.
        } finally {
            addedViews -= view
        }
    }

    /** Called from Service.onDestroy() to guarantee zero leaked overlay views. */
    fun removeAll() {
        addedViews.toList().forEach { remove(it) }
    }

    fun manager(): WindowManager = windowManager
}
