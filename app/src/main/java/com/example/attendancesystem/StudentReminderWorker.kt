package com.example.attendancesystem

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.attendancesystem.notifications.LocalNotificationManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Locale

/**
 * Background worker that runs periodically to check for upcoming classes.
 * This acts as a backup to AlarmManager-based notifications.
 * WorkManager runs every 15 minutes minimum.
 */
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
            
            // Check for upcoming classes and show notification if within window
            val upcomingClass = getUpcomingClass(user.uid, reminderMinutes)
            if (upcomingClass != null) {
                val (subject, timeUntil) = upcomingClass
                val notificationManager = LocalNotificationManager.getInstance(applicationContext)
                notificationManager.showNotification(
                    title = "📚 Class Reminder",
                    message = "$subject class starts in ~$timeUntil minutes. Time to head to class!"
                )
            }
            
            // Also ensure alarms are scheduled (in case they were cleared)
            LocalNotificationManager.getInstance(applicationContext).scheduleAllClassNotifications()
            
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("StudentReminder", "Worker error: ${e.message}", e)
            Result.failure()
        }
    }

    private suspend fun getUpcomingClass(studentId: String, reminderMinutes: Int): Pair<String, Int>? {
        return try {
            val db = FirebaseFirestore.getInstance()
            val userDoc = db.collection("users").document(studentId).get().await()
            val section = userDoc.getString("section")?.uppercase() ?: return null

            val day = Calendar.getInstance().getDisplayName(
                Calendar.DAY_OF_WEEK,
                Calendar.LONG,
                Locale.getDefault()
            ) ?: return null

            val schedules = db.collection("schedules")
                .whereEqualTo("day", day)
                .whereEqualTo("section", section)
                .get().await()

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
                    
                    // Check if class is within reminder window (±5 minute buffer for WorkManager imprecision)
                    if (minutesUntilClass in (reminderMinutes - 5)..(reminderMinutes + 10)) {
                        return Pair(subject, minutesUntilClass)
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
