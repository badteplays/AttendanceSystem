package com.example.attendancesystem

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StudentReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = applicationContext.getSharedPreferences("student_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("notifications_enabled", true)) return Result.success()

            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser ?: return Result.success()

            val reminderMinutes = prefs.getInt("reminder_minutes", 10)
            
            val upcomingClass = getUpcomingClass(user.uid, reminderMinutes)
            if (upcomingClass != null) {
                val (subject, timeUntil) = upcomingClass
                showNotification(
                    "Class Reminder",
                    "$subject class starts in $timeUntil minutes. Get ready!"
                )
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private suspend fun getUpcomingClass(studentId: String, reminderMinutes: Int): Pair<String, Int>? {
        return try {
            val db = FirebaseFirestore.getInstance()
            val userDoc = db.collection("users").document(studentId).get().await()
            val section = userDoc.getString("section")?.uppercase() ?: return null

            android.util.Log.d("StudentReminder", "Checking for upcoming class - Section: $section, Reminder: $reminderMinutes min")

            val day = Calendar.getInstance().getDisplayName(
                Calendar.DAY_OF_WEEK,
                Calendar.LONG,
                Locale.getDefault()
            ) ?: return null

            android.util.Log.d("StudentReminder", "Current day: $day")

            val schedules = db.collection("schedules")
                .whereEqualTo("day", day)
                .whereEqualTo("section", section)
                .get().await()
            
            android.util.Log.d("StudentReminder", "Found ${schedules.size()} schedules for today")

            val now = Calendar.getInstance()
            val currentTimeInMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

            for (doc in schedules.documents) {
                val startTime = doc.getString("startTime") ?: continue
                val subject = doc.getString("subject") ?: "Class"
                
                try {
                    val timeParts = startTime.split(":")
                    val classHour = timeParts[0].toInt()
                    val classMinute = timeParts[1].toInt()
                    val classTimeInMinutes = classHour * 60 + classMinute
                    
                    val minutesUntilClass = classTimeInMinutes - currentTimeInMinutes
                    
                    android.util.Log.d("StudentReminder", "$subject at $startTime - Minutes until: $minutesUntilClass")
                    
                    if (minutesUntilClass in (reminderMinutes - 2)..(reminderMinutes + 2)) {
                        android.util.Log.d("StudentReminder", "Found upcoming class: $subject in $minutesUntilClass minutes")
                        return Pair(subject, minutesUntilClass)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("StudentReminder", "Error parsing time: ${e.message}")
                    continue
                }
            }
            android.util.Log.d("StudentReminder", "No upcoming classes within reminder window")
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun showNotification(title: String, message: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "student_reminders"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Class Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for upcoming classes"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
        
        val intent = Intent(applicationContext, StudentMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pending = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .build()
            
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}


