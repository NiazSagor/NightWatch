package com.byteutility.dev.nightwatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "ALARM RINGING!")
        Toast.makeText(context, "Adaptive Alarm: Wake up!", Toast.LENGTH_LONG).show()
        // In a real app, you would start an Activity or a Notification with sound here.
    }
}
