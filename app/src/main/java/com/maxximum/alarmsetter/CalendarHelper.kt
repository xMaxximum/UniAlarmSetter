package com.maxximum.alarmsetter

import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import java.time.Instant
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
            Log.e("CalendarHelper", "Calendar permission not granted", e)
        } catch (e: Exception) {
            Log.e("CalendarHelper", "Error retrieving calendars", e)
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
            
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.EVENT_LOCATION
            )
            
            val selection = "(${CalendarContract.Events.CALENDAR_ID} = ?) AND " +
                    "(${CalendarContract.Events.DTSTART} >= ?) AND " +
                    "(${CalendarContract.Events.DTSTART} <= ?)"
            val selectionArgs = arrayOf(
                calendarId.toString(),
                now.toString(),
                oneWeekFromNow.toString()
            )
            val sortOrder = "${CalendarContract.Events.DTSTART} ASC"
            
            val cursor: Cursor? = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )
            
            cursor?.use {
                val idColumn = it.getColumnIndex(CalendarContract.Events._ID)
                val titleColumn = it.getColumnIndex(CalendarContract.Events.TITLE)
                val descriptionColumn = it.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                val startColumn = it.getColumnIndex(CalendarContract.Events.DTSTART)
                val endColumn = it.getColumnIndex(CalendarContract.Events.DTEND)
                val allDayColumn = it.getColumnIndex(CalendarContract.Events.ALL_DAY)
                val locationColumn = it.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                
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
                    
                    events.add(CalendarEvent(id, title, description, startTime, endTime, allDay, location))
                    count++
                }
            }
        } catch (e: SecurityException) {
            Log.e("CalendarHelper", "Calendar permission not granted", e)
        } catch (e: Exception) {
            Log.e("CalendarHelper", "Error retrieving events", e)
        }
        
        return events
    }

    fun getAbsoluteNextEvent(calendarId: Long): CalendarEvent? {
        try {
            val now = System.currentTimeMillis()
            val sevenDaysFromNow = now + (7L * 24 * 60 * 60 * 1000L) // 7 days
            
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.EVENT_LOCATION
            )
            
            val selection = "(${CalendarContract.Events.CALENDAR_ID} = ?) AND " +
                    "(${CalendarContract.Events.DTSTART} >= ?) AND " +
                    "(${CalendarContract.Events.DTSTART} <= ?) AND " +
                    "(${CalendarContract.Events.ALL_DAY} = 0)"
            val selectionArgs = arrayOf(
                calendarId.toString(),
                now.toString(),
                sevenDaysFromNow.toString()
            )
            val sortOrder = "${CalendarContract.Events.DTSTART} ASC"
            
            val cursor: Cursor? = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val idColumn = it.getColumnIndex(CalendarContract.Events._ID)
                    val titleColumn = it.getColumnIndex(CalendarContract.Events.TITLE)
                    val descriptionColumn = it.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                    val startColumn = it.getColumnIndex(CalendarContract.Events.DTSTART)
                    val endColumn = it.getColumnIndex(CalendarContract.Events.DTEND)
                    val allDayColumn = it.getColumnIndex(CalendarContract.Events.ALL_DAY)
                    val locationColumn = it.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                    
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
                    
                    return CalendarEvent(id, title, description, startTime, endTime, allDay, location)
                }
            }
        } catch (e: SecurityException) {
            Log.e("CalendarHelper", "Calendar permission not granted", e)
        } catch (e: Exception) {
            Log.e("CalendarHelper", "Error retrieving absolute next event", e)
        }
        
        return null
    }
}
