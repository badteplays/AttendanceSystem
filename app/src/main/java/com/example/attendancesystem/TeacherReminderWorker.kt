package com.example.attendancesystem

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class TeacherReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser
            
            if (currentUser == null) {
                return Result.failure()
            }

            // Get teacher's name from Firestore
            val db = FirebaseFirestore.getInstance()
            val userDoc = db.collection("users").document(currentUser.uid).get().await()
            val teacherName = userDoc.getString("name") ?: "Teacher"

            // Check if teacher has any schedules for today
            val hasSchedulesToday = checkTodaySchedules(currentUser.uid)
            
            if (hasSchedulesToday) {
                showNotification(
                    "Daily Attendance Reminder",
                    "Good morning, $teacherName! Don't forget to generate QR codes for your classes today.",
                    applicationContext
                )
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private suspend fun checkTodaySchedules(teacherId: String): Boolean {
        return try {
            val db = FirebaseFirestore.getInstance()
            val today = java.util.Calendar.getInstance().getDisplayName(
                java.util.Calendar.DAY_OF_WEEK,
                java.util.Calendar.LONG,
                java.util.Locale.getDefault()
            ) ?: ""

            val schedules = db.collection("schedules")
                .whereEqualTo("teacherId", teacherId)
                .whereEqualTo("day", today)
                .get()
                .await()

            !schedules.isEmpty
        } catch (e: Exception) {
            false
        }
    }

    private fun showNotification(title: String, message: String, context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "teacher_reminder_channel"

        // Create notification channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Teacher Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily reminders for teachers"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Create intent to open TeacherDashboardActivity
        val intent = Intent(context, TeacherDashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(1001, notification)
    }
} 