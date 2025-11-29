package com.example.attendancesystem

import android.app.Application
import android.content.Context
import com.example.attendancesystem.utils.ThemeManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.work.WorkManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit

class AttendanceApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        val themeManager = ThemeManager.getInstance(this)
        themeManager.initializeTheme()
        
        initializeStudentReminders()
    }
    
    private fun initializeStudentReminders() {
        try {
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser
            
            if (currentUser != null) {
                val db = FirebaseFirestore.getInstance()
                db.collection("users").document(currentUser.uid)
                    .get()
                    .addOnSuccessListener { document ->
                        val role = document.getString("role")
                        if (role == "student") {
                            val prefs = getSharedPreferences("student_prefs", Context.MODE_PRIVATE)
                            val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
                            
                            if (notificationsEnabled) {
                                scheduleStudentReminders()
                            }
                        }
                    }
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceApp", "Error initializing reminders: ${e.message}", e)
        }
    }
    
    private fun scheduleStudentReminders() {
        try {
            val workRequest = PeriodicWorkRequestBuilder<StudentReminderWorker>(
                15, TimeUnit.MINUTES
            ).build()
            
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "student_reminder_work",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            
            android.util.Log.d("AttendanceApp", "Background class reminders scheduled (runs every 15 min, even when app is closed)")
        } catch (e: Exception) {
            android.util.Log.e("AttendanceApp", "Error scheduling reminders: ${e.message}", e)
        }
    }
}
