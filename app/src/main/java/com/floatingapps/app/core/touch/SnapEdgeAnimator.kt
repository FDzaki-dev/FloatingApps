package com.floatingapps.app.core.touch

import android.animation.ValueAnimator
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator

/**
 * Animates a floating overlay view's WindowManager.LayoutParams.x to the
 * nearest horizontal screen edge after a drag ends (Chat-Heads-style
 * snap-to-edge). Every WindowManager call is guarded: the view can be
 * removed mid-animation from another callback (service stopped, permission
 * revoked, panel toggled) since this runs on the main thread's animation
 * ticks, not inside a synchronized block.
 */
class SnapEdgeAnimator(private val windowManager: WindowManager) {

    private var animator: ValueAnimator? = null

    fun snapToNearestEdge(
        view: View,
        params: WindowManager.LayoutParams,
        screenWidth: Int,
        onUpdate: (() -> Unit)? = null
    ) {
        cancel()
        val viewWidth = if (view.width > 0) view.width else view.measuredWidth
        val targetX = if (params.x + viewWidth / 2 <= screenWidth / 2) {
            0
        } else {
            (screenWidth - viewWidth).coerceAtLeast(0)
        }
        if (targetX == params.x) return

        animator = ValueAnimator.ofInt(params.x, targetX).apply {
            duration = 220L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                params.x = anim.animatedValue as Int
                try {
                    windowManager.updateViewLayout(view, params)
                    onUpdate?.invoke()
                } catch (e: Exception) {
                    // View detached mid-flight - stop quietly, no crash.
                    cancel()
                }
            }
            start()
        }
    }

    fun cancel() {
        animator?.cancel()
        animator = null
    }
}
