package com.floatingapps.app.core.window

/**
 * Best-effort extraction of an Android task ID for a package from raw
 * `dumpsys activity activities <pkg>` text - the same feed
 * [com.floatingapps.app.core.session.LaunchVerification] already uses to
 * decide freeform vs fullscreen.
 *
 * KNOWN LIMITATION (documented, not hidden - same honesty policy as
 * LaunchVerification/BatteryOptimizationHelper): dumpsys text layout is not
 * a stable cross-OEM/cross-API-level contract. Several token shapes have
 * been seen across Android versions ("...pkg/.Activity t42}",
 * "taskId=42", "Task{... #42"). This tries each pattern in order and
 * returns the first match - a heuristic signal, not a guaranteed parse.
 * Null means "could not determine", which callers must treat as
 * inconclusive, never as "task 0" or any other false default.
 */
object TaskIdParser {

    private val PATTERNS = listOf(
        Regex("""t(\d+)\}"""),          // "...pkg/.Activity t42}"
        Regex("""taskid[=:]\s*(\d+)"""), // "taskId=42" / "TaskId: 42"
        Regex("""task\s*#(\d+)""")       // "Task #42"
    )

    /** [dump] should be the lowercase dumpsys text; [packageName] is matched
     *  per-line so we only read task IDs from lines that actually mention
     *  the target app, not an unrelated task elsewhere in the dump. */
    fun findTaskId(dump: String, packageName: String): Int? {
        val needle = packageName.lowercase()
        val relevantLines = dump.lineSequence().filter { it.contains(needle) }
        for (line in relevantLines) {
            for (pattern in PATTERNS) {
                val match = pattern.find(line)
                if (match != null) {
                    return match.groupValues[1].toIntOrNull()
                }
            }
        }
        return null
    }
}
