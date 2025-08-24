package com.maxximum.alarmsetter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalTime
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

    companion object {
        // Test method to manually trigger boot receiver logic
        // This can be called from your MainActivity for testing
        fun testBootLogic(context: Context) {
            LogManager.i("BootReceiver", "Manual test of boot logic triggered")
            val bootReceiver = BootReceiver()
            bootReceiver.handleBootCompleted(context)
        }
    }
}
