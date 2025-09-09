package com.example.attendancesystem

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import android.widget.LinearLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.example.attendancesystem.models.Attendance
import com.example.attendancesystem.AttendanceListAdapter
import com.example.attendancesystem.ManualAddStudentDialog
import com.example.attendancesystem.TeacherReminderWorker
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.attendancesystem.utils.ProfilePictureManager
import java.util.*
import androidx.work.*
import java.util.concurrent.TimeUnit

class TeacherDashboardActivity : AppCompatActivity() {
    private lateinit var textQrExpiry: TextView
    private lateinit var buttonRenewQr: Button
    private lateinit var buttonShowQr: Button
    private lateinit var buttonRefreshAttendance: Button
    private lateinit var imageProfilePic: ImageView
    private lateinit var textInitials: TextView
    private lateinit var textName: TextView
    private lateinit var textDepartment: TextView
    private lateinit var attendanceRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var attendanceAdapter: AttendanceListAdapter
    private val attendanceList = mutableListOf<com.example.attendancesystem.models.Attendance>()
    private var countDownTimer: CountDownTimer? = null
    private var currentExpirationMinutes: Int = 15 // Default expiry
    private var latestSessionId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_dashboard_new)
        
        try {
            // View initializations
            textQrExpiry = findViewById(R.id.textQrExpiry)
            buttonRenewQr = findViewById(R.id.buttonRenewQr)
            buttonShowQr = findViewById(R.id.buttonShowQr)
            buttonRefreshAttendance = findViewById(R.id.buttonRefreshAttendance)
            imageProfilePic = findViewById(R.id.imageProfilePic)
            textInitials = findViewById(R.id.textInitials)
            textName = findViewById(R.id.textName)
            textDepartment = findViewById(R.id.textDepartment)
            attendanceRecyclerView = findViewById(R.id.attendanceRecyclerView)
            attendanceAdapter = AttendanceListAdapter(
                onItemClick = {},
                onManualAddClick = {
                    showManualAddStudentDialog()
                }
            )
            attendanceRecyclerView.adapter = attendanceAdapter
            attendanceRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        } catch (e: Exception) {
            android.util.Log.e("TeacherDashboard", "Error initializing views", e)
        }
        
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val currentUser = auth.currentUser
        
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid)
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null) {
                            android.util.Log.e("TeacherDashboard", "Firestore error: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null && snapshot.exists()) {
                            val name = snapshot.getString("name") ?: "Teacher Name"
                            val department = snapshot.getString("department") ?: "Department / Role"
                            val profilePicUrl = snapshot.getString("profilePicUrl")
                            textName.text = name
                            textDepartment.text = department
                            
                            // Use ProfilePictureManager to load profile picture globally
                            val profileManager = ProfilePictureManager.getInstance()
                            profileManager.loadProfilePicture(this@TeacherDashboardActivity, imageProfilePic, textInitials, name, "TC")
                        } else {
                            Toast.makeText(this, "User data not found.", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "Error loading user data: ${e.message}", Toast.LENGTH_LONG).show()
                        e.printStackTrace()
                    }
                }
        } else {
            Toast.makeText(this, "No current user found.", Toast.LENGTH_LONG).show()
        }
        // Enable button click listeners and session renewal
        buttonRenewQr.setOnClickListener { renewAttendanceSession() }
        buttonShowQr.setOnClickListener { showQrCodeDialog() }
        buttonRefreshAttendance.setOnClickListener {
            Toast.makeText(this, "Refreshing attendance...", Toast.LENGTH_SHORT).show()
            // Manually re-query attendance for the current session
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val sessionId = latestSessionId
            if (!sessionId.isNullOrEmpty()) {
                db.collection("attendance")
                    .whereEqualTo("sessionId", sessionId)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        attendanceList.clear()
                        snapshot.documents.mapNotNull { doc ->
                            doc.toObject(com.example.attendancesystem.models.Attendance::class.java)?.apply { id = doc.id }
                        }.let { attendanceList.addAll(it) }
                        attendanceAdapter.submitAttendanceList(attendanceList)
                        Toast.makeText(this, "Manual refresh complete.", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Refresh failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            } else {
                Toast.makeText(this, "No sessionId available.", Toast.LENGTH_SHORT).show()
            }
        }
        // Setup the addScheduleFab as the manual add student button
        val addScheduleFab = findViewById<FloatingActionButton>(R.id.addScheduleFab)
        addScheduleFab.setOnClickListener {
            showManualAddStudentDialog()
        }
        renewAttendanceSession()

        // Disable legacy bottom nav in this Activity. Use TeacherMainActivity container instead.

        val buttonAnalytics = findViewById<Button>(R.id.buttonAnalytics)
        buttonAnalytics.setOnClickListener {
            val intent = Intent(this, AnalyticsActivity::class.java)
            startActivity(intent)
        }

        // Schedule daily teacher reminder at 9:05 AM
        val workManager = WorkManager.getInstance(this)
        val currentTime = java.util.Calendar.getInstance()
        val targetTime = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 9)
            set(java.util.Calendar.MINUTE, 5)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (before(currentTime)) add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        val initialDelay = targetTime.timeInMillis - currentTime.timeInMillis
        val reminderRequest = PeriodicWorkRequestBuilder<TeacherReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            "teacher_reminder_work",
            ExistingPeriodicWorkPolicy.REPLACE,
            reminderRequest
        )
        // Low attendance alert (monthly check)
        checkLowAttendanceAlert()
        
        // Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(false) // Dashboard is home
    }
    
    override fun onBackPressed() {
        // Exit app with confirmation dialog
        AlertDialog.Builder(this)
            .setTitle("Exit App")
            .setMessage("Are you sure you want to exit?")
            .setPositiveButton("Yes") { _, _ ->
                super.onBackPressed()
            }
            .setNegativeButton("No", null)
            .show()
    }
    
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    private fun renewAttendanceSession() {
        countDownTimer?.cancel()
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val expiresAt = now + currentExpirationMinutes * 60 * 1000
        latestSessionId = sessionId
        val qrSession = hashMapOf(
            "sessionId" to sessionId,
            "createdAt" to now,
            "expiresAt" to expiresAt,
            "teacherId" to FirebaseAuth.getInstance().currentUser?.uid
        )
        FirebaseFirestore.getInstance().collection("attendance_sessions")
            .document(sessionId)
            .set(qrSession)
            .addOnSuccessListener {
                // Start listening for attendance for this session IMMEDIATELY after session is created
                attachAttendanceListener(sessionId)
                startExpirationCountdown(expiresAt)
            }
            .addOnFailureListener { textQrExpiry.text = "Error saving session" }
    }

    private fun attachAttendanceListener(sessionId: String?) {
        val db = FirebaseFirestore.getInstance()
        if (!sessionId.isNullOrEmpty()) {
            db.collection("attendance")
                .whereEqualTo("sessionId", sessionId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    attendanceList.clear()
                    snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject(com.example.attendancesystem.models.Attendance::class.java)?.apply {
                            id = doc.id
                        }
                    }?.let { attendanceList.addAll(it) }
                    runOnUiThread {
                        attendanceAdapter.submitAttendanceList(attendanceList)
                        attendanceAdapter.notifyDataSetChanged()
                    }
                    android.util.Log.d("TeacherDashboard", "[REALTIME] attendanceList size: ${attendanceList.size}")
                    attendanceList.forEach { android.util.Log.d("TeacherDashboard", "[REALTIME] Attendance: ${it.studentName}, sessionId=${it.sessionId}") }
                    val docCount = snapshot?.documents?.size ?: 0
                    val debugMsg = "[REALTIME] Attendance docs: $docCount\nSessionId: $sessionId"
                    android.util.Log.d("TeacherDashboard", debugMsg)
                    Toast.makeText(this, debugMsg, Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun startExpirationCountdown(expiresAt: Long) {
        val millisUntilExpiration = expiresAt - System.currentTimeMillis()
        countDownTimer = object : CountDownTimer(millisUntilExpiration, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 60000
                val seconds = (millisUntilFinished % 60000) / 1000
                textQrExpiry.text = "Expires in %02d:%02d".format(minutes, seconds)
            }
            override fun onFinish() {
                textQrExpiry.text = "Expired"
            }
        }.start()
    }

    private fun showSetExpiryDialog() {
        val picker = android.widget.NumberPicker(this).apply {
            minValue = 1
            maxValue = 60
            value = currentExpirationMinutes
        }
        AlertDialog.Builder(this)
            .setTitle("Set Expiry (minutes)")
            .setView(picker)
            .setPositiveButton("Set") { _, _ ->
                currentExpirationMinutes = picker.value
                renewAttendanceSession()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showQrCodeDialog() {
        val sessionId = latestSessionId ?: run {
            Toast.makeText(this, "No active QR session", Toast.LENGTH_SHORT).show()
            return
        }
        val teacherId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        // TODO: Pass scheduleId, subject, section from actual schedule if available
        val scheduleId = sessionId // For now, use sessionId as scheduleId placeholder
        val subject = "Attendance"
        val section = ""
        val qrData = com.example.attendancesystem.models.QRCodeData(
            teacherId = teacherId,
            sessionId = sessionId,
            userId = "",
            timestamp = System.currentTimeMillis(),
            scheduleId = scheduleId,
            subject = subject,
            section = section,
            expirationMinutes = currentExpirationMinutes
        )
        val json = qrData.toJson()
        val barcodeEncoder = com.journeyapps.barcodescanner.BarcodeEncoder()
        val bitmap = barcodeEncoder.encodeBitmap(json, com.google.zxing.BarcodeFormat.QR_CODE, 400, 400)
        val imageView = android.widget.ImageView(this).apply { setImageBitmap(bitmap) }
        AlertDialog.Builder(this)
            .setTitle("Attendance QR Code")
            .setView(imageView)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showManualAddStudentDialog() {
        // Use the correct scheduleId and sessionId
        val scheduleId = latestSessionId ?: "" // If you have a real scheduleId, use it here
        val sessionId = latestSessionId ?: ""
        val dialog = ManualAddStudentDialog(this, scheduleId, sessionId)
        dialog.show()
    }

    private fun checkLowAttendanceAlert() {
        val db = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: return
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -30)
        val thirtyDaysAgo = calendar.time
        db.collection("attendance")
            .whereGreaterThanOrEqualTo("timestamp", thirtyDaysAgo)
            .get()
            .addOnSuccessListener { documents ->
                val attendanceByStudent = mutableMapOf<String, Int>()
                val totalByStudent = mutableMapOf<String, Int>()
                for (doc in documents) {
                    val studentId = doc.getString("studentId") ?: continue
                    val status = doc.getString("status") ?: ""
                    totalByStudent[studentId] = totalByStudent.getOrDefault(studentId, 0) + 1
                    if (status == "PRESENT" || status == "LATE") {
                        attendanceByStudent[studentId] = attendanceByStudent.getOrDefault(studentId, 0) + 1
                    }
                }
                val lowAttendanceStudents = totalByStudent.filter { (studentId, total) ->
                    val attended = attendanceByStudent.getOrDefault(studentId, 0)
                    total > 0 && attended.toDouble() / total < 0.75
                }.keys
                if (lowAttendanceStudents.isNotEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("Low Attendance Alert")
                        .setMessage("Students with low attendance this month: ${lowAttendanceStudents.joinToString(", ")}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
    }

    private fun setupBottomNavigation() {
        // Home button (already selected)
        findViewById<LinearLayout>(R.id.nav_home_btn)?.setOnClickListener { 
            // Already on home, just refresh or do nothing
            Toast.makeText(this, "Already on Dashboard", Toast.LENGTH_SHORT).show()
        }
        
        // Schedule button
        findViewById<LinearLayout>(R.id.nav_schedule_btn)?.setOnClickListener { 
            try {
                val intent = Intent(this, TeacherSchedulesListActivity::class.java)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            } catch (e: Exception) {
                Toast.makeText(this, "Error opening schedules: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Attendance button
        findViewById<LinearLayout>(R.id.nav_attendance_btn)?.setOnClickListener { 
            try {
                val intent = Intent(this, TeacherAttendanceOverviewActivity::class.java)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            } catch (e: Exception) {
                Toast.makeText(this, "Error opening attendance: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Analytics button
        findViewById<LinearLayout>(R.id.nav_analytics_btn)?.setOnClickListener { 
            try {
                val intent = Intent(this, AnalyticsActivity::class.java)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            } catch (e: Exception) {
                Toast.makeText(this, "Error opening analytics: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Settings button
        findViewById<LinearLayout>(R.id.nav_settings_btn)?.setOnClickListener { 
            try {
                val intent = Intent(this, TeacherOptionsActivity::class.java)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            } catch (e: Exception) {
                Toast.makeText(this, "Error opening settings: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
