package com.example.attendancesystem

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.auth.FirebaseAuth

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val prefs = getSharedPreferences("student_prefs", Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
        if (!notificationsEnabled) return
        val title = remoteMessage.notification?.title ?: "Notification"
        val body = remoteMessage.notification?.body ?: ""
        
        // Determine the appropriate activity based on login state
        val intent = getAppropriateIntent()
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
        val channelId = "default_channel"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Default Channel", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }
        notificationManager.notify(0, notificationBuilder.build())
    }
    
    private fun getAppropriateIntent(): Intent {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        
        // If not logged in, go to LoginActivity which will handle auto-login
        if (currentUser == null) {
            return Intent(this, LoginActivity::class.java)
        }
        
        // User is logged in - send them to LoginActivity which will route them appropriately
        // LoginActivity already has logic to detect logged-in users and navigate to correct dashboard
        // This avoids blocking the main thread with Firestore queries
        return Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }
}
