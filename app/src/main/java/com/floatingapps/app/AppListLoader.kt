package com.floatingapps.app

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

object AppListLoader {

    fun load(context: Context, callback: (List<FloatableApp>) -> Unit) {
        val appContext = context.applicationContext
        val mainHandler = Handler(Looper.getMainLooper())
        Thread {
            val pm = appContext.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val selfPackage = appContext.packageName
            val resolveInfos = try {
                pm.queryIntentActivities(intent, 0)
            } catch (e: Exception) {
                emptyList()
            }
            val list = resolveInfos.mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == selfPackage) return@mapNotNull null
                try {
                    FloatableApp(
                        packageName = pkg,
                        activityName = ri.activityInfo.name,
                        label = ri.loadLabel(pm).toString(),
                        icon = ri.loadIcon(pm)
                    )
                } catch (e: Exception) {
                    null
                }
            }.sortedBy { it.label.lowercase() }

            mainHandler.post { callback(list) }
        }.start()
    }
}
