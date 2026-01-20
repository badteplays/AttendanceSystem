package com.example.attendancesystem

import android.app.Application
import android.content.Context
import com.example.attendancesystem.notifications.LocalNotificationManager
import com.example.attendancesystem.utils.ThemeManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AttendanceApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        val themeManager = ThemeManager.getInstance(this)
        themeManager.initializeTheme()
        
        initializeNotifications()
    }
    
    private fun initializeNotifications() {
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
                                scheduleClassNotifications()
                            }
                        }
                    }
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceApp", "Error initializing notifications: ${e.message}", e)
        }
    }
    
    private fun scheduleClassNotifications() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val notificationManager = LocalNotificationManager.getInstance(this@AttendanceApplication)
                notificationManager.scheduleAllClassNotifications()
                android.util.Log.d("AttendanceApp", "Class notifications scheduled with precise timing")
            } catch (e: Exception) {
                android.util.Log.e("AttendanceApp", "Error scheduling notifications: ${e.message}", e)
            }
        }
    }
}

<<<<<<< HEAD

=======
}
>>>>>>> origin/master
