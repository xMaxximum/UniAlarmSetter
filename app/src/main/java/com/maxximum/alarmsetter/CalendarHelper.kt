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
            
            Log.d("CalendarHelper", "Querying events for calendar $calendarId from $now to $oneWeekFromNow")
            
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
            
            Log.d("CalendarHelper", "Using instances URI: $instancesUri")
            
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
                    
                    Log.d("CalendarHelper", "Found event: $title at $startTime (recurring event support enabled)")
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
            
            Log.d("CalendarHelper", "Querying next absolute event for calendar $calendarId from $now to $sevenDaysFromNow")
            
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
            
            val selection = "(${CalendarContract.Instances.CALENDAR_ID} = ?) AND " +
                    "(${CalendarContract.Instances.ALL_DAY} = 0)"
            val selectionArgs = arrayOf(calendarId.toString())
            val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"
            
            // Build URI with time range for instances
            val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(now.toString())
                .appendPath(sevenDaysFromNow.toString())
                .build()
            
            Log.d("CalendarHelper", "Using instances URI for next event: $instancesUri")
            
            val cursor: Cursor? = context.contentResolver.query(
                instancesUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
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
                    
                    Log.d("CalendarHelper", "Found next event: $title at $startTime (recurring events supported)")
                    return CalendarEvent(id, title, description, startTime, endTime, allDay, location)
                }
            }
        } catch (e: SecurityException) {
            Log.e("CalendarHelper", "Calendar permission not granted", e)
        } catch (e: Exception) {
            Log.e("CalendarHelper", "Error retrieving absolute next event", e)
        }
        
        Log.d("CalendarHelper", "No next event found")
        return null
    }
}
