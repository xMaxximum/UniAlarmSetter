package com.maxximum.alarmsetter

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        LogManager.bootLog("BootReceiver", "Received intent: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                LogManager.bootLog(
                    "BootReceiver",
                    "Boot completed, checking alarm worker configuration"
                )
                handleBootCompleted(context)
            }

            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                // Only handle if it's our own package being replaced
                val packageName = intent.data?.schemeSpecificPart
                if (packageName == context.packageName) {
                    LogManager.bootLog(
                        "BootReceiver",
                        "Our package was updated, checking alarm worker configuration"
                    )
                    handleBootCompleted(context)
                } else {
                    LogManager.bootLog(
                        "BootReceiver",
                        "Different package updated: $packageName, ignoring"
                    )
                }
            }

            else -> {
                LogManager.bootLog("BootReceiver", "Unhandled intent action: ${intent.action}")
            }
        }
    }

    private fun handleBootCompleted(context: Context) {
        val settingsHelper = SettingsHelper(context)

        // Check if daily worker was enabled before reboot
        if (settingsHelper.isDailyWorkerEnabled()) {
            LogManager.bootLog("BootReceiver", "Daily worker was enabled, re-scheduling after boot")

            // Get the saved daily run time
            val dailyRunTime = settingsHelper.getDailyRunTime()

            // Calculate initial delay for the daily worker
            val initialDelay = calculateInitialDelay(dailyRunTime)

            // Schedule the periodic work again
            val workRequest = PeriodicWorkRequestBuilder<AlarmWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily_alarm_work",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )

            LogManager.bootLog(
                "BootReceiver",
                "Daily alarm worker re-scheduled for ${dailyRunTime}"
            )

            // Immediately set alarm if possible (even if it's after the worker's scheduled time)
            trySetImmediateAlarm(context, settingsHelper)

            try {
                NotificationHelper.sendAlarmNotification(
                    context,
                    "Alarm Service Restored",
                    "Daily alarm service has been restored after device restart and will run at ${
                        dailyRunTime.format(
                            java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                        )
                    }"
                )
            } catch (e: Exception) {
                LogManager.bootLog("BootReceiver", "Could not send notification: ${e.message}")
            }
        } else {
            LogManager.bootLog("BootReceiver", "Daily worker was disabled, no action needed")
        }

    }

    // Helper function to calculate initial delay for periodic work
    private fun calculateInitialDelay(targetTime: LocalTime): Long {
        val now = LocalTime.now()
        val today = java.time.LocalDate.now()
        val targetDateTime = if (targetTime.isAfter(now)) {
            java.time.LocalDateTime.of(today, targetTime)
        } else {
            java.time.LocalDateTime.of(today.plusDays(1), targetTime)
        }

        val nowDateTime = java.time.LocalDateTime.now()
        return java.time.Duration.between(nowDateTime, targetDateTime).toMillis()
    }

    // Try to set an alarm immediately after boot if conditions are met
    private fun trySetImmediateAlarm(context: Context, settingsHelper: SettingsHelper) {
        try {
            LogManager.bootLog("BootReceiver", "Attempting to set immediate alarm after boot")
            
            val calendarHelper = CalendarHelper(context)

            // Check if we have required permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && 
                !Settings.canDrawOverlays(context)) {
                LogManager.bootLog("BootReceiver", "Overlay permission not granted, skipping immediate alarm")
                return
            }

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) 
                != PackageManager.PERMISSION_GRANTED) {
                LogManager.bootLog("BootReceiver", "Calendar permission not granted, skipping immediate alarm")
                return
            }

            // Get selected calendar
            val selectedCalendarId = calendarHelper.getSelectedCalendarId()
            if (selectedCalendarId == -1L) {
                LogManager.bootLog("BootReceiver", "No calendar selected, skipping immediate alarm")
                return
            }

            // Get next event
            val nextEvent = calendarHelper.getAbsoluteNextEvent(selectedCalendarId)
            if (nextEvent == null) {
                LogManager.bootLog("BootReceiver", "No upcoming events found, skipping immediate alarm")
                return
            }

            // Check if the event is within Android AlarmClock API limitations
            if (!calendarHelper.isEventWithinAlarmApiLimitation(nextEvent)) {
                val reason = calendarHelper.getAlarmLimitationReason(nextEvent)
                LogManager.bootLog("BootReceiver", "Event is outside alarm API limitation: $reason")
                return
            }

            // Calculate alarm time (event time minus wake up minutes)
            val wakeUpMinutes = settingsHelper.getWakeUpMinutesBefore()
            val alarmLabel = settingsHelper.getAlarmLabel()
            val alarmDateTime = nextEvent.startTime.minusMinutes(wakeUpMinutes.toLong())

            // Create intent to set alarm in system alarm clock app
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, alarmDateTime.hour)
                putExtra(AlarmClock.EXTRA_MINUTES, alarmDateTime.minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, alarmLabel)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            // Start the alarm intent
            context.startActivity(intent)

            val alarmTimeString = alarmDateTime.format(DateTimeFormatter.ofPattern("MMM dd, HH:mm"))
            val eventTimeString = nextEvent.startTime.format(DateTimeFormatter.ofPattern("MMM dd, HH:mm"))

            LogManager.bootLog("BootReceiver", "Boot alarm '$alarmLabel' set for $alarmTimeString (${wakeUpMinutes}min before ${nextEvent.title})")

            // Send notification about the immediate alarm being set
            try {
                NotificationHelper.sendAlarmNotification(
                    context,
                    "Boot Alarm Set",
                    "Alarm '$alarmLabel' set immediately after boot for $alarmTimeString\n${wakeUpMinutes} min before: ${nextEvent.title}\nEvent time: $eventTimeString"
                )
            } catch (e: Exception) {
                LogManager.bootLog("BootReceiver", "Could not send boot alarm notification: ${e.message}")
            }

        } catch (e: Exception) {
            LogManager.bootLog("BootReceiver", "Error setting immediate boot alarm: ${e.message}")
        }
    }

    companion object {
        // Test method to manually trigger boot receiver logic
        // This can be called from your MainActivity for testing
        fun testBootLogic(context: Context) {
            LogManager.i("BootReceiver", "Manual test of boot logic triggered")
            val bootReceiver = BootReceiver()
            bootReceiver.handleBootCompleted(context)
        }

        // Test method to manually trigger just the immediate alarm logic
        fun testImmediateAlarm(context: Context) {
            LogManager.i("BootReceiver", "Manual test of immediate alarm logic triggered")
            val bootReceiver = BootReceiver()
            val settingsHelper = SettingsHelper(context)
            bootReceiver.trySetImmediateAlarm(context, settingsHelper)
        }
    }
}
