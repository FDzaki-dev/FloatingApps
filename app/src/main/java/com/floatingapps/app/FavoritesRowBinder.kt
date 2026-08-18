package com.floatingapps.app

import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout

object FavoritesRowBinder {

    /**
     * [onSlotLongClick] defaults to a no-op so old call sites don't need to
     * change. v2_Batch7 added it for closing an already-floating favorite;
     * v2_Batch9 widens it to `(View, FloatableApp) -> Unit` - the anchor
     * View is needed to show a PopupMenu of window-position actions
     * (Maximize/Snap/Restore/Close) right at the slot, not just a single
     * close action.
     *
     * [isLive] closes the other half of the same P1 #14 debt: a favorite
     * currently backed by a live floating session now gets a visibly
     * different ring color (`favorite_slot_background_live`) instead of
     * looking identical to a non-floating one - previously undiscoverable
     * without tapping.
     */
    fun bind(
        container: LinearLayout,
        allApps: List<FloatableApp>,
        onSlotTap: (FloatableApp) -> Unit,
        onEmptySlotTap: () -> Unit,
        onSlotLongClick: (View, FloatableApp) -> Unit = { _, _ -> },
        isLive: (FloatableApp) -> Boolean = { false }
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
                icon.setBackgroundResource(
                    if (isLive(app)) R.drawable.favorite_slot_background_live
                    else R.drawable.favorite_slot_background
                )
                slotView.setOnClickListener { onSlotTap(app) }
                slotView.setOnLongClickListener { onSlotLongClick(slotView, app); true }
            } else {
                icon.setImageDrawable(null)
                icon.alpha = 0.35f
                icon.setBackgroundResource(R.drawable.favorite_slot_background)
                slotView.setOnClickListener { onEmptySlotTap() }
            }
            container.addView(slotView)
        }
    }
}
