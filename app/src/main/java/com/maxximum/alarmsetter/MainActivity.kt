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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Request overlay permission on first launch if not granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
        }
        
        setContent {
            AlarmSetterTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AlarmSetterScreen(
                        modifier = Modifier.padding(innerPadding),
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        onRequestCalendarPermission = { requestCalendarPermission() }
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
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmSetterScreen(
    modifier: Modifier = Modifier,
    onRequestOverlayPermission: () -> Unit = {},
    onRequestCalendarPermission: () -> Unit = {}
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
    
    val calendarHelper = remember { CalendarHelper(context) }
    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    var selectedCalendar by remember { mutableStateOf<CalendarInfo?>(null) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    
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
            
            // Load calendars if we have permission
            if (hasCalendarPermission) {
                calendars = calendarHelper.getSystemCalendars()
                
                // Restore previously selected calendar
                val savedCalendarId = calendarHelper.getSelectedCalendarId()
                if (savedCalendarId != -1L && selectedCalendar == null) {
                    selectedCalendar = calendars.find { it.id == savedCalendarId }
                }
            }
        }
    }
    
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
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
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            // Background Worker Button
            Button(
                onClick = {
                    if (hasOverlayPermission) {
                        // Schedule background worker to set alarm in 10 seconds (for testing)
                        val workRequest = OneTimeWorkRequestBuilder<AlarmWorker>()
                            .setInitialDelay(10, TimeUnit.SECONDS)
                            .build()
                        
                        WorkManager.getInstance(context).enqueue(workRequest)
                        Toast.makeText(context, "Background system alarm scheduled for 10 seconds from now", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Need overlay permission for background alarms", Toast.LENGTH_LONG).show()
                        onRequestOverlayPermission()
                    }
                },
                enabled = hasOverlayPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasOverlayPermission) Color(0xFF4CAF50) else Color.Gray
                )
            ) {
                Text("Test Background Alarm (10 sec)")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Overlay Permission Button (if needed)
            if (!hasOverlayPermission) {
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
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Permission status
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Status:",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = if (hasCalendarPermission) "✅ Calendar Access" else "❌ Calendar Permission Needed",
                    color = if (hasCalendarPermission) Color(0xFF4CAF50) else Color(0xFFFF5722),
                    fontSize = 14.sp
                )
                
                Text(
                    text = if (hasOverlayPermission) "✅ Overlay Permission" else "❌ Overlay Permission Needed",
                    color = if (hasOverlayPermission) Color(0xFF4CAF50) else Color(0xFFFF5722),
                    fontSize = 14.sp
                )
                
                if (hasCalendarPermission) {
                    Text(
                        text = if (selectedCalendar != null) "✅ Calendar Selected" else "⚠️ No Calendar Selected",
                        color = if (selectedCalendar != null) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        fontSize = 14.sp
                    )
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