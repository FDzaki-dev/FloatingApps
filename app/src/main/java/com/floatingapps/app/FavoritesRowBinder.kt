package com.floatingapps.app

import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout

object FavoritesRowBinder {

    /**
     * [onSlotLongClick] defaults to a no-op so existing call sites don't
     * need to change - v2_Batch7 adds it for closing an already-floating
     * favorite (core.window.FloatingWindowController.close) without
     * touching every previous binder call.
     */
    fun bind(
        container: LinearLayout,
        allApps: List<FloatableApp>,
        onSlotTap: (FloatableApp) -> Unit,
        onEmptySlotTap: () -> Unit,
        onSlotLongClick: (FloatableApp) -> Unit = {}
    ) {
        val context = container.context
        container.removeAllViews()
        val favorites = FavoritesManager.resolveFavorites(context, allApps)
        val inflater = LayoutInflater.from(context)

        for (i in 0 until FavoritesManager.MAX_SLOTS) {
            val slotView = inflater.inflate(R.layout.layout_favorite_slot, container, false)
            val icon = slotView.findViewById<ImageView>(R.id.ivFavoriteIcon)
            val app = favorites.getOrNull(i)
            if (app != null) {
                icon.setImageDrawable(app.icon)
                icon.alpha = 1f
                slotView.setOnClickListener { onSlotTap(app) }
                slotView.setOnLongClickListener { onSlotLongClick(app); true }
            } else {
                icon.setImageDrawable(null)
                icon.alpha = 0.35f
                slotView.setOnClickListener { onEmptySlotTap() }
            }
            container.addView(slotView)
        }
    }
}
