package com.floatingapps.app

import android.app.Application
import com.floatingapps.app.core.session.FloatingSessionManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(applicationContext, defaultHandler))
        // Application-scoped context only (never an Activity/Service) - see
        // FloatingSessionManager.init doc. Runs once per process start,
        // regardless of whether MainActivity or FloatingBubbleService is
        // the actual entry point this time.
        FloatingSessionManager.init(applicationContext)
    }
}
