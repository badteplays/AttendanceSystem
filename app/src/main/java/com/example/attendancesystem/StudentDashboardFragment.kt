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
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
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

<<<<<<< HEAD
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var textWelcomeStudent: TextView? = null
    private var textName: TextView? = null
    private var textCourse: TextView? = null
    private var imageProfilePic: ImageView? = null
    private var textInitials: TextView? = null
    private var buttonScanQR: View? = null
    private var buttonViewHistory: View? = null
    private var fabScanQR: ExtendedFloatingActionButton? = null
    private var textTodayStatus: TextView? = null
    private var textStatusTime: TextView? = null
    private var statusIndicator: View? = null
    private var textPresentCount: TextView? = null
    private var textAbsentCount: TextView? = null
    private var textLateCount: TextView? = null
=======
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var textWelcomeStudent: TextView
    private lateinit var textName: TextView
    private lateinit var textCourse: TextView
    private lateinit var imageProfilePic: ImageView
    private lateinit var textInitials: TextView
    private lateinit var buttonScanQR: View
    private lateinit var buttonViewHistory: View
    private lateinit var fabScanQR: FloatingActionButton
    private lateinit var textTodayStatus: TextView
    private lateinit var textStatusTime: TextView
    private lateinit var statusIndicator: View
    
    // Stats counters
    private lateinit var textPresentCount: TextView
    private lateinit var textAbsentCount: TextView
    private lateinit var textLateCount: TextView
>>>>>>> origin/master
    
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
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
<<<<<<< HEAD
        try {
            initializeViews(view)
            loadUserData()
            loadTodayStatus()
            loadAttendanceStats()
            setupClickListeners()
            setupSwipeRefresh()
        } catch (e: Exception) {
            android.util.Log.e("StudentDashboard", "Error in onViewCreated: ${e.message}", e)
        }
    }

    private fun initializeViews(view: View) {
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
        textPresentCount = view.findViewById(R.id.textPresentCount)
        textAbsentCount = view.findViewById(R.id.textAbsentCount)
        textLateCount = view.findViewById(R.id.textLateCount)
=======

        initializeViews(view)
        loadUserData()
        loadTodayStatus()
        loadAttendanceStats()
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
            
            // Initialize stat counters
            textPresentCount = view.findViewById(R.id.textPresentCount)
            textAbsentCount = view.findViewById(R.id.textAbsentCount)
            textLateCount = view.findViewById(R.id.textLateCount)
        } catch (e: Exception) {
            android.util.Log.e("StudentDashboard", "Error initializing views", e)
        }
>>>>>>> origin/master
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout?.setOnRefreshListener {
            loadUserData()
            loadTodayStatus()
            loadAttendanceStats()
<<<<<<< HEAD
            swipeRefreshLayout?.isRefreshing = false
=======
            swipeRefreshLayout.isRefreshing = false
>>>>>>> origin/master
        }
    }
    
    /**
     * Loads attendance statistics for the current month
     */
    private fun loadAttendanceStats() {
        val currentUser = auth.currentUser ?: return
        
        // Get start of current month
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val monthStart = com.google.firebase.Timestamp(calendar.time)
        
        android.util.Log.d("StudentDashboard", "Loading attendance stats from: $monthStart")
        
        db.collection("attendance")
            .whereEqualTo("userId", currentUser.uid)
            .whereGreaterThanOrEqualTo("timestamp", monthStart)
            .get()
            .addOnSuccessListener { snapshot ->
                var presentCount = 0
                var absentCount = 0
                var lateCount = 0
                
                for (doc in snapshot.documents) {
                    val status = doc.getString("status")?.uppercase() ?: "PRESENT"
                    when (status) {
                        "PRESENT" -> presentCount++
                        "ABSENT" -> absentCount++
                        "LATE" -> lateCount++
                        "EXCUSED" -> presentCount++ // Count excused as present
                        "CUTTING" -> absentCount++ // Count cutting as absent
                    }
                }
                
                android.util.Log.d("StudentDashboard", "Stats - Present: $presentCount, Absent: $absentCount, Late: $lateCount")
                
<<<<<<< HEAD
                textPresentCount?.text = presentCount.toString()
                textAbsentCount?.text = absentCount.toString()
                textLateCount?.text = lateCount.toString()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("StudentDashboard", "Error loading stats: ${e.message}", e)
                textPresentCount?.text = "0"
                textAbsentCount?.text = "0"
                textLateCount?.text = "0"
=======
                // Update UI
                textPresentCount.text = presentCount.toString()
                textAbsentCount.text = absentCount.toString()
                textLateCount.text = lateCount.toString()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("StudentDashboard", "Error loading stats: ${e.message}", e)
                // Set defaults on error
                textPresentCount.text = "0"
                textAbsentCount.text = "0"
                textLateCount.text = "0"
>>>>>>> origin/master
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
            updateStatusUI("Present - $justMarkedSubject", "Marked at $timeString", Color.parseColor("#22C55E"))
<<<<<<< HEAD
=======
            
            // Reload stats after marking
>>>>>>> origin/master
            loadAttendanceStats()

            arguments?.clear()
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
                updateStatusUI("Not marked yet", "", Color.parseColor("#71717A"))
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
                        updateStatusUI("Present - $subject", "Marked at $timeString", Color.parseColor("#22C55E"))
                    } else {

                        android.util.Log.d("StudentDashboard", "Current class '$subject' - not marked yet")
                        updateStatusUI("Not marked yet", "Scan QR for $subject", Color.parseColor("#71717A"))
                    }
                    checkAttendanceForCurrentClass(userId, scheduleId, subject, prefs)
                } else {

                    prefs.edit().clear().apply()
                    android.util.Log.d("StudentDashboard", "No current class - clearing saved status")
                    attendanceListener?.remove()
                    attendanceListener = null

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
                        updateStatusUI("Next class: ${nextClass.second}", "at $timeStr", Color.parseColor("#3B82F6"))
                    } else {
                        updateStatusUI("No more classes today", "", Color.parseColor("#71717A"))
                    }
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("StudentDashboard", "Error loading schedules: ${e.message}", e)
                updateStatusUI("Not marked yet", "", Color.parseColor("#71717A"))
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
                        updateStatusUI("Present - $subject", "Marked at $timeString", Color.parseColor("#22C55E"))
                    } else {

                        android.util.Log.d("StudentDashboard", "Current class '$subject' - not marked yet")
                        updateStatusUI("Not marked yet", "Scan QR for $subject", Color.parseColor("#71717A"))
                    }
                    checkAttendanceForCurrentClass(userId, scheduleId, subject, prefs)
                } else {

                    prefs.edit().clear().apply()
                    android.util.Log.d("StudentDashboard", "No current class")
                    attendanceListener?.remove()
                    attendanceListener = null

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
                        updateStatusUI("Next class: ${nextClass.second}", "at $timeStr", Color.parseColor("#3B82F6"))
                    } else {
                        updateStatusUI("No more classes today", "", Color.parseColor("#71717A"))
                    }
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("StudentDashboard", "Error loading schedules with lowercase: ${e.message}", e)
                updateStatusUI("Not marked yet", "", Color.parseColor("#71717A"))
            }
    }

    private fun checkAttendanceForCurrentClass(
        userId: String,
        scheduleId: String,
        subject: String,
        prefs: android.content.SharedPreferences
    ) {

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
                    updateStatusUI("Not marked yet", "Scan QR to mark attendance", Color.parseColor("#71717A"))
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
                        "PRESENT" -> updateStatusUI("Present - $subject", timeString, Color.parseColor("#22C55E"))
                        "LATE" -> updateStatusUI("Late - $subject", timeString, Color.parseColor("#F59E0B"))
                        "EXCUSED" -> updateStatusUI("Excused - $subject", timeString, Color.parseColor("#3B82F6"))
<<<<<<< HEAD
=======
                        "CUTTING" -> updateStatusUI("Cutting - $subject", timeString, Color.parseColor("#EF4444"))
>>>>>>> origin/master
                        else -> updateStatusUI("Marked - $subject", timeString, Color.parseColor("#22C55E"))
                    }
                    prefs.edit().apply {
                        putString("markedSubject", subject)
                        putLong("markedTime", timestamp?.toDate()?.time ?: System.currentTimeMillis())
                        apply()
                    }
                    loadAttendanceStats()
                } else {

                    android.util.Log.d("StudentDashboard", "✗✗✗ NO ATTENDANCE FOUND ✗✗✗")
                    android.util.Log.d("StudentDashboard", "Subject being queried: $subject")
                    android.util.Log.d("StudentDashboard", "ScheduleId being queried: $scheduleId")
<<<<<<< HEAD
                    val markedSubject = prefs.getString("markedSubject", null)
                    val markedTime = prefs.getLong("markedTime", 0L)
                    val isRecentMarked = markedSubject == subject &&
                        markedTime > 0 &&
                        System.currentTimeMillis() - markedTime <= 2 * 60 * 1000
                    if (isRecentMarked) {
                        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                        val timeString = timeFormat.format(Date(markedTime))
                        updateStatusUI("Present - $subject", "Marked at $timeString", Color.parseColor("#22C55E"))
                    } else {
                        updateStatusUI("Not marked yet", "Scan QR for $subject", Color.parseColor("#71717A"))
                    }
=======
                    updateStatusUI("Not marked yet", "Scan QR for $subject", Color.parseColor("#71717A"))
>>>>>>> origin/master
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
        textTodayStatus?.text = statusText
        textStatusTime?.text = timeText
        statusIndicator?.setBackgroundColor(color)
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            try {
                startActivity(Intent(requireContext(), LoginActivity::class.java))
                requireActivity().finish()
            } catch (e: Exception) {
                android.util.Log.e("StudentDashboard", "Error redirecting to login: ${e.message}")
            }
            return
        }

        db.collection("users").document(currentUser.uid)
            .addSnapshotListener { snapshot, _ ->
                if (!isAdded) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    studentName = snapshot.getString("name") ?: "Student"
<<<<<<< HEAD
                    textWelcomeStudent?.text = getGreeting()
=======
                    textWelcomeStudent.text = getGreeting()
>>>>>>> origin/master
                    val studentSection = snapshot.getString("section") ?: "Section"

<<<<<<< HEAD
                    textName?.text = studentName
                    textCourse?.text = studentSection.uppercase()
=======
                    textName.text = studentName
                    textCourse.text = studentSection.uppercase()
>>>>>>> origin/master

                    val img = imageProfilePic
                    val ini = textInitials
                    if (img != null && ini != null) {
                        ProfilePictureManager.getInstance().loadProfilePicture(requireContext(), img, ini, studentName, "ST")
                    }
                }
            }
    }
    
    private fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    private fun setupClickListeners() {
        buttonScanQR?.setOnClickListener {
            try { switchToFragment(QRScannerFragment()) }
            catch (e: Exception) { android.util.Log.e("StudentDashboard", "Error: ${e.message}") }
        }

        buttonViewHistory?.setOnClickListener {
            try { switchToFragment(StudentAttendanceHistoryFragment()) }
            catch (e: Exception) { android.util.Log.e("StudentDashboard", "Error: ${e.message}") }
        }

        fabScanQR?.setOnClickListener {
            try { switchToFragment(QRScannerFragment()) }
            catch (e: Exception) { android.util.Log.e("StudentDashboard", "Error: ${e.message}") }
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
