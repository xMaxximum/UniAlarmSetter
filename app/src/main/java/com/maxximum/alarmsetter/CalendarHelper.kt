package com.maxximum.alarmsetter

import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log

data class CalendarInfo(
    val id: Long,
    val name: String,
    val displayName: String,
    val accountName: String,
    val accountType: String
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
}
