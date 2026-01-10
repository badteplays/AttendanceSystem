package com.example.attendancesystem.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.attendancesystem.R
import com.example.attendancesystem.StudentMainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Locale

/**
 * Manages local notifications for class reminders.
 * Uses AlarmManager for precise timing instead of WorkManager.
 */
class LocalNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "class_reminders"
        const val CHANNEL_NAME = "Class Reminders"
        const val CHANNEL_DESC = "Notifications for upcoming classes"
        
        private const val TAG = "LocalNotificationMgr"
        private const val PREFS_NAME = "notification_prefs"
        private const val KEY_SCHEDULED_ALARMS = "scheduled_alarms"
        
        @Volatile
        private var INSTANCE: LocalNotificationManager? = null
        
        fun getInstance(context: Context): LocalNotificationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocalNotificationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        createNotificationChannel()
    }

    /**
     * Creates the notification channel (required for Android O+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    /**
     * Schedules notifications for all classes for the current user.
     * Call this on login and when schedules change.
     */
    suspend fun scheduleAllClassNotifications() {
        val studentPrefs = context.getSharedPreferences("student_prefs", Context.MODE_PRIVATE)
        if (!studentPrefs.getBoolean("notifications_enabled", true)) {
            Log.d(TAG, "Notifications disabled by user")
            return
        }

        val reminderMinutes = studentPrefs.getInt("reminder_minutes", 10)
        
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: run {
            Log.d(TAG, "No user logged in")
            return
        }

        try {
            val db = FirebaseFirestore.getInstance()
            val userDoc = db.collection("users").document(user.uid).get().await()
            val section = userDoc.getString("section")?.uppercase() ?: run {
                Log.d(TAG, "No section found for user")
                return
            }

            // Cancel all existing alarms first
            cancelAllAlarms()

            // Get all schedules for this section
            val schedules = db.collection("schedules")
                .whereEqualTo("section", section)
                .get()
                .await()

            Log.d(TAG, "Found ${schedules.size()} schedules for section $section")

            var scheduledCount = 0
            for (doc in schedules.documents) {
                val day = doc.getString("day") ?: continue
                val startTime = doc.getString("startTime") ?: continue
                val subject = doc.getString("subject") ?: "Class"
                val scheduleId = doc.id

                // Schedule for each day this week
                scheduleWeeklyNotification(
                    scheduleId = scheduleId,
                    day = day,
                    startTime = startTime,
                    subject = subject,
                    reminderMinutes = reminderMinutes
                )
                scheduledCount++
            }

            Log.d(TAG, "Scheduled $scheduledCount class notifications")

        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling notifications: ${e.message}", e)
        }
    }

    /**
     * Schedules a notification for a specific class.
     */
    private fun scheduleWeeklyNotification(
        scheduleId: String,
        day: String,
        startTime: String,
        subject: String,
        reminderMinutes: Int
    ) {
        try {
            val calendar = getNextOccurrence(day, startTime)
            
            // Subtract reminder minutes
            calendar.add(Calendar.MINUTE, -reminderMinutes)

            // Don't schedule if it's in the past
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                // Try next week
                calendar.add(Calendar.WEEK_OF_YEAR, 1)
            }

            val requestCode = scheduleId.hashCode()

            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = "CLASS_REMINDER"
                putExtra("scheduleId", scheduleId)
                putExtra("subject", subject)
                putExtra("startTime", startTime)
                putExtra("reminderMinutes", reminderMinutes)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Schedule with setAlarmClock for most reliable delivery
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(calendar.timeInMillis, pendingIntent),
                        pendingIntent
                    )
                } else {
                    // Fallback for when exact alarms not permitted
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(calendar.timeInMillis, pendingIntent),
                    pendingIntent
                )
            }

            // Save to prefs for tracking
            saveScheduledAlarm(requestCode)

            Log.d(TAG, "Scheduled notification for $subject at ${calendar.time}")

        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling notification for $subject: ${e.message}")
        }
    }

    /**
     * Gets the next occurrence of a day/time combination.
     */
    private fun getNextOccurrence(day: String, time: String): Calendar {
        val calendar = Calendar.getInstance()
        
        // Parse time (HH:mm format)
        val timeParts = time.split(":")
        val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0

        // Map day name to Calendar constant
        val targetDay = when (day.lowercase()) {
            "sunday" -> Calendar.SUNDAY
            "monday" -> Calendar.MONDAY
            "tuesday" -> Calendar.TUESDAY
            "wednesday" -> Calendar.WEDNESDAY
            "thursday" -> Calendar.THURSDAY
            "friday" -> Calendar.FRIDAY
            "saturday" -> Calendar.SATURDAY
            else -> Calendar.MONDAY
        }

        // Set the time
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // Calculate days until target day
        val currentDay = calendar.get(Calendar.DAY_OF_WEEK)
        var daysUntil = targetDay - currentDay
        if (daysUntil < 0) {
            daysUntil += 7
        } else if (daysUntil == 0 && calendar.timeInMillis <= System.currentTimeMillis()) {
            daysUntil = 7 // Next week
        }

        calendar.add(Calendar.DAY_OF_YEAR, daysUntil)

        return calendar
    }

    /**
     * Schedules a single immediate test notification.
     */
    fun scheduleTestNotification(delaySeconds: Int = 5) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.SECOND, delaySeconds)

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = "CLASS_REMINDER"
            putExtra("scheduleId", "test")
            putExtra("subject", "Test Notification")
            putExtra("startTime", "now")
            putExtra("reminderMinutes", 0)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            999999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )

        Log.d(TAG, "Test notification scheduled for ${delaySeconds} seconds")
    }

    /**
     * Shows a notification immediately.
     */
    fun showNotification(
        title: String,
        message: String,
        notificationId: Int = System.currentTimeMillis().toInt()
    ) {
        val intent = Intent(context, StudentMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .build()

        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "Notification shown: $title")
    }

    /**
     * Cancels all scheduled alarms.
     */
    fun cancelAllAlarms() {
        val scheduledAlarms = getScheduledAlarms()
        for (requestCode in scheduledAlarms) {
            val intent = Intent(context, NotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
        clearScheduledAlarms()
        Log.d(TAG, "Cancelled ${scheduledAlarms.size} alarms")
    }

    private fun saveScheduledAlarm(requestCode: Int) {
        val alarms = getScheduledAlarms().toMutableSet()
        alarms.add(requestCode)
        prefs.edit().putStringSet(KEY_SCHEDULED_ALARMS, alarms.map { it.toString() }.toSet()).apply()
    }

    private fun getScheduledAlarms(): Set<Int> {
        return prefs.getStringSet(KEY_SCHEDULED_ALARMS, emptySet())
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet() ?: emptySet()
    }

    private fun clearScheduledAlarms() {
        prefs.edit().remove(KEY_SCHEDULED_ALARMS).apply()
    }
}


