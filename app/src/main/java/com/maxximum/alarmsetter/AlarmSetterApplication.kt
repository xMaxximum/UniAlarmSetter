package com.maxximum.alarmsetter

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager

class AlarmSetterApplication : Application(), Configuration.Provider {
    
    override fun onCreate() {
        super.onCreate()
        // WorkManager is automatically initialized when using Configuration.Provider
        // No need to manually call WorkManager.initialize()
    }
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
