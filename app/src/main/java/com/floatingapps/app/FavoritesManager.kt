package com.floatingapps.app

import android.content.Context

/**
 * Stores up to MAX_SLOTS pinned apps as simple "package::activity" keys in
 * SharedPreferences. Icons/labels are re-resolved at display time from the
 * already-loaded app list, so nothing heavy (like a Drawable) is persisted.
 */
object FavoritesManager {
    private const val PREFS = "floating_favorites"
    private const val KEY = "slots"
    const val MAX_SLOTS = 6

    private fun keyOf(app: FloatableApp) = "${app.packageName}::${app.activityName}"

    fun getFavoriteKeys(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split(";;").filter { it.isNotBlank() }
    }

    fun isFavorite(context: Context, app: FloatableApp): Boolean =
        getFavoriteKeys(context).contains(keyOf(app))

    /** Returns true if the app was added, false if it was removed. */
    fun toggleFavorite(context: Context, app: FloatableApp): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = keyOf(app)
        val current = getFavoriteKeys(context).toMutableList()
        val added: Boolean
        if (current.contains(key)) {
            current.remove(key)
            added = false
        } else {
            if (current.size >= MAX_SLOTS) {
                current.removeAt(0)
            }
            current.add(key)
            added = true
        }
        prefs.edit().putString(KEY, current.joinToString(";;")).apply()
        return added
    }

    /** Maps saved keys back to full FloatableApp entries (with icon/label) using the already-loaded app list. */
    fun resolveFavorites(context: Context, allApps: List<FloatableApp>): List<FloatableApp> {
        if (allApps.isEmpty()) return emptyList()
        val byKey = allApps.associateBy { "${it.packageName}::${it.activityName}" }
        return getFavoriteKeys(context).mapNotNull { byKey[it] }
    }
}
