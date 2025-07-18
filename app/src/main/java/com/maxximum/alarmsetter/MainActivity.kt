package com.maxximum.alarmsetter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.util.Calendar
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import com.maxximum.alarmsetter.ui.theme.AlarmSetterTheme

class MainActivity : ComponentActivity() {
    
    // Activity result launcher for overlay permission
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Permission result is handled automatically by checking Settings.canDrawOverlays()
        // in the Composable
    }
    
    // Activity result launcher for calendar permission
    private val calendarPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Permission result is handled automatically in the Composable
    }
    
    // Activity result launcher for notification permission
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Permission result is handled automatically in the Composable
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Request overlay permission on first launch if not granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
        }
        
        // Request notification permission on first launch if not granted (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission()
            }
        }
        
        setContent {
            AlarmSetterTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AlarmSetterScreen(
                        modifier = Modifier.padding(innerPadding),
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        onRequestCalendarPermission = { requestCalendarPermission() },
                        onRequestNotificationPermission = { requestNotificationPermission() }
                    )
                }
            }
        }
    }
    
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }
    
    private fun requestCalendarPermission() {
        calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmSetterScreen(
    modifier: Modifier = Modifier,
    onRequestOverlayPermission: () -> Unit = {},
    onRequestCalendarPermission: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {}
) {
    val context = LocalContext.current
    var hasOverlayPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        )
    }
    
    var hasCalendarPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Not needed for older versions
            }
        )
    }
    
    val calendarHelper = remember { CalendarHelper(context) }
    val settingsHelper = remember { SettingsHelper(context) }
    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    var selectedCalendar by remember { mutableStateOf<CalendarInfo?>(null) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var upcomingEvents by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    
    // Settings states
    var wakeUpMinutesBefore by remember { mutableStateOf(settingsHelper.getWakeUpMinutesBefore()) }
    var wakeUpTextValue by remember { mutableStateOf(settingsHelper.getWakeUpMinutesBefore().toString()) }
    var alarmLabel by remember { mutableStateOf(settingsHelper.getAlarmLabel()) }
    var dailyRunTime by remember { mutableStateOf(settingsHelper.getDailyRunTime()) }
    var isDailyWorkerEnabled by remember { mutableStateOf(settingsHelper.isDailyWorkerEnabled()) }
    var showTimePicker by remember { mutableStateOf(false) }
    var timePickerState by remember { 
        mutableStateOf(TimePickerState(
            initialHour = dailyRunTime.hour,
            initialMinute = dailyRunTime.minute,
            is24Hour = true
        ))
    }
    
    // Check permissions status periodically
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000) // Check every second
            hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
            
            hasCalendarPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
            
            hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            
            // Load calendars if we have permission
            if (hasCalendarPermission) {
                calendars = calendarHelper.getSystemCalendars()
                
                // Restore previously selected calendar
                val savedCalendarId = calendarHelper.getSelectedCalendarId()
                if (savedCalendarId != -1L && selectedCalendar == null) {
                    selectedCalendar = calendars.find { it.id == savedCalendarId }
                }
                
                // Load upcoming events if calendar is selected
                selectedCalendar?.let { calendar ->
                    upcomingEvents = calendarHelper.getUpcomingEvents(calendar.id)
                }
            }
        }
    }
    
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // App title
            Text(
                text = "Alarm Setter",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Calendar Permission Section
            if (!hasCalendarPermission) {
                Text(
                    text = "Calendar Permission Required",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFFF6B35)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        onRequestCalendarPermission()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35))
                ) {
                    Text("Grant Calendar Permission")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Notification Permission Section (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                Text(
                    text = "Notification Permission Required",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFFF6B35)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        onRequestNotificationPermission()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35))
                ) {
                    Text("Grant Notification Permission")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Calendar Selection Section
            if (hasCalendarPermission) {
                Text(
                    text = "Select Calendar",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Calendar Dropdown
                ExposedDropdownMenuBox(
                    expanded = isDropdownExpanded,
                    onExpandedChange = { isDropdownExpanded = !isDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedCalendar?.displayName ?: "Select a calendar...",
                        onValueChange = { },
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false }
                    ) {
                        calendars.forEach { calendar ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = calendar.displayName,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${calendar.accountName} (${calendar.accountType})",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                },
                                onClick = {
                                    selectedCalendar = calendar
                                    calendarHelper.saveSelectedCalendar(calendar)
                                    isDropdownExpanded = false
                                    upcomingEvents = calendarHelper.getUpcomingEvents(calendar.id)
                                    Toast.makeText(
                                        context,
                                        "Selected: ${calendar.displayName}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Selected Calendar Info
                selectedCalendar?.let { calendar ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "✅ Selected: ",
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = calendar.displayName,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Upcoming Events Section
                    if (upcomingEvents.isNotEmpty()) {
                        Text(
                            text = "Upcoming Events",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                upcomingEvents.take(3).forEach { event ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = event.title,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (event.allDay) "All Day" else 
                                                    event.startTime.format(DateTimeFormatter.ofPattern("MMM dd, HH:mm")),
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (event != upcomingEvents.take(3).last()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                                
                                if (upcomingEvents.size > 3) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "... and ${upcomingEvents.size - 3} more",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No upcoming events found",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Settings Section
                Text(
                    text = "Alarm Settings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Wake up minutes before field
                OutlinedTextField(
                    value = wakeUpTextValue,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty()) {
                            wakeUpTextValue = ""
                        } else {
                            newValue.toIntOrNull()?.let { minutes ->
                                if (minutes in 0..999) {  // Allow typing larger numbers
                                    wakeUpTextValue = newValue
                                    if (minutes <= 120) {
                                        wakeUpMinutesBefore = minutes
                                        settingsHelper.setWakeUpMinutesBefore(minutes)
                                    }
                                }
                            }
                        }
                    },
                    label = { Text("Wake up minutes before event") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    suffix = { Text("min") },
                    supportingText = {
                        Text("Max 120 minutes", fontSize = 12.sp)
                    },
                    isError = wakeUpTextValue.toIntOrNull()?.let { it > 120 } == true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Alarm label field
                OutlinedTextField(
                    value = alarmLabel,
                    onValueChange = { newValue ->
                        if (newValue.length <= 50) { // Reasonable limit for alarm label
                            alarmLabel = newValue
                            settingsHelper.setAlarmLabel(newValue)
                        }
                    },
                    label = { Text("Alarm label/name") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text("This name will be reused for daily alarms", fontSize = 12.sp)
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Daily run time picker
                OutlinedTextField(
                    value = dailyRunTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Daily run time") },
                    trailingIcon = {
                        Button(
                            onClick = { showTimePicker = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Set")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimePicker = true }
                )
                
                if (showTimePicker) {
                    AlertDialog(
                        onDismissRequest = { showTimePicker = false },
                        title = {
                            Text(
                                "Select Daily Run Time",
                                style = MaterialTheme.typography.headlineSmall
                            )
                        },
                        text = {
                            TimePicker(
                                state = timePickerState,
                                colors = TimePickerDefaults.colors()
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    dailyRunTime = java.time.LocalTime.of(
                                        timePickerState.hour,
                                        timePickerState.minute
                                    )
                                    settingsHelper.setDailyRunTime(
                                        timePickerState.hour,
                                        timePickerState.minute
                                    )
                                    showTimePicker = false
                                    
                                    // Update/Schedule periodic work
                                    if (isDailyWorkerEnabled) {
                                        val workRequest = PeriodicWorkRequestBuilder<AlarmWorker>(24, TimeUnit.HOURS)
                                            .setInitialDelay(calculateInitialDelay(dailyRunTime), TimeUnit.MILLISECONDS)
                                            .build()
                                        
                                        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                                            "daily_alarm_work",
                                            ExistingPeriodicWorkPolicy.REPLACE,
                                            workRequest
                                        )
                                        
                                        Toast.makeText(
                                            context,
                                            "Daily alarm schedule updated to ${dailyRunTime.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Daily run time updated to ${dailyRunTime.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            ) {
                                Text("Confirm")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showTimePicker = false }
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Daily worker enabled toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enable daily auto alarms",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Switch(
                        checked = isDailyWorkerEnabled,
                        onCheckedChange = { enabled ->
                            isDailyWorkerEnabled = enabled
                            settingsHelper.setDailyWorkerEnabled(enabled)
                            
                            if (enabled) {
                                // Schedule periodic work
                                val workRequest = PeriodicWorkRequestBuilder<AlarmWorker>(24, TimeUnit.HOURS)
                                    .setInitialDelay(calculateInitialDelay(dailyRunTime), TimeUnit.MILLISECONDS)
                                    .build()
                                
                                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                                    "daily_alarm_work",
                                    ExistingPeriodicWorkPolicy.REPLACE,
                                    workRequest
                                )
                            } else {
                                // Cancel periodic work
                                WorkManager.getInstance(context).cancelUniqueWork("daily_alarm_work")
                            }
                            
                            Toast.makeText(
                                context,
                                if (enabled) "Daily auto alarms enabled" else "Daily auto alarms disabled",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action Buttons
                selectedCalendar?.let { calendar ->
                    Button(
                        onClick = {
                            val nextEvent = calendarHelper.getAbsoluteNextEvent(calendar.id)
                            if (nextEvent != null) {
                                // Calculate alarm time (event time minus wake up minutes)
                                val alarmDateTime = nextEvent.startTime.minusMinutes(wakeUpMinutesBefore.toLong())
                                
                                // The system will automatically reuse existing alarms with identical parameters
                                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                                    putExtra(AlarmClock.EXTRA_HOUR, alarmDateTime.hour)
                                    putExtra(AlarmClock.EXTRA_MINUTES, alarmDateTime.minute)
                                    putExtra(AlarmClock.EXTRA_MESSAGE, alarmLabel)
                                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                
                                try {
                                    context.startActivity(intent)
                                    
                                    // Send notification about the alarm being set
                                    val alarmTimeString = alarmDateTime.format(DateTimeFormatter.ofPattern("MMM dd, HH:mm"))
                                    val eventTimeString = nextEvent.startTime.format(DateTimeFormatter.ofPattern("MMM dd, HH:mm"))
                                    
                                    NotificationHelper.sendAlarmNotification(
                                        context,
                                        "Alarm Set Successfully",
                                        "Alarm '$alarmLabel' set for $alarmTimeString\n$wakeUpMinutesBefore min before: ${nextEvent.title}\nEvent time: $eventTimeString"
                                    )
                                    
                                    Toast.makeText(
                                        context,
                                        "Alarm '$alarmLabel' set for ${alarmDateTime.format(DateTimeFormatter.ofPattern("MMM dd, HH:mm"))} " +
                                        "($wakeUpMinutesBefore min before: ${nextEvent.title})",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error setting alarm: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    "No upcoming events with specific times found",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                    ) {
                        Text("Set Alarm for Next Event")
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            // Overlay Permission Button (if needed and not in calendar section)
            if (!hasOverlayPermission && !hasCalendarPermission && hasNotificationPermission) {
                Button(
                    onClick = {
                        onRequestOverlayPermission()
                        Toast.makeText(context, "Please grant overlay permission for background alarms", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35))
                ) {
                    Text("Grant Overlay Permission")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Permission status (simplified)
            if (!hasCalendarPermission || !hasOverlayPermission || !hasNotificationPermission) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Required Permissions:",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (!hasCalendarPermission) {
                        Text(
                            text = "❌ Calendar Access Needed",
                            color = Color(0xFFFF5722),
                            fontSize = 14.sp
                        )
                    }
                    
                    if (!hasOverlayPermission) {
                        Text(
                            text = "❌ Overlay Permission Needed (for background alarms)",
                            color = Color(0xFFFF5722),
                            fontSize = 14.sp
                        )
                    }
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                        Text(
                            text = "❌ Notification Permission Needed",
                            color = Color(0xFFFF5722),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AlarmSetterPreview() {
    AlarmSetterTheme {
        AlarmSetterScreen()
    }
}