package com.maxximum.alarmsetter

import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class CalendarInfo(
    val id: Long,
    val name: String,
    val displayName: String,
    val accountName: String,
    val accountType: String
)

data class CalendarEvent(
    val id: Long,
    val title: String,
    val description: String?,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val allDay: Boolean,
    val location: String?
)

class CalendarHelper(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("calendar_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val SELECTED_CALENDAR_ID_KEY = "selected_calendar_id"
        private const val SELECTED_CALENDAR_NAME_KEY = "selected_calendar_name"
    }
    
    fun getSystemCalendars(): List<CalendarInfo> {
        val calendars = mutableListOf<CalendarInfo>()
        
        try {
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.NAME,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE
            )
            
            val cursor: Cursor? = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
            )
            
            cursor?.use {
                val idColumn = it.getColumnIndex(CalendarContract.Calendars._ID)
                val nameColumn = it.getColumnIndex(CalendarContract.Calendars.NAME)
                val displayNameColumn = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountNameColumn = it.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                val accountTypeColumn = it.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
                
                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn) ?: ""
                    val displayName = it.getString(displayNameColumn) ?: ""
                    val accountName = it.getString(accountNameColumn) ?: ""
                    val accountType = it.getString(accountTypeColumn) ?: ""
                    
                    calendars.add(CalendarInfo(id, name, displayName, accountName, accountType))
                }
            }
        } catch (e: SecurityException) {
            LogManager.e("CalendarHelper", "Calendar permission not granted", e)
        } catch (e: Exception) {
            LogManager.e("CalendarHelper", "Error retrieving calendars", e)
        }
        
        return calendars
    }
    
    fun saveSelectedCalendar(calendar: CalendarInfo) {
        prefs.edit()
            .putLong(SELECTED_CALENDAR_ID_KEY, calendar.id)
            .putString(SELECTED_CALENDAR_NAME_KEY, calendar.displayName)
            .apply()
    }
    
    fun getSelectedCalendarId(): Long {
        return prefs.getLong(SELECTED_CALENDAR_ID_KEY, -1L)
    }
    
    fun getSelectedCalendarName(): String {
        return prefs.getString(SELECTED_CALENDAR_NAME_KEY, "None Selected") ?: "None Selected"
    }
    
    fun hasSelectedCalendar(): Boolean {
        return getSelectedCalendarId() != -1L
    }
    
    fun clearSelectedCalendar() {
        prefs.edit()
            .remove(SELECTED_CALENDAR_ID_KEY)
            .remove(SELECTED_CALENDAR_NAME_KEY)
            .apply()
    }
    
    fun getUpcomingEvents(calendarId: Long, maxEvents: Int = 5): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        
        try {
            val now = System.currentTimeMillis()
            val oneWeekFromNow = now + (7 * 24 * 60 * 60 * 1000L) // 1 week
            
            LogManager.d("CalendarHelper", "Querying events for calendar $calendarId from $now to $oneWeekFromNow")
            
            // Use Instances URI to get expanded recurring events
            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION
            )
            
            val selection = "(${CalendarContract.Instances.CALENDAR_ID} = ?)"
            val selectionArgs = arrayOf(calendarId.toString())
            val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"
            
            // Build URI with time range for instances
            val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(now.toString())
                .appendPath(oneWeekFromNow.toString())
                .build()
            
            LogManager.d("CalendarHelper", "Using instances URI: $instancesUri")
            
            val cursor: Cursor? = context.contentResolver.query(
                instancesUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )
            
            cursor?.use {
                val idColumn = it.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleColumn = it.getColumnIndex(CalendarContract.Instances.TITLE)
                val descriptionColumn = it.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
                val startColumn = it.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endColumn = it.getColumnIndex(CalendarContract.Instances.END)
                val allDayColumn = it.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                val locationColumn = it.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                
                var count = 0
                while (it.moveToNext() && count < maxEvents) {
                    val id = it.getLong(idColumn)
                    val title = it.getString(titleColumn) ?: "No Title"
                    val description = it.getString(descriptionColumn)
                    val startTimeMillis = it.getLong(startColumn)
                    val endTimeMillis = it.getLong(endColumn)
                    val allDay = it.getInt(allDayColumn) == 1
                    val location = it.getString(locationColumn)
                    
                    val startTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(startTimeMillis),
                        ZoneId.systemDefault()
                    )
                    val endTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(endTimeMillis),
                        ZoneId.systemDefault()
                    )
                    
                    LogManager.d("CalendarHelper", "Found event: $title at $startTime (recurring event support enabled)")
                    events.add(CalendarEvent(id, title, description, startTime, endTime, allDay, location))
                    count++
                }
            }
        } catch (e: SecurityException) {
            LogManager.e("CalendarHelper", "Calendar permission not granted", e)
        } catch (e: Exception) {
            LogManager.e("CalendarHelper", "Error retrieving events", e)
        }
        
        return events
    }

    fun getAbsoluteNextEvent(calendarId: Long): CalendarEvent? {
        try {
            val now = System.currentTimeMillis()
            val sevenDaysFromNow = now + (7L * 24 * 60 * 60 * 1000L) // 7 days
            
            LogManager.d("CalendarHelper", "Querying next absolute event for calendar $calendarId from $now to $sevenDaysFromNow")
            LogManager.d("CalendarHelper", "Current date/time: ${LocalDateTime.now()} (${LocalDateTime.now().dayOfWeek})")
            
            // Use Instances URI to get expanded recurring events
            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION
            )
            
            // Exclude all-day events since they don't have specific times
            val selection = "(${CalendarContract.Instances.CALENDAR_ID} = ?) AND " +
                    "(${CalendarContract.Instances.ALL_DAY} = 0)"
            val selectionArgs = arrayOf(calendarId.toString())
            val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"
            
            // Build URI with time range for instances
            val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(now.toString())
                .appendPath(sevenDaysFromNow.toString())
                .build()
            
            LogManager.d("CalendarHelper", "Using instances URI for next event: $instancesUri")
            
            val cursor: Cursor? = context.contentResolver.query(
                instancesUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )
            
            cursor?.use {
                // Look for the first event that's within API limitations
                var firstEvent: CalendarEvent? = null
                
                while (it.moveToNext()) {
                    val idColumn = it.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                    val titleColumn = it.getColumnIndex(CalendarContract.Instances.TITLE)
                    val descriptionColumn = it.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
                    val startColumn = it.getColumnIndex(CalendarContract.Instances.BEGIN)
                    val endColumn = it.getColumnIndex(CalendarContract.Instances.END)
                    val allDayColumn = it.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                    val locationColumn = it.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                    
                    val id = it.getLong(idColumn)
                    val title = it.getString(titleColumn) ?: "No Title"
                    val description = it.getString(descriptionColumn)
                    val startTimeMillis = it.getLong(startColumn)
                    val endTimeMillis = it.getLong(endColumn)
                    val allDay = it.getInt(allDayColumn) == 1
                    val location = it.getString(locationColumn)
                    
                    val startTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(startTimeMillis),
                        ZoneId.systemDefault()
                    )
                    val endTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(endTimeMillis),
                        ZoneId.systemDefault()
                    )
                    
                    val event = CalendarEvent(id, title, description, startTime, endTime, allDay, location)
                    
                    // Store the first event we encounter
                    if (firstEvent == null) {
                        firstEvent = event
                    }
                    
                    LogManager.d("CalendarHelper", "Found event: '$title' at $startTime (${startTime.dayOfWeek})")
                    LogManager.d("CalendarHelper", "Event date: ${startTime.toLocalDate()}")
                    
                    // Check if this event is within API limitations
                    val withinLimitation = isEventWithinAlarmApiLimitation(event)
                    LogManager.d("CalendarHelper", "Event within API limitation: $withinLimitation")
                    
                    if (withinLimitation) {
                        LogManager.d("CalendarHelper", "Returning first event within limitations: $title")
                        return event
                    } else {
                        LogManager.d("CalendarHelper", "Event outside limitations, reason: ${getAlarmLimitationReason(event)}")
                        // Since events are sorted by time (ASC), if this event is too far away,
                        // all subsequent events will also be too far away. Return the first event.
                        LogManager.d("CalendarHelper", "Stopping search, all subsequent events will be too far away. Returning first event: ${firstEvent?.title}")
                        return firstEvent
                    }
                }
                
                // If we processed all events but found none within limitations, return the first one
                return firstEvent
            }
        } catch (e: SecurityException) {
            LogManager.e("CalendarHelper", "Calendar permission not granted", e)
        } catch (e: Exception) {
            LogManager.e("CalendarHelper", "Error retrieving absolute next event", e)
        }
        
        LogManager.d("CalendarHelper", "No next event found")
        return null
    }
    
    /**
     * Checks if an event is within the Android AlarmClock API limitation (today or tomorrow only).
     * The Android AlarmClock API can only set alarms for the next occurrence of a specific time,
     * which means it can only handle today (if the time hasn't passed) or tomorrow.
     */
    fun isEventWithinAlarmApiLimitation(event: CalendarEvent): Boolean {
        val now = LocalDateTime.now()
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val eventDate = event.startTime.toLocalDate()
        
        LogManager.d("CalendarHelper", "Checking alarm limitation for event '${event.title}' on $eventDate")
        LogManager.d("CalendarHelper", "Today: $today, Tomorrow: $tomorrow, Event date: $eventDate")
        LogManager.d("CalendarHelper", "Event time: ${event.startTime}, Current time: $now")
        
        val result = when {
            eventDate == today -> {
                // For today, alarm can only be set if the time hasn't passed yet
                val canSet = event.startTime.isAfter(now)
                LogManager.d("CalendarHelper", "Event is today, time has${if (canSet) " not" else ""} passed")
                canSet
            }
            eventDate == tomorrow -> {
                LogManager.d("CalendarHelper", "Event is tomorrow - can set alarm")
                true
            }
            else -> {
                val daysDifference = java.time.temporal.ChronoUnit.DAYS.between(today, eventDate)
                LogManager.d("CalendarHelper", "Event is $daysDifference days away from today - cannot set alarm")
                false
            }
        }
        
        LogManager.d("CalendarHelper", "Alarm limitation result: $result")
        return result
    }
    
    /**
     * Gets the reason why an event is outside the Android AlarmClock API limitation.
     */
    fun getAlarmLimitationReason(event: CalendarEvent): String {
        val now = LocalDateTime.now()
        val today = LocalDate.now()
        val eventDate = event.startTime.toLocalDate()
        val daysDifference = java.time.temporal.ChronoUnit.DAYS.between(today, eventDate)
        
        return when {
            eventDate == today && event.startTime.isBefore(now) -> {
                "The event is today but the time has already passed. Android can only set alarms for future times."
            }
            daysDifference > 1 -> {
                "The event is $daysDifference days away (on ${eventDate.dayOfWeek}). Due to Android API limitations, alarms can only be set for today or tomorrow."
            }
            daysDifference < 0 -> {
                "The event is in the past."
            }
            else -> "Unknown limitation (this shouldn't happen)"
        }
    }

    /**
     * Debug method to help troubleshoot alarm limitation issues.
     * Call this when you encounter the Friday edge case to get detailed information.
     */
    fun debugEventLimitation(event: CalendarEvent): String {
        val now = LocalDateTime.now()
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val eventDate = event.startTime.toLocalDate()
        val daysDifference = java.time.temporal.ChronoUnit.DAYS.between(today, eventDate)
        
        return buildString {
            appendLine("=== DEBUG: Event Limitation Analysis ===")
            appendLine("Event: '${event.title}'")
            appendLine("Event Date/Time: ${event.startTime}")
            appendLine("Event Day of Week: ${event.startTime.dayOfWeek}")
            appendLine("Current Date/Time: $now")
            appendLine("Current Day of Week: ${now.dayOfWeek}")
            appendLine("Today: $today")
            appendLine("Tomorrow: $tomorrow")
            appendLine("Event Date: $eventDate")
            appendLine("Days Difference: $daysDifference")
            appendLine("Event is today: ${eventDate == today}")
            appendLine("Event is tomorrow: ${eventDate == tomorrow}")
            appendLine("Event time is after now: ${event.startTime.isAfter(now)}")
            appendLine("Within API Limitation: ${isEventWithinAlarmApiLimitation(event)}")
            appendLine("Limitation Reason: ${getAlarmLimitationReason(event)}")
            appendLine("===============================")
        }
    }
    
    /**
     * Helper method to explicitly check for Friday edge case and other potential issues.
     * Returns detailed information about why an alarm might be set incorrectly.
     */
    fun validateAlarmEligibility(calendarId: Long): String {
        val event = getAbsoluteNextEvent(calendarId)
        if (event == null) {
            return "No upcoming events found"
        }
        
        val debugInfo = debugEventLimitation(event)
        LogManager.d("CalendarHelper", debugInfo)
        
        val now = LocalDateTime.now()
        val isWithinLimitation = isEventWithinAlarmApiLimitation(event)
        
        return buildString {
            appendLine("Next Event: '${event.title}'")
            appendLine("Event Time: ${event.startTime} (${event.startTime.dayOfWeek})")
            appendLine("Current Time: $now (${now.dayOfWeek})")
            appendLine("Can Set Alarm: $isWithinLimitation")
            if (!isWithinLimitation) {
                appendLine("Reason: ${getAlarmLimitationReason(event)}")
            }
            
            // Special check for Friday edge case
            if (now.dayOfWeek == java.time.DayOfWeek.FRIDAY && 
                event.startTime.dayOfWeek == java.time.DayOfWeek.MONDAY && 
                isWithinLimitation) {
                appendLine("⚠️ POTENTIAL FRIDAY EDGE CASE DETECTED!")
                appendLine("This alarm is being allowed but it's Friday and the event is Monday")
                appendLine("This might be the bug you're looking for!")
            }
        }
    }
}
