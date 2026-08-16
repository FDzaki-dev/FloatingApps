package com.floatingapps.app.core.touch

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import kotlin.math.abs

/**
 * Reusable drag handler for a WRAP_CONTENT overlay window (the bubble).
 *
 * Design decisions (mirrored in PROJECT_STATE.md):
 * - TOUCH CAPTURE VS PASSTHROUGH: capture only happens inside the bubble's
 *   own bounds. The overlay window is sized WRAP_CONTENT and flagged
 *   FLAG_NOT_FOCUSABLE, so any touch outside the bubble passes through to
 *   whatever app is under it automatically - no FLAG_NOT_TOUCHABLE region
 *   trickery required.
 * - TAP VS DRAG: resolved using the platform's own touch slop
 *   (ViewConfiguration.scaledTouchSlop), not a hardcoded pixel guess, so it
 *   matches the device/OEM's actual gesture sensitivity.
 * - BOUNDARY CONSTRAINTS: clamped on every ACTION_MOVE via
 *   ScreenMetricsProvider so the bubble can never be dragged off-screen and
 *   become unreachable/un-tappable.
 * - SNAP-TO-EDGE: on release, if the gesture was a drag (not a tap), the
 *   bubble animates to the nearest horizontal edge.
 */
class FloatingDragTouchListener(
    context: Context,
    private val windowManager: WindowManager,
    private val params: WindowManager.LayoutParams,
    private val onLayoutChanged: () -> Unit,
    private val onTap: () -> Unit,
    private val onDragStateChanged: (Boolean) -> Unit = {}
) : View.OnTouchListener {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val snapAnimator = SnapEdgeAnimator(windowManager)

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var velocityTracker: VelocityTracker? = null

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val appContext = view.context.applicationContext
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                snapAnimator.cancel()
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    isDragging = true
                    onDragStateChanged(true)
                }
                if (isDragging) {
                    val bounds = ScreenMetricsProvider.current(appContext)
                    val viewW = if (view.width > 0) view.width else view.measuredWidth
                    val viewH = if (view.height > 0) view.height else view.measuredHeight
                    params.x = ScreenMetricsProvider.clamp(initialX + dx.toInt(), viewW, bounds.width)
                    params.y = ScreenMetricsProvider.clamp(initialY + dy.toInt(), viewH, bounds.height)
                    safeUpdateLayout(view)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.recycle()
                velocityTracker = null
                if (isDragging) {
                    onDragStateChanged(false)
                    val bounds = ScreenMetricsProvider.current(appContext)
                    snapAnimator.snapToNearestEdge(view, params, bounds.width) { onLayoutChanged() }
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    onTap()
                }
                isDragging = false
                return true
            }
        }
        return false
    }

    /** Re-clamps the bubble into the new bounds after a rotation / config change. */
    fun onScreenBoundsChanged(view: View) {
        val appContext = view.context.applicationContext
        val bounds = ScreenMetricsProvider.current(appContext)
        val viewW = if (view.width > 0) view.width else view.measuredWidth
        val viewH = if (view.height > 0) view.height else view.measuredHeight
        params.x = ScreenMetricsProvider.clamp(params.x, viewW, bounds.width)
        params.y = ScreenMetricsProvider.clamp(params.y, viewH, bounds.height)
        safeUpdateLayout(view)
    }

    private fun safeUpdateLayout(view: View) {
        try {
            windowManager.updateViewLayout(view, params)
            onLayoutChanged()
        } catch (e: Exception) {
            // Window already removed (service stopping / permission revoked
            // mid-drag) - safe to ignore, the next touch event no-ops the same way.
        }
    }

    /** Call from Service.onDestroy()/removeBubble() to stop any in-flight animation. */
    fun release() {
        snapAnimator.cancel()
        velocityTracker?.recycle()
        velocityTracker = null
    }
}
