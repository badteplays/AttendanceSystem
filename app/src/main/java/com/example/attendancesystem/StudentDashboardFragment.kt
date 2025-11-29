package com.example.attendancesystem

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.widget.Button
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.attendancesystem.utils.ProfilePictureManager
import com.google.firebase.firestore.Query
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.Color

class StudentDashboardFragment : Fragment() {

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var textWelcomeStudent: TextView
    private lateinit var textName: TextView
    private lateinit var textCourse: TextView
    private lateinit var imageProfilePic: ImageView
    private lateinit var textInitials: TextView
    private lateinit var buttonScanQR: Button
    private lateinit var buttonViewHistory: Button
    private lateinit var fabScanQR: FloatingActionButton
    private lateinit var textTodayStatus: TextView
    private lateinit var textStatusTime: TextView
    private lateinit var statusIndicator: View
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var userId: String = ""
    private var studentName: String = ""
    private var attendanceListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_student_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        loadUserData()
        loadTodayStatus()
        setupClickListeners()
        setupSwipeRefresh()
    }

    private fun initializeViews(view: View) {
        try {
            swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
            textWelcomeStudent = view.findViewById(R.id.textWelcomeStudent)
            textName = view.findViewById(R.id.textName)
            textCourse = view.findViewById(R.id.textCourse)
            imageProfilePic = view.findViewById(R.id.imageProfilePic)
            textInitials = view.findViewById(R.id.textInitials)
            buttonScanQR = view.findViewById(R.id.buttonScanQR)
            buttonViewHistory = view.findViewById(R.id.buttonViewHistory)
            fabScanQR = view.findViewById(R.id.fabScanQR)
            textTodayStatus = view.findViewById(R.id.textTodayStatus)
            textStatusTime = view.findViewById(R.id.textStatusTime)
            statusIndicator = view.findViewById(R.id.statusIndicator)
        } catch (e: Exception) {
            android.util.Log.e("StudentDashboard", "Error initializing views", e)
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            loadUserData()
            loadTodayStatus()
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun loadTodayStatus() {
        val currentUser = auth.currentUser ?: return
        val prefs = requireContext().getSharedPreferences("student_attendance", Context.MODE_PRIVATE)

        val justMarkedSubject = arguments?.getString("justMarkedSubject")
        val justMarkedTime = arguments?.getLong("justMarkedTime", 0L) ?: 0L

        if (justMarkedSubject != null && justMarkedTime > 0) {

            prefs.edit().apply {
                putString("markedSubject", justMarkedSubject)
                putLong("markedTime", justMarkedTime)
                apply()
            }

            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val timeString = timeFormat.format(Date(justMarkedTime))

            android.util.Log.d("StudentDashboard", "✓✓✓ Just marked attendance! Showing immediately ✓✓✓")
            updateStatusUI("Present - $justMarkedSubject", "Marked at $timeString", Color.parseColor("#4CAF50"))

            arguments?.clear()
            return
        }

        android.util.Log.d("StudentDashboard", "Checking for current class")
        db.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { userDoc ->
                val section = userDoc.getString("section")?.uppercase() ?: return@addOnSuccessListener
                checkCurrentClassWithMarkedStatus(currentUser.uid, section, prefs)
            }
            .addOnFailureListener { e ->
                android.util.Log.e("StudentDashboard", "Error loading user: ${e.message}", e)
                updateStatusUI("Not marked yet", "", Color.parseColor("#9E9E9E"))
            }
    }

    private fun checkCurrentClassWithMarkedStatus(userId: String, section: String, prefs: android.content.SharedPreferences) {

        val calendar = Calendar.getInstance()
        val currentDay = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()) ?: ""
        val nowMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        android.util.Log.d("StudentDashboard", "=== CHECKING CURRENT CLASS ===")
        android.util.Log.d("StudentDashboard", "Current day: $currentDay, Current time: ${nowMinutes / 60}:${String.format("%02d", nowMinutes % 60)}")
        android.util.Log.d("StudentDashboard", "Section: $section")

        db.collection("schedules")
            .whereEqualTo("section", section)
            .get()
            .addOnSuccessListener { allSchedules ->

                if (allSchedules.isEmpty) {
                    android.util.Log.d("StudentDashboard", "No schedules found with uppercase section, trying lowercase...")
                    checkSchedulesWithLowercaseSection(userId, section.lowercase(), currentDay, nowMinutes, prefs)
                    return@addOnSuccessListener
                }

                val schedules = allSchedules.filter {
                    it.getString("day")?.equals(currentDay, ignoreCase = true) == true
                }
                android.util.Log.d("StudentDashboard", "Found ${schedules.size} schedules for today out of ${allSchedules.size()} total schedules")
                var currentSchedule: com.google.firebase.firestore.DocumentSnapshot? = null

                for (schedule in schedules) {
                    val startTime = schedule.getString("startTime") ?: continue
                    val endTime = schedule.getString("endTime") ?: continue

                    val startMin = parseTimeToMinutes(startTime)
                    val endMin = parseTimeToMinutes(endTime)

                    val isWithinClass = if (endMin < startMin) {

                        nowMinutes >= startMin || nowMinutes <= endMin
                    } else {
                        nowMinutes >= startMin && nowMinutes <= endMin
                    }

                    if (isWithinClass) {
                        currentSchedule = schedule
                        break
                    }
                }

                if (currentSchedule != null) {
                    val scheduleId = currentSchedule!!.id
                    val subject = currentSchedule!!.getString("subject") ?: ""

                    val markedSubject = prefs.getString("markedSubject", null)
                    val markedTime = prefs.getLong("markedTime", 0L)

                    if (markedSubject == subject && markedTime > 0) {

                        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                        val timeString = timeFormat.format(Date(markedTime))
                        android.util.Log.d("StudentDashboard", "✓ Current class '$subject' matches saved marked status")
                        updateStatusUI("Present - $subject", "Marked at $timeString", Color.parseColor("#4CAF50"))
                    } else {

                        android.util.Log.d("StudentDashboard", "Current class '$subject' - not marked yet")
                        updateStatusUI("Not marked yet", "Scan QR for $subject", Color.parseColor("#9E9E9E"))
                    }
                } else {

                    prefs.edit().clear().apply()
                    android.util.Log.d("StudentDashboard", "No current class - clearing saved status")

                    val nextClass = schedules.mapNotNull { schedule ->
                        val startTime = schedule.getString("startTime") ?: return@mapNotNull null
                        val startMin = parseTimeToMinutes(startTime)
                        if (startMin > nowMinutes) {
                            Pair(startMin, schedule.getString("subject") ?: "")
                        } else null
                    }.minByOrNull { it.first }

                    if (nextClass != null) {
                        val hour = nextClass.first / 60
                        val minute = nextClass.first % 60
                        val timeStr = String.format("%d:%02d %s",
                            if (hour == 0) 12 else if (hour > 12) hour - 12 else hour,
                            minute,
                            if (hour < 12) "AM" else "PM"
                        )
                        updateStatusUI("Next class: ${nextClass.second}", "at $timeStr", Color.parseColor("#2196F3"))
                    } else {
                        updateStatusUI("Not marked yet", "No class right now", Color.parseColor("#9E9E9E"))
                    }
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("StudentDashboard", "Error loading schedules: ${e.message}", e)
                updateStatusUI("Not marked yet", "", Color.parseColor("#9E9E9E"))
            }
    }

    private fun checkSchedulesWithLowercaseSection(
        userId: String,
        lowercaseSection: String,
        currentDay: String,
        nowMinutes: Int,
        prefs: android.content.SharedPreferences
    ) {
        db.collection("schedules")
            .whereEqualTo("section", lowercaseSection)
            .get()
            .addOnSuccessListener { allSchedules ->
                android.util.Log.d("StudentDashboard", "Found ${allSchedules.size()} schedules with lowercase section")

                val schedules = allSchedules.filter {
                    it.getString("day")?.equals(currentDay, ignoreCase = true) == true
                }
                android.util.Log.d("StudentDashboard", "Found ${schedules.size} schedules for today out of ${allSchedules.size()} total schedules")
                var currentSchedule: com.google.firebase.firestore.DocumentSnapshot? = null

                for (schedule in schedules) {
                    val startTime = schedule.getString("startTime") ?: continue
                    val endTime = schedule.getString("endTime") ?: continue

                    val startMin = parseTimeToMinutes(startTime)
                    val endMin = parseTimeToMinutes(endTime)

                    val isWithinClass = if (endMin < startMin) {

                        nowMinutes >= startMin || nowMinutes <= endMin
                    } else {
                        nowMinutes >= startMin && nowMinutes <= endMin
                    }

                    if (isWithinClass) {
                        currentSchedule = schedule
                        break
                    }
                }

                if (currentSchedule != null) {
                    val scheduleId = currentSchedule!!.id
                    val subject = currentSchedule!!.getString("subject") ?: ""

                    val markedSubject = prefs.getString("markedSubject", null)
                    val markedTime = prefs.getLong("markedTime", 0L)

                    if (markedSubject == subject && markedTime > 0) {

                        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                        val timeString = timeFormat.format(Date(markedTime))
                        android.util.Log.d("StudentDashboard", "✓ Current class '$subject' matches saved marked status")
                        updateStatusUI("Present - $subject", "Marked at $timeString", Color.parseColor("#4CAF50"))
                    } else {

                        android.util.Log.d("StudentDashboard", "Current class '$subject' - not marked yet")
                        updateStatusUI("Not marked yet", "Scan QR for $subject", Color.parseColor("#9E9E9E"))
                    }
                } else {

                    prefs.edit().clear().apply()
                    android.util.Log.d("StudentDashboard", "No current class")

                    val nextClass = schedules
                        .mapNotNull { schedule ->
                            val startTime = schedule.getString("startTime") ?: return@mapNotNull null
                            val startMin = parseTimeToMinutes(startTime)
                            if (startMin > nowMinutes) {
                                Pair(startMin, schedule.getString("subject") ?: "")
                            } else null
                        }
                        .minByOrNull { it.first }

                    if (nextClass != null) {
                        val hour = nextClass.first / 60
                        val minute = nextClass.first % 60
                        val timeStr = String.format("%d:%02d %s",
                            if (hour == 0) 12 else if (hour > 12) hour - 12 else hour,
                            minute,
                            if (hour < 12) "AM" else "PM"
                        )
                        updateStatusUI("Next class: ${nextClass.second}", "at $timeStr", Color.parseColor("#2196F3"))
                    } else {
                        updateStatusUI("Not marked yet", "No class right now", Color.parseColor("#9E9E9E"))
                    }
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("StudentDashboard", "Error loading schedules with lowercase: ${e.message}", e)
                updateStatusUI("Not marked yet", "", Color.parseColor("#9E9E9E"))
            }
    }

    private fun checkAttendanceForCurrentClass(userId: String, scheduleId: String, subject: String) {

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayStart = com.google.firebase.Timestamp(calendar.time)

        android.util.Log.d("StudentDashboard", "=== QUERYING ATTENDANCE FOR CURRENT CLASS ===")
        android.util.Log.d("StudentDashboard", "userId: $userId")
        android.util.Log.d("StudentDashboard", "scheduleId: $scheduleId")
        android.util.Log.d("StudentDashboard", "subject: $subject")
        android.util.Log.d("StudentDashboard", "timestamp >= $todayStart")

        attendanceListener?.remove()
        attendanceListener = db.collection("attendance")
            .whereEqualTo("userId", userId)
            .whereEqualTo("scheduleId", scheduleId)
            .whereEqualTo("subject", subject)
            .whereGreaterThanOrEqualTo("timestamp", todayStart)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    android.util.Log.e("StudentDashboard", "Error loading attendance: ${e.message}", e)
                    updateStatusUI("Not marked yet", "Scan QR to mark attendance", Color.parseColor("#9E9E9E"))
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val attendance = snapshot.documents.first()
                    val status = attendance.getString("status") ?: "PRESENT"
                    val timestamp = attendance.getTimestamp("timestamp")

                    android.util.Log.d("StudentDashboard", "✓✓✓ ATTENDANCE FOUND! ✓✓✓")
                    android.util.Log.d("StudentDashboard", "Document ID: ${attendance.id}")
                    android.util.Log.d("StudentDashboard", "status: $status")
                    android.util.Log.d("StudentDashboard", "timestamp: $timestamp")
                    android.util.Log.d("StudentDashboard", "Total documents in snapshot: ${snapshot.documents.size}")

                    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                    val timeString = timestamp?.toDate()?.let { timeFormat.format(it) } ?: ""

                    when (status) {
                        "PRESENT" -> updateStatusUI("Present - $subject", timeString, Color.parseColor("#4CAF50"))
                        "LATE" -> updateStatusUI("Late - $subject", timeString, Color.parseColor("#FF9800"))
                        "EXCUSED" -> updateStatusUI("Excused - $subject", timeString, Color.parseColor("#2196F3"))
                        "CUTTING" -> updateStatusUI("Cutting - $subject", timeString, Color.parseColor("#F44336"))
                        else -> updateStatusUI("Marked - $subject", timeString, Color.parseColor("#4CAF50"))
                    }
                } else {

                    android.util.Log.d("StudentDashboard", "✗✗✗ NO ATTENDANCE FOUND ✗✗✗")
                    android.util.Log.d("StudentDashboard", "Subject being queried: $subject")
                    android.util.Log.d("StudentDashboard", "ScheduleId being queried: $scheduleId")
                    updateStatusUI("Not marked yet", "Scan QR for $subject", Color.parseColor("#9E9E9E"))
                }
            }
    }

    private fun parseTimeToMinutes(time: String): Int {
        return try {
            val parts = time.split(":")
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            hour * 60 + minute
        } catch (e: Exception) {
            0
        }
    }

    private fun updateStatusUI(statusText: String, timeText: String, color: Int) {
        textTodayStatus.text = statusText
        textStatusTime.text = timeText
        statusIndicator.setBackgroundColor(color)
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
            return
        }

        db.collection("users").document(currentUser.uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    studentName = snapshot.getString("name") ?: "Student"
                    val welcomeText = "Welcome, $studentName!"
                    textWelcomeStudent.text = welcomeText
                    val studentSection = snapshot.getString("section") ?: "Section"
                    val profilePicUrl = snapshot.getString("profilePicUrl")

                    textName.text = studentName
                    textCourse.text = studentSection

                    val profileManager = ProfilePictureManager.getInstance()
                    profileManager.loadProfilePicture(requireContext(), imageProfilePic, textInitials, studentName, "ST")
                }
            }
    }

    private fun setupClickListeners() {

        buttonScanQR.setOnClickListener {
            try {

                switchToFragment(QRScannerFragment())
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error opening scanner: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        buttonViewHistory.setOnClickListener {
            try {

                switchToFragment(StudentAttendanceHistoryFragment())
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error opening history: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        fabScanQR.setOnClickListener {
            try {

                switchToFragment(QRScannerFragment())
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error opening scanner: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun switchToFragment(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(requireContext(), message, duration).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        attendanceListener?.remove()
        attendanceListener = null
    }
}
