package com.floatingapps.app.core.session

import android.content.Context

/**
 * Disk-backed slice of P0 #7 "Persistent Floating State". Honest scope
 * (documented, not silently narrowed): this persists WHICH apps had a
 * floating session and their last known state/label/timestamp, so the
 * registry survives process death instead of resetting to empty. It does
 * NOT persist window position/size, because this app doesn't own that
 * rendering - the OS's native freeform window chrome does (see
 * PROJECT_STATE.md "Kenapa Butuh Shizuku"), so there's no local
 * position/size to persist in the first place. Full window-geometry
 * persistence would only make sense once the resize/reposition control
 * layer (deferred, see core.window.FloatingWindowController) exists.
 *
 * Format mirrors [com.floatingapps.app.FavoritesManager]'s simple
 * delimited-string SharedPreferences style on purpose, to keep this
 * dependency-free (no JSON library) and consistent with the rest of the
 * codebase.
 */
object SessionPersistence {
    private const val PREFS = "floating_sessions"
    private const val KEY = "history"
    private const val MAX_ENTRIES = 20 // FIFO cap, same principle as CrashHandler's log retention
    private const val FIELD_SEP = "::"
    private const val ENTRY_SEP = ";;"

    data class PersistedEntry(
        val packageName: String,
        val activityName: String,
        val label: String,
        val state: FloatingSessionState,
        val timestamp: Long
    )

    fun save(context: Context, sessions: Map<String, FloatingSession>) {
        // Only persist sessions with a meaningful outcome - mid-flight
        // LAUNCHING never survives to be worth restoring as-is.
        val entries = sessions.values
            .filter { it.state != FloatingSessionState.LAUNCHING }
            .sortedByDescending { it.verifiedAt ?: it.launchedAt }
            .take(MAX_ENTRIES)
        val serialized = entries.joinToString(ENTRY_SEP) { s ->
            listOf(s.packageName, s.activityName, s.label, s.state.name, (s.verifiedAt ?: s.launchedAt).toString())
                .joinToString(FIELD_SEP)
        }
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, serialized).apply()
        } catch (e: Exception) {
            // Best-effort - a failed save should never crash a launch/close flow.
        }
    }

    fun load(context: Context): List<PersistedEntry> {
        val raw = try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        } catch (e: Exception) {
            ""
        }
        if (raw.isBlank()) return emptyList()
        return raw.split(ENTRY_SEP).mapNotNull { entry ->
            val parts = entry.split(FIELD_SEP)
            if (parts.size != 5) return@mapNotNull null
            val state = try {
                FloatingSessionState.valueOf(parts[3])
            } catch (e: Exception) {
                return@mapNotNull null
            }
            PersistedEntry(
                packageName = parts[0],
                activityName = parts[1],
                label = parts[2],
                state = state,
                timestamp = parts[4].toLongOrNull() ?: 0L
            )
        }
    }
}
