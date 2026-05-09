package com.byteutility.dev.nightwatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.SleepClassifyEvent

class SleepReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SleepReceiver"
        private var consecutiveHighConfidenceCount = 0
        private const val CONFIDENCE_THRESHOLD = 85
        private const val REQUIRED_CONSECUTIVE_EVENTS = 2

        fun createPendingIntent(context: Context): android.app.PendingIntent {
            val intent = Intent(context, SleepReceiver::class.java)
            return android.app.PendingIntent.getBroadcast(
                context,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (SleepClassifyEvent.hasEvents(intent)) {
            val events = SleepClassifyEvent.extractEvents(intent)
            Log.d(TAG, "Received ${events.size} sleep events")

            val prefs = context.getSharedPreferences("NightWatchPrefs", Context.MODE_PRIVATE)
            var count = prefs.getInt("consecutive_high_confidence", 0)

            for (event in events) {
                Log.d(TAG, "Sleep Confidence: ${event.confidence}")
                if (event.confidence >= CONFIDENCE_THRESHOLD) {
                    count++
                } else {
                    count = 0
                }

                if (count >= REQUIRED_CONSECUTIVE_EVENTS) {
                    Log.d(TAG, "High confidence sleep confirmed!")
                    handleSleepConfirmed(context, event.timestampMillis)
                    count = 0 // Reset after trigger
                    break
                }
            }
            prefs.edit().putInt("consecutive_high_confidence", count).apply()
        }
    }

    private fun handleSleepConfirmed(context: Context, sleepStartTime: Long) {
        val prefs = context.getSharedPreferences("NightWatchPrefs", Context.MODE_PRIVATE)
        val targetWakeup = prefs.getLong("target_wakeup", 0L)
        val requiredDuration = prefs.getLong("required_duration", 0L)
        val hardDeadline = prefs.getLong("hard_deadline", 0L)

        if (targetWakeup == 0L) {
            Log.e(TAG, "Target wakeup time not set!")
            return
        }

        // Reschedule the alarm
        AlarmRescheduler.rescheduleAlarm(
            context,
            targetWakeup,
            requiredDuration,
            sleepStartTime,
            hardDeadline
        )

        // Auto-Stop Mechanism:
        // 1. Unsubscribe from Sleep API
        unsubscribeFromSleepUpdates(context)

        // 2. Terminate background processes/state
        consecutiveHighConfidenceCount = 0
        prefs.edit().putBoolean("is_monitoring", false).apply()
        
        Log.d(TAG, "Auto-stop complete. Unsubscribed and monitoring stopped.")
    }

    private fun unsubscribeFromSleepUpdates(context: Context) {
        val client = ActivityRecognition.getClient(context)
        client.removeSleepSegmentUpdates(createPendingIntent(context))
            .addOnSuccessListener {
                Log.d(TAG, "Successfully unsubscribed from Sleep API")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to unsubscribe from Sleep API", e)
            }
    }
}
