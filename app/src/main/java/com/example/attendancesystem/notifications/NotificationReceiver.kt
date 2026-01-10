package com.example.attendancesystem.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that handles scheduled notification alarms.
 * Triggered by AlarmManager at the scheduled time.
 */
class NotificationReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotificationReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received broadcast: ${intent.action}")

        when (intent.action) {
            "CLASS_REMINDER" -> handleClassReminder(context, intent)
            Intent.ACTION_BOOT_COMPLETED -> handleBootCompleted(context)
            "android.intent.action.QUICKBOOT_POWERON" -> handleBootCompleted(context)
        }
    }

    private fun handleClassReminder(context: Context, intent: Intent) {
        val scheduleId = intent.getStringExtra("scheduleId") ?: return
        val subject = intent.getStringExtra("subject") ?: "Class"
        val startTime = intent.getStringExtra("startTime") ?: ""
        val reminderMinutes = intent.getIntExtra("reminderMinutes", 10)

        Log.d(TAG, "Class reminder triggered: $subject at $startTime")

        // Check if notifications are still enabled
        val prefs = context.getSharedPreferences("student_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications_enabled", true)) {
            Log.d(TAG, "Notifications disabled, skipping")
            return
        }

        // Show the notification
        val notificationManager = LocalNotificationManager.getInstance(context)
        
        val title = "📚 Class Starting Soon!"
        val message = if (reminderMinutes > 0) {
            "$subject class starts in $reminderMinutes minutes. Time to head to class!"
        } else {
            "$subject class is starting now!"
        }

        notificationManager.showNotification(
            title = title,
            message = message,
            notificationId = scheduleId.hashCode()
        )

        // Reschedule for next week
        CoroutineScope(Dispatchers.IO).launch {
            try {
                notificationManager.scheduleAllClassNotifications()
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling notifications: ${e.message}")
            }
        }
    }

    private fun handleBootCompleted(context: Context) {
        Log.d(TAG, "Device boot completed, rescheduling notifications")
        
        // Reschedule all notifications after device reboot
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val notificationManager = LocalNotificationManager.getInstance(context)
                notificationManager.scheduleAllClassNotifications()
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling after boot: ${e.message}")
            }
        }
    }
}


