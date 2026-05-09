package com.byteutility.dev.nightwatch

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.SleepSegmentRequest
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AdaptiveAlarmScreen()
                }
            }
        }
    }
}

@Composable
fun AdaptiveAlarmScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("NightWatchPrefs", Context.MODE_PRIVATE) }
    
    var targetWakeup by remember { mutableStateOf(prefs.getLong("target_wakeup", getDefaultTargetWakeup())) }
    var requiredDurationHrs by remember { mutableStateOf((prefs.getLong("required_duration", 7 * 3600000L) / 3600000L).toInt()) }
    var hardDeadline by remember { mutableStateOf(prefs.getLong("hard_deadline", getDefaultHardDeadline())) }
    var isMonitoring by remember { mutableStateOf(prefs.getBoolean("is_monitoring", false)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val activityGranted = permissions[Manifest.permission.ACTIVITY_RECOGNITION] ?: false
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else true

        if (activityGranted && notificationGranted) {
            startMonitoring(context, targetWakeup, requiredDurationHrs, hardDeadline)
            isMonitoring = true
        } else {
            Toast.makeText(context, "Permissions required for PoC", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("NightWatch Adaptive Alarm PoC", style = MaterialTheme.typography.headlineMedium)

        TimePickerField("Target Wake-up Time", targetWakeup) { targetWakeup = it }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Required Sleep (Hours): ")
            Slider(
                value = requiredDurationHrs.toFloat(),
                onValueChange = { requiredDurationHrs = it.toInt() },
                valueRange = 4f..10f,
                modifier = Modifier.weight(1f)
            )
            Text("$requiredDurationHrs")
        }

        TimePickerField("Hard Deadline", hardDeadline) { hardDeadline = it }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (isMonitoring) {
                    stopMonitoring(context)
                    isMonitoring = false
                } else {
                    val permissions = mutableListOf(Manifest.permission.ACTIVITY_RECOGNITION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    
                    val allGranted = permissions.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }

                    if (allGranted) {
                        startMonitoring(context, targetWakeup, requiredDurationHrs, hardDeadline)
                        isMonitoring = true
                    } else {
                        permissionLauncher.launch(permissions.toTypedArray())
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isMonitoring) "Stop Monitoring" else "Start NightWatch")
        }

        if (isMonitoring) {
            CircularProgressIndicator()
            Text("Monitoring sleep confidence...")
        }
    }
}

@Composable
fun TimePickerField(label: String, timeMillis: Long, onTimeSelected: (Long) -> Unit) {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    // Simplified: Just showing the time. In a real app, use a TimePickerDialog.
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(sdf.format(Date(timeMillis)), style = MaterialTheme.typography.bodyLarge)
        Text("(In PoC: Long-press or Use System Picker)", style = MaterialTheme.typography.labelSmall)
    }
}

fun getDefaultTargetWakeup(): Long {
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 7)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        if (timeInMillis < System.currentTimeMillis()) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }.timeInMillis
}

fun getDefaultHardDeadline(): Long {
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        if (timeInMillis < System.currentTimeMillis()) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }.timeInMillis
}

fun startMonitoring(context: Context, wakeup: Long, durationHrs: Int, deadline: Long) {
    val prefs = context.getSharedPreferences("NightWatchPrefs", Context.MODE_PRIVATE)
    prefs.edit().apply {
        putLong("target_wakeup", wakeup)
        putLong("required_duration", durationHrs * 3600000L)
        putLong("hard_deadline", deadline)
        putBoolean("is_monitoring", true)
    }.apply()

    val client = ActivityRecognition.getClient(context)
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
        client.requestSleepSegmentUpdates(
            SleepReceiver.createPendingIntent(context),
            SleepSegmentRequest.getDefaultSleepSegmentRequest()
        ).addOnSuccessListener {
            Toast.makeText(context, "Sleep Monitoring Started", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(context, "Failed to start monitoring: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }
}

fun stopMonitoring(context: Context) {
    val client = ActivityRecognition.getClient(context)
    client.removeSleepSegmentUpdates(SleepReceiver.createPendingIntent(context))
    context.getSharedPreferences("NightWatchPrefs", Context.MODE_PRIVATE).edit().putBoolean("is_monitoring", false).apply()
    Toast.makeText(context, "Monitoring Stopped", Toast.LENGTH_SHORT).show()
}
