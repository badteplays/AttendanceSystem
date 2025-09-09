package com.example.attendancesystem

import android.content.Intent
import android.os.Bundle
import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.LinearLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


class TeacherSchedulesListActivity : AppCompatActivity() {
    private lateinit var scheduleAdapter: ScheduleAdapter
    private lateinit var db: FirebaseFirestore
    private lateinit var teacherId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_schedules_list)

        db = FirebaseFirestore.getInstance()
        teacherId = FirebaseAuth.getInstance().currentUser?.uid ?: ""



        scheduleAdapter = ScheduleAdapter(
            schedules = listOf(),
            onGenerateQR = { schedule -> generateQRCodeForSchedule(schedule) },
            onRemove = { schedule -> removeSchedule(schedule) },
            onEdit = { schedule -> editSchedule(schedule) }
        )
        val recyclerView = findViewById<RecyclerView>(R.id.scheduleRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = scheduleAdapter

        loadSchedules()

        // Setup Add Schedule buttons
        setupAddScheduleButtons()
        
        setupBottomNavigation()
        
        // Test the time parsing logic
        testTimeParsing()
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        // Return to dashboard with animation
        val intent = Intent(this, TeacherDashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        finish()
    }
    
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    private fun loadSchedules() {
        db.collection("schedules")
            .whereEqualTo("teacherId", teacherId)
            .get()
            .addOnSuccessListener { result ->
                val scheduleList = result.map { doc ->
                    val startTime = doc.getString("startTime") ?: ""
                    val endTime = doc.getString("endTime") ?: ""
                    
                    // Convert 24-hour format to 12-hour format with AM/PM
                    val timeString = if (startTime.isNotEmpty() && endTime.isNotEmpty()) {
                        "${convertTo12HourFormat(startTime)}-${convertTo12HourFormat(endTime)}"
                    } else {
                        ""
                    }
                    
                    android.util.Log.d("ScheduleDebug", "Schedule ${doc.id}: startTime='$startTime', endTime='$endTime', converted='$timeString'")
                    
                    ScheduleItem(
                        id = doc.id,
                        subject = doc.getString("subject") ?: "",
                        section = doc.getString("section") ?: "",
                        day = doc.getString("day") ?: "",
                        time = timeString,
                        room = doc.getString("room") ?: ""
                    )
                }
                scheduleAdapter.updateSchedules(scheduleList)
                updateScheduleCounters(scheduleList)
                        }
    }
    
    private fun convertTo12HourFormat(time24: String): String {
        try {
            // Parse time in format "HH:mm"
            val parts = time24.split(":")
            if (parts.size != 2) return time24
            
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            
            val amPm = if (hour < 12) "am" else "pm"
            val hour12 = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            
            return String.format("%d:%02d%s", hour12, minute, amPm)
        } catch (e: Exception) {
            android.util.Log.d("ScheduleDebug", "Error converting time '$time24': ${e.message}")
            return time24
        }
    }
    
    private fun updateScheduleCounters(scheduleList: List<ScheduleItem>) {
        // Update total schedules counter
        val totalSchedules = scheduleList.size
        findViewById<android.widget.TextView>(R.id.textTotalSchedules)?.text = totalSchedules.toString()
        
        // Update active today counter
        val today = getCurrentDayOfWeek()
        val currentTime = getCurrentTimeInMinutes()
        
        android.util.Log.d("ScheduleDebug", "Today: $today, Current time: $currentTime minutes")
        android.util.Log.d("ScheduleDebug", "Total schedules: $totalSchedules")
        
        val activeToday = scheduleList.count { schedule ->
            val scheduleDay = schedule.day.lowercase().trim()
            val todayLower = today.lowercase().trim()
            
            android.util.Log.d("ScheduleDebug", "Schedule: ${schedule.subject}, Day: '${schedule.day}' -> '$scheduleDay', Time: '${schedule.time}'")
            android.util.Log.d("ScheduleDebug", "Day match: '$scheduleDay' == '$todayLower' ? ${scheduleDay == todayLower}")
            
            if (scheduleDay == todayLower) {
                // Parse the schedule time (e.g., "11:50pm-12:10am")
                val timeRange = parseTimeRange(schedule.time)
                android.util.Log.d("ScheduleDebug", "Time range parsed: $timeRange")
                
                if (timeRange != null) {
                    val (startTime, endTime) = timeRange
                    val isActive = isTimeInRange(currentTime, startTime, endTime)
                    android.util.Log.d("ScheduleDebug", "Time check: $currentTime in range $startTime-$endTime? $isActive")
                    isActive
                } else {
                    android.util.Log.d("ScheduleDebug", "Failed to parse time range")
                    false
                }
            } else {
                false
            }
        }
        
        android.util.Log.d("ScheduleDebug", "Active today count: $activeToday")
        findViewById<android.widget.TextView>(R.id.textActiveSchedules)?.text = activeToday.toString()
    }
    
    private fun getCurrentDayOfWeek(): String {
        val calendar = java.util.Calendar.getInstance()
        return when (calendar.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.SUNDAY -> "Sunday"
            java.util.Calendar.MONDAY -> "Monday"
            java.util.Calendar.TUESDAY -> "Tuesday"
            java.util.Calendar.WEDNESDAY -> "Wednesday"
            java.util.Calendar.THURSDAY -> "Thursday"
            java.util.Calendar.FRIDAY -> "Friday"
            java.util.Calendar.SATURDAY -> "Saturday"
            else -> "Unknown"
        }
    }
    
    private fun getCurrentTimeInMinutes(): Int {
        val calendar = java.util.Calendar.getInstance()
        return calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
    }
    
    private fun parseTimeRange(timeString: String): Pair<Int, Int>? {
        try {
            android.util.Log.d("ScheduleDebug", "Parsing time range: '$timeString'")
            
            // Handle formats like "11:50pm-12:10am" or "11:50PM-12:10AM"
            // Also try different separators like " - " or "–"
            val separators = listOf("-", " - ", "–", " – ")
            var parts: List<String>? = null
            
            for (separator in separators) {
                val splitParts = timeString.split(separator)
                if (splitParts.size == 2) {
                    parts = splitParts
                    android.util.Log.d("ScheduleDebug", "Split with '$separator': ${splitParts}")
                    break
                }
            }
            
            if (parts == null || parts.size != 2) {
                android.util.Log.d("ScheduleDebug", "Could not split time range with any separator")
                return null
            }
            
            val startTimeStr = parts[0].trim()
            val endTimeStr = parts[1].trim()
            
            android.util.Log.d("ScheduleDebug", "Start time string: '$startTimeStr', End time string: '$endTimeStr'")
            
            val startTime = parseTimeToMinutes(startTimeStr)
            val endTime = parseTimeToMinutes(endTimeStr)
            
            return if (startTime != null && endTime != null) {
                android.util.Log.d("ScheduleDebug", "Successfully parsed time range: $startTime to $endTime")
                Pair(startTime, endTime)
            } else {
                android.util.Log.d("ScheduleDebug", "Failed to parse one or both times: start=$startTime, end=$endTime")
                null
            }
        } catch (e: Exception) {
            android.util.Log.d("ScheduleDebug", "Exception parsing time range '$timeString': ${e.message}")
            return null
        }
    }
    
    private fun parseTimeToMinutes(timeStr: String): Int? {
        try {
            android.util.Log.d("ScheduleDebug", "Parsing time: '$timeStr'")
            val timePattern = Regex("(\\d{1,2}):(\\d{2})\\s*(am|pm)", RegexOption.IGNORE_CASE)
            val match = timePattern.find(timeStr)
            
            if (match == null) {
                android.util.Log.d("ScheduleDebug", "No regex match for: '$timeStr'")
                return null
            }
            
            var hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            val amPm = match.groupValues[3].lowercase()
            
            android.util.Log.d("ScheduleDebug", "Parsed: hour=$hour, minute=$minute, amPm=$amPm")
            
            // Convert to 24-hour format
            if (amPm == "pm" && hour != 12) {
                hour += 12
            } else if (amPm == "am" && hour == 12) {
                hour = 0
            }
            
            val totalMinutes = hour * 60 + minute
            android.util.Log.d("ScheduleDebug", "Converted to minutes: $totalMinutes")
            return totalMinutes
        } catch (e: Exception) {
            android.util.Log.d("ScheduleDebug", "Exception parsing time '$timeStr': ${e.message}")
            return null
        }
    }
    
    private fun isTimeInRange(currentTime: Int, startTime: Int, endTime: Int): Boolean {
        // Handle cases where the time range crosses midnight (e.g., 11:50pm to 12:10am)
        return if (endTime < startTime) {
            // Time range crosses midnight
            currentTime >= startTime || currentTime <= endTime
        } else {
            // Normal time range within the same day
            currentTime >= startTime && currentTime <= endTime
        }
    }

    private fun generateQRCodeForSchedule(schedule: ScheduleItem) {
        val session = hashMapOf(
            "teacherId" to teacherId,
            "scheduleId" to schedule.id,
            "subject" to schedule.subject,
            "section" to schedule.section,
            "day" to schedule.day,
            "time" to schedule.time,
            "room" to schedule.room,
            "createdAt" to System.currentTimeMillis(),
            "expiresAt" to System.currentTimeMillis() + 15 * 60 * 1000
        )
        db.collection("attendance_sessions")
            .add(session)
            .addOnSuccessListener {
                Toast.makeText(this, "QR/session generated!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to generate QR/session", Toast.LENGTH_SHORT).show()
            }
    }

    private fun removeSchedule(schedule: ScheduleItem) {
        db.collection("schedules").document(schedule.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Schedule removed", Toast.LENGTH_SHORT).show()
                loadSchedules()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to remove schedule", Toast.LENGTH_SHORT).show()
            }
    }

    private fun editSchedule(schedule: ScheduleItem) {
        // Navigate to TeacherScheduleActivity for editing testing
        val intent = Intent(this, TeacherScheduleActivity::class.java)
        intent.putExtra("edit_schedule_id", schedule.id)
        startActivity(intent)
    }
    
    private fun setupAddScheduleButtons() {
        // Add Schedule Button
        findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonAddSchedule)?.setOnClickListener {
            openAddScheduleActivity()
        }
    }
    
    private fun openAddScheduleActivity() {
        try {
            val intent = Intent(this, TeacherScheduleActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening schedule creator: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupBottomNavigation() {
        // Home button -> container
        findViewById<LinearLayout>(R.id.nav_home_btn)?.setOnClickListener {
            val intent = Intent(this, TeacherMainActivity::class.java)
            intent.putExtra("selected_tab", R.id.nav_home)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
        
        // Schedule button -> container
        findViewById<LinearLayout>(R.id.nav_schedule_btn)?.setOnClickListener {
            val intent = Intent(this, TeacherMainActivity::class.java)
            intent.putExtra("selected_tab", R.id.nav_schedule)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
        
        // Attendance button -> container
        findViewById<LinearLayout>(R.id.nav_attendance_btn)?.setOnClickListener {
            val intent = Intent(this, TeacherMainActivity::class.java)
            intent.putExtra("selected_tab", R.id.nav_attendance)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
        
        // Analytics button -> container
        findViewById<LinearLayout>(R.id.nav_analytics_btn)?.setOnClickListener {
            val intent = Intent(this, TeacherMainActivity::class.java)
            intent.putExtra("selected_tab", R.id.nav_analytics)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
        
        // Settings button -> container
        findViewById<LinearLayout>(R.id.nav_settings_btn)?.setOnClickListener {
            val intent = Intent(this, TeacherMainActivity::class.java)
            intent.putExtra("selected_tab", R.id.nav_settings)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
    }
    
    private fun testTimeParsing() {
        android.util.Log.d("ScheduleDebug", "=== TESTING TIME PARSING ===")
        val today = getCurrentDayOfWeek()
        val currentTime = getCurrentTimeInMinutes()
        
        android.util.Log.d("ScheduleDebug", "Current day: $today")
        android.util.Log.d("ScheduleDebug", "Current time: $currentTime minutes (${currentTime/60}:${currentTime%60})")
        
        // Test your specific time format
        val testTimes = listOf(
            "11:50pm-12:10am",
            "11:50PM-12:10AM", 
            "11:50 pm - 12:10 am",
            "11:50pm–12:10am"
        )
        
        testTimes.forEach { timeString ->
            android.util.Log.d("ScheduleDebug", "Testing: '$timeString'")
            val result = parseTimeRange(timeString)
            if (result != null) {
                val (start, end) = result
                val isInRange = isTimeInRange(currentTime, start, end)
                android.util.Log.d("ScheduleDebug", "  -> Start: $start (${start/60}:${String.format("%02d", start%60)}), End: $end (${end/60}:${String.format("%02d", end%60)}), In range: $isInRange")
            } else {
                android.util.Log.d("ScheduleDebug", "  -> Failed to parse")
            }
        }
        android.util.Log.d("ScheduleDebug", "=== END TEST ===")
    }
}
