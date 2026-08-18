package com.floatingapps.app.core.window

import android.content.Context
import android.graphics.Rect
import com.floatingapps.app.core.touch.ScreenMetricsProvider

/**
 * Best-effort preset bounds for the floating-session "position" menu
 * (Maximize / Snap Left / Snap Right / Restore) - the safe, testable slice
 * of P0 #3 "True Window Management" that ships without a bespoke
 * drag-to-resize touch chrome (that subsystem is still deliberately
 * deferred - see [FloatingWindowController] class doc and PROJECT_STATE.md
 * "Sengaja TIDAK dikerjakan" history for why free-drag resize needs real
 * device testing before it lands).
 *
 * Bounds are computed from [ScreenMetricsProvider]'s CURRENT usable screen
 * size at call time (same source the bubble uses for boundary-clamping),
 * not whatever the screen size was when the session first launched, so a
 * preset picked after a rotation is still correct.
 */
object WindowGeometry {

    enum class Preset { MAXIMIZE, SNAP_LEFT, SNAP_RIGHT, RESTORE }

    /** RESTORE is a reasonable default floating size, NOT a remembered
     *  original size - this app has no way to know a window's size before
     *  the OS drew it (same "never guess" principle as
     *  [com.floatingapps.app.core.window.TaskIdParser]/taskId - see
     *  FloatingSession.taskId doc). */
    private const val RESTORE_WIDTH_FRACTION = 0.85f
    private const val RESTORE_HEIGHT_FRACTION = 0.75f

    fun forPreset(context: Context, preset: Preset): Rect {
        val screen = ScreenMetricsProvider.current(context)
        val w = screen.width
        val h = screen.height
        return when (preset) {
            Preset.MAXIMIZE -> Rect(0, 0, w, h)
            Preset.SNAP_LEFT -> Rect(0, 0, w / 2, h)
            Preset.SNAP_RIGHT -> Rect(w / 2, 0, w, h)
            Preset.RESTORE -> {
                val rw = (w * RESTORE_WIDTH_FRACTION).toInt()
                val rh = (h * RESTORE_HEIGHT_FRACTION).toInt()
                val left = (w - rw) / 2
                val top = (h - rh) / 2
                Rect(left, top, left + rw, top + rh)
            }
        }
    }
}
