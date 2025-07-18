package com.maxximum.alarmsetter

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalTime

class SettingsHelper(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("alarm_settings", Context.MODE_PRIVATE)
    
    companion object {
        private const val WAKE_UP_MINUTES_BEFORE_KEY = "wake_up_minutes_before"
        private const val DAILY_RUN_TIME_HOUR_KEY = "daily_run_time_hour"
        private const val DAILY_RUN_TIME_MINUTE_KEY = "daily_run_time_minute"
        private const val DAILY_WORKER_ENABLED_KEY = "daily_worker_enabled"
        private const val ALARM_LABEL_KEY = "alarm_label"
        
        private const val DEFAULT_WAKE_UP_MINUTES = 90
        private const val DEFAULT_DAILY_RUN_HOUR = 22 // 10 PM
        private const val DEFAULT_DAILY_RUN_MINUTE = 0
        private const val DEFAULT_ALARM_LABEL = "Uni Alarm"
    }
    
    // Wake up minutes before event
    fun setWakeUpMinutesBefore(minutes: Int) {
        prefs.edit().putInt(WAKE_UP_MINUTES_BEFORE_KEY, minutes).apply()
    }
    
    fun getWakeUpMinutesBefore(): Int {
        return prefs.getInt(WAKE_UP_MINUTES_BEFORE_KEY, DEFAULT_WAKE_UP_MINUTES)
    }
    
    // Daily run time
    fun setDailyRunTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(DAILY_RUN_TIME_HOUR_KEY, hour)
            .putInt(DAILY_RUN_TIME_MINUTE_KEY, minute)
            .apply()
    }
    
    fun getDailyRunTime(): LocalTime {
        val hour = prefs.getInt(DAILY_RUN_TIME_HOUR_KEY, DEFAULT_DAILY_RUN_HOUR)
        val minute = prefs.getInt(DAILY_RUN_TIME_MINUTE_KEY, DEFAULT_DAILY_RUN_MINUTE)
        return LocalTime.of(hour, minute)
    }
    
    // Daily worker enabled
    fun setDailyWorkerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(DAILY_WORKER_ENABLED_KEY, enabled).apply()
    }
    
    fun isDailyWorkerEnabled(): Boolean {
        return prefs.getBoolean(DAILY_WORKER_ENABLED_KEY, false)
    }
    
    // Alarm label
    fun setAlarmLabel(label: String) {
        prefs.edit().putString(ALARM_LABEL_KEY, label).apply()
    }
    
    fun getAlarmLabel(): String {
        return prefs.getString(ALARM_LABEL_KEY, DEFAULT_ALARM_LABEL) ?: DEFAULT_ALARM_LABEL
    }
}
