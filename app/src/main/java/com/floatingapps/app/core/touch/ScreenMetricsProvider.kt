package com.floatingapps.app.core.touch

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager

/**
 * Resolves current usable screen bounds in pixels, tolerant of rotation,
 * foldable state changes, and multi-window resize. Used to clamp the
 * floating bubble's position so it can never be dragged (or left, after a
 * rotation) off-screen and become unreachable.
 */
object ScreenMetricsProvider {

    data class Bounds(val width: Int, val height: Int)

    fun current(context: Context): Bounds {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return fallback(context)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                Bounds(bounds.width(), bounds.height())
            } else {
                val point = Point()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealSize(point)
                Bounds(point.x, point.y)
            }
        } catch (e: Exception) {
            // Never let a metrics failure crash the overlay - fall back to a
            // conservative guess from resources instead.
            fallback(context)
        }
    }

    private fun fallback(context: Context): Bounds {
        val dm = context.resources.displayMetrics
        return Bounds(dm.widthPixels, dm.heightPixels)
    }

    /** Clamps [value] so a view of size [viewSize] stays fully inside [0, axisSize]. */
    fun clamp(value: Int, viewSize: Int, axisSize: Int): Int {
        if (axisSize <= viewSize) return 0
        return value.coerceIn(0, axisSize - viewSize)
    }
}
