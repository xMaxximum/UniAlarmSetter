package com.maxximum.alarmsetter

import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class AlarmWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Log.d("AlarmWorker", "Background worker started - setting system alarm")
            
            // Check if we have SYSTEM_ALERT_WINDOW permission (required for background activity launch)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && 
                !Settings.canDrawOverlays(applicationContext)) {
                Log.e("AlarmWorker", "SYSTEM_ALERT_WINDOW permission not granted")
                return Result.failure()
            }
            
            // Get current time and add 1 minute
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MINUTE, 1)
            
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            
            // Create intent to set alarm in system alarm clock app
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, "Background alarm set by AlarmSetter app")
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            // Start the alarm intent
            applicationContext.startActivity(intent)
            
            Log.d("AlarmWorker", "Background alarm set for $hour:${minute.toString().padStart(2, '0')}")
            Result.success()
        } catch (e: Exception) {
            Log.e("AlarmWorker", "Error setting background alarm: ${e.message}", e)
            Result.failure()
        }
    }
}
