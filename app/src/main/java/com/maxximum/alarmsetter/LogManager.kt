package com.maxximum.alarmsetter

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue

data class LogEntry(
    val timestamp: LocalDateTime,
    val level: String,
    val tag: String,
    val message: String
)

object LogManager {
    private val logs = ConcurrentLinkedQueue<LogEntry>()
    private const val MAX_LOGS = 1000 // Keep last 1000 log entries
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
    
    // Custom log methods that both log to system and store internally
    fun d(tag: String, message: String) {
        Log.d(tag, message)
        addLog("DEBUG", tag, message)
    }
    
    fun i(tag: String, message: String) {
        Log.i(tag, message)
        addLog("INFO", tag, message)
    }
    
    fun w(tag: String, message: String) {
        Log.w(tag, message)
        addLog("WARN", tag, message)
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
            addLog("ERROR", tag, "$message: ${throwable.message}")
        } else {
            Log.e(tag, message)
            addLog("ERROR", tag, message)
        }
    }
    
    // Boot-specific logging method that ensures the message is logged to system logcat
    fun bootLog(tag: String, message: String) {
        Log.i("BOOT_$tag", message)
        // Also try to add to internal logs, but don't fail if storage isn't available
        try {
            addLog("INFO", "BOOT_$tag", message)
        } catch (e: Exception) {
            // Ignore - storage might not be available during early boot
        }
    }
    
    private fun addLog(level: String, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = LocalDateTime.now(),
            level = level,
            tag = tag,
            message = message
        )
        
        logs.offer(entry)
        
        // Remove oldest entries if we exceed max size
        while (logs.size > MAX_LOGS) {
            logs.poll()
        }
    }
    
    fun getAllLogs(): List<LogEntry> {
        return logs.toList().sortedBy { it.timestamp }
    }
    
    fun getLogsAsString(): String {
        if (logs.isEmpty()) return ""
        return getAllLogs().joinToString("\n" + "─".repeat(50) + "\n") { entry ->
            val timeStr = entry.timestamp.format(dateTimeFormatter)
            val levelIcon = getLevelIcon(entry.level)
            val tag = entry.tag
            val message = entry.message
            
            "$timeStr $levelIcon\n📱 [$tag] $message"
        }
    }
    
    private fun getLevelIcon(level: String): String {
        return when(level) {
            "DEBUG" -> "🔍 DEBUG"
            "INFO" -> "ℹ️ INFO "
            "WARN" -> "⚠️ WARN "
            "ERROR" -> "❌ ERROR"
            else -> "📝 $level"
        }
    }
    
    fun clearLogs() {
        logs.clear()
        Log.i("LogManager", "Logs cleared by user")
    }
    
    fun getLogsCount(): Int = logs.size
    
    // Filter logs by tag
    fun getLogsByTag(tag: String): List<LogEntry> {
        return getAllLogs().filter { it.tag == tag }
    }
    
    // Filter logs by level
    fun getLogsByLevel(level: String): List<LogEntry> {
        return getAllLogs().filter { it.level == level }
    }
    
    // Get recent logs (last N entries)
    fun getRecentLogs(count: Int): List<LogEntry> {
        return getAllLogs().takeLast(count)
    }
}
