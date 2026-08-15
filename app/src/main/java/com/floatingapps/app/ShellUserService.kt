package com.floatingapps.app

/**
 * Runs inside a separate process started by Shizuku with shell (or root) UID.
 * Do NOT use Android framework Context APIs here - this process is not a
 * normal app process, only plain JVM / java.io.* / java.lang.Process work
 * reliably. We only need to spawn shell commands, so that's all this does.
 *
 * Must have a public no-arg constructor (Shizuku instantiates it via reflection).
 */
class ShellUserService : IShellService.Stub() {

    override fun destroy() {
        // Shizuku calls this to ask the service process to exit.
        System.exit(0)
    }

    override fun execArr(command: Array<String>): String {
        return try {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output.trim()
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }
}
