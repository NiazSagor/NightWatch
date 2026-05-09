package com.byteutility.dev.nightwatch

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

object AlarmRescheduler {
    private const val TAG = "AlarmRescheduler"

    fun rescheduleAlarm(
        context: Context,
        targetWakeupTime: Long,
        requiredSleepDurationMs: Long,
        actualSleepStartTime: Long,
        hardDeadline: Long
    ) {
        val idealSleepStartTime = targetWakeupTime - requiredSleepDurationMs
        val delay = actualSleepStartTime - idealSleepStartTime

        var newWakeupTime = targetWakeupTime
        if (delay > 0) {
            newWakeupTime += delay
            Log.d(TAG, "Delay detected: ${delay / 60000} minutes. Shifting alarm.")
        } else {
            Log.d(TAG, "User fell asleep on or before time. No shift needed.")
        }

        // Apply Hard Deadline safety feature
        if (newWakeupTime > hardDeadline) {
            Log.d(TAG, "New wakeup time exceeds hard deadline. Capping to hard deadline.")
            newWakeupTime = hardDeadline
        }

        setSystemAlarm(context, newWakeupTime)
    }

    private fun setSystemAlarm(context: Context, timeInMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    timeInMillis,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeInMillis,
                pendingIntent
            )
        }

        val calendar = Calendar.getInstance().apply { this.timeInMillis = timeInMillis }
        Log.d(TAG, "Alarm set for: ${calendar.time}")
    }
}
