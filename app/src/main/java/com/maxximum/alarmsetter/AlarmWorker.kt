package com.maxximum.alarmsetter

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.format.DateTimeFormatter

class AlarmWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "alarm_notifications"
        private const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        return try {
            LogManager.d("AlarmWorker", "Daily background worker started")
            
            val settingsHelper = SettingsHelper(applicationContext)
            val calendarHelper = CalendarHelper(applicationContext)
            
            // Check if daily worker is enabled
            if (!settingsHelper.isDailyWorkerEnabled()) {
                LogManager.d("AlarmWorker", "Daily worker is disabled")
                return Result.success()
            }
            
            // Check if we have required permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && 
                !Settings.canDrawOverlays(applicationContext)) {
                LogManager.e("AlarmWorker", "SYSTEM_ALERT_WINDOW permission not granted")
                sendNotification("Permission Error", "Overlay permission required for background alarms")
                return Result.failure()
            }
            
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_CALENDAR) 
                != PackageManager.PERMISSION_GRANTED) {
                LogManager.e("AlarmWorker", "Calendar permission not granted")
                sendNotification("Permission Error", "Calendar permission required")
                return Result.failure()
            }
            
            // Get selected calendar
            val selectedCalendarId = calendarHelper.getSelectedCalendarId()
            if (selectedCalendarId == -1L) {
                LogManager.e("AlarmWorker", "No calendar selected")
                sendNotification("Configuration Error", "No calendar selected in app")
                return Result.failure()
            }
            
            // Get next event (using getAbsoluteNextEvent)
            val nextEvent = calendarHelper.getAbsoluteNextEvent(selectedCalendarId)
            if (nextEvent == null) {
                LogManager.d("AlarmWorker", "No upcoming events found")
                sendNotification("No Events", "No upcoming events found")
                return Result.success()
            }
            
            // Validate alarm eligibility and log detailed information
            val validationInfo = calendarHelper.validateAlarmEligibility(selectedCalendarId)
            LogManager.d("AlarmWorker", "Alarm validation:\n$validationInfo")
            
            // Check if the event is within Android AlarmClock API limitations
            // Android can only set alarms for today (if time hasn't passed) or tomorrow
            // This ensures the background worker follows the same limitations as manual setting
            if (!calendarHelper.isEventWithinAlarmApiLimitation(nextEvent)) {
                val reason = calendarHelper.getAlarmLimitationReason(nextEvent)
                LogManager.d("AlarmWorker", "Event is outside alarm API limitation: $reason")
                sendNotification(
                    "Cannot Set Alarm", 
                    "Next event cannot be set as alarm: $reason\n\nAndroid only allows setting alarms for today or tomorrow."
                )
                return Result.success()
            }
            
            // Calculate alarm time (event time minus wake up minutes)
            val wakeUpMinutes = settingsHelper.getWakeUpMinutesBefore()
            val alarmLabel = settingsHelper.getAlarmLabel()
            val alarmDateTime = nextEvent.startTime.minusMinutes(wakeUpMinutes.toLong())
            
            // Create intent to set alarm in system alarm clock app
            // The system will automatically reuse existing alarms with identical parameters (hour, minutes, message)
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, alarmDateTime.hour)
                putExtra(AlarmClock.EXTRA_MINUTES, alarmDateTime.minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, alarmLabel)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            // Start the alarm intent
            applicationContext.startActivity(intent)
            
            val alarmTimeString = alarmDateTime.format(DateTimeFormatter.ofPattern("MMM dd, HH:mm"))
            val eventTimeString = nextEvent.startTime.format(DateTimeFormatter.ofPattern("MMM dd, HH:mm"))
            
            LogManager.d("AlarmWorker", "Background alarm '$alarmLabel' set for $alarmTimeString (${wakeUpMinutes}min before ${nextEvent.title})")
            
            // Send success notification
            sendNotification(
                "Alarm Set Successfully",
                "Alarm '$alarmLabel' set for $alarmTimeString\n${wakeUpMinutes} min before: ${nextEvent.title}\nEvent time: $eventTimeString"
            )
            
            Result.success()
        } catch (e: Exception) {
            LogManager.e("AlarmWorker", "Error in daily alarm worker: ${e.message}", e)
            sendNotification("Error", "Failed to set daily alarm: ${e.message}")
            Result.failure()
        }
    }
    
    private fun sendNotification(title: String, content: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create notification channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Alarm Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for alarm setting results"
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        // Check if we have notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) 
                == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        } else {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }
}
