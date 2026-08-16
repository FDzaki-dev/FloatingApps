package com.floatingapps.app

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.system.exitProcess

/**
 * Fail-safe uncaught exception handler.
 * - API 29+: writes to MediaStore Documents/FloatingApps/logs (no legacy storage permission).
 * - API 26-28: writes to app-specific external storage (also permission-free).
 * - Keeps only the most recent MAX_LOGS files (FIFO).
 */
class CrashHandler(
    private val appContext: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "CrashHandler"
        private const val APP_FOLDER = "FloatingApps"
        private const val MAX_LOGS = 50
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            writeCrashLog(thread, throwable)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write crash log", e)
        } finally {
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable)
            } else {
                exitProcess(10)
            }
        }
    }

    private fun writeCrashLog(thread: Thread, throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val uuid = UUID.randomUUID().toString().substring(0, 8)
        val fileName = "crash_${timestamp}_${uuid}.txt"
        val content = buildLogContent(thread, throwable, timestamp)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(fileName, content)
            cleanOldLogsMediaStore()
        } else {
            writeViaAppStorage(fileName, content)
            cleanOldLogsAppStorage()
        }
    }

    private fun buildLogContent(thread: Thread, throwable: Throwable, timestamp: String): String {
        val versionName = try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            "unknown"
        }
        val sb = StringBuilder()
        sb.append("=== Floating Apps Crash Report ===\n")
        sb.append("Timestamp: $timestamp\n")
        sb.append("App Version: $versionName\n")
        sb.append("Android OS: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
        sb.append("Thread: ${thread.name}\n")
        sb.append("\n--- Stack Trace ---\n")
        sb.append(Log.getStackTraceString(throwable))
        return sb.toString()
    }

    private fun writeViaMediaStore(fileName: String, content: String) {
        val resolver = appContext.contentResolver
        val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/$APP_FOLDER/logs"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }
        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return
        resolver.openOutputStream(uri)?.use { out ->
            out.write(content.toByteArray())
        }
    }

    private fun cleanOldLogsMediaStore() {
        val resolver = appContext.contentResolver
        val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/$APP_FOLDER/logs/"
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val cursor = resolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            arrayOf(relativePath),
            "${MediaStore.MediaColumns.DATE_ADDED} ASC"
        ) ?: return

        val ids = mutableListOf<Long>()
        cursor.use {
            while (it.moveToNext()) {
                ids.add(it.getLong(it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)))
            }
        }
        if (ids.size > MAX_LOGS) {
            val toDelete = ids.take(ids.size - MAX_LOGS)
            for (id in toDelete) {
                val deleteUri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)
                resolver.delete(deleteUri, null, null)
            }
        }
    }

    private fun logsDir(): File {
        val dir = File(appContext.getExternalFilesDir(null), "logs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun writeViaAppStorage(fileName: String, content: String) {
        val file = File(logsDir(), fileName)
        FileOutputStream(file).use { it.write(content.toByteArray()) }
    }

    private fun cleanOldLogsAppStorage() {
        val files = logsDir().listFiles()?.sortedBy { it.lastModified() } ?: return
        if (files.size > MAX_LOGS) {
            val toDelete = files.take(files.size - MAX_LOGS)
            toDelete.forEach { it.delete() }
        }
    }
}
