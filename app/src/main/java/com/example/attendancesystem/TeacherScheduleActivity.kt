package com.example.attendancesystem

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import java.util.concurrent.TimeUnit

class TeacherScheduleActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var teacherId: String
    private var editingScheduleId: String? = null

    private lateinit var editSubject: TextInputEditText
    private lateinit var editSection: TextInputEditText
    private lateinit var spinnerDay: Spinner
    private lateinit var timePickerStart: TimePicker
    private lateinit var timePickerEnd: TimePicker
    private lateinit var editNotes: TextInputEditText
    private lateinit var buttonSave: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.teacher_schedule)

        db = FirebaseFirestore.getInstance()
        teacherId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        initializeViews()
        setupDaySpinner()
        setupBottomNavigation()
        setupToolbar()
        setupSaveButton()
        
        // Check if editing existing schedule
        editingScheduleId = intent.getStringExtra("edit_schedule_id")
        if (editingScheduleId != null) {
            loadScheduleForEdit()
        }
    }

    private fun initializeViews() {
        editSubject = findViewById(R.id.editSubject)
        editSection = findViewById(R.id.editSection)
        spinnerDay = findViewById(R.id.spinnerDay)
        timePickerStart = findViewById(R.id.timePickerStart)
        timePickerEnd = findViewById(R.id.timePickerEnd)
        editNotes = findViewById(R.id.editNotes)
        buttonSave = findViewById(R.id.buttonSave)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            finish()
        }
        
        // Update title based on mode
        if (editingScheduleId != null) {
            toolbar.title = "Edit Schedule"
                    buttonSave.text = "Update Schedule"
        } else {
            toolbar.title = "Create Schedule"
            buttonSave.text = "Save Schedule"
        }
    }

    private fun setupDaySpinner() {
        val days = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, days)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDay.adapter = adapter
    }

    private fun setupSaveButton() {
        buttonSave.setOnClickListener {
            saveSchedule()
        }
    }

        private fun saveSchedule() {
        // Check authentication first
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated. Please log in again.", Toast.LENGTH_LONG).show()
            return
        }
        
        val subject = editSubject.text.toString().trim()
        val section = editSection.text.toString().trim().lowercase() // Normalize to lowercase
        val day = spinnerDay.selectedItem.toString()
        val notes = editNotes.text.toString().trim()

        if (subject.isEmpty() || section.isEmpty()) {
            Toast.makeText(this, "Please fill in Subject and Section", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Debug info
        android.util.Log.d("ScheduleSave", "User ID: ${currentUser.uid}")
        android.util.Log.d("ScheduleSave", "Attempting to save schedule: $subject - $section")

        // Get time from pickers
            val startHour = timePickerStart.hour
            val startMinute = timePickerStart.minute
            val endHour = timePickerEnd.hour
            val endMinute = timePickerEnd.minute

        val startTime = String.format("%02d:%02d", startHour, startMinute)
        val endTime = String.format("%02d:%02d", endHour, endMinute)

            val schedule = hashMapOf(
            "teacherId" to currentUser.uid,
                "subject" to subject,
                "section" to section,
                "day" to day,
                "startTime" to startTime,
                "endTime" to endTime,
            "notes" to notes,
            "lastGeneratedDate" to "",
            "createdAt" to com.google.firebase.Timestamp.now()
            )
        
        android.util.Log.d("ScheduleSave", "Schedule data: $schedule")

            if (editingScheduleId != null) {
            // Update existing schedule
                db.collection("schedules").document(editingScheduleId!!)
                    .set(schedule)
                    .addOnSuccessListener {
                    Toast.makeText(this, "Schedule updated successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to update schedule: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
            // Create new schedule
                db.collection("schedules")
                    .add(schedule)
                    .addOnSuccessListener { docRef ->
                    Toast.makeText(this, "Schedule created successfully!", Toast.LENGTH_SHORT).show()
                        scheduleAutoQR(docRef.id, day, startHour, startMinute)
                    finish()
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("ScheduleSave", "Failed to create schedule", e)
                    Toast.makeText(this, "Failed to create schedule: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun loadScheduleForEdit() {
        editingScheduleId?.let { scheduleId ->
            db.collection("schedules").document(scheduleId)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        editSubject.setText(doc.getString("subject") ?: "")
                        editSection.setText(doc.getString("section") ?: "")
                        editNotes.setText(doc.getString("notes") ?: "")
                        
                        // Set day spinner
                        val day = doc.getString("day") ?: ""
                        val dayAdapter = spinnerDay.adapter as ArrayAdapter<String>
                        val dayPosition = dayAdapter.getPosition(day)
                        if (dayPosition >= 0) {
                            spinnerDay.setSelection(dayPosition)
                        }
                        
                        // Set time pickers
                        val startTime = doc.getString("startTime") ?: "09:00"
                        val endTime = doc.getString("endTime") ?: "10:00"
                        
                        val startParts = startTime.split(":")
                        if (startParts.size == 2) {
                            timePickerStart.hour = startParts[0].toIntOrNull() ?: 9
                            timePickerStart.minute = startParts[1].toIntOrNull() ?: 0
                        }
                        
                        val endParts = endTime.split(":")
                        if (endParts.size == 2) {
                            timePickerEnd.hour = endParts[0].toIntOrNull() ?: 10
                            timePickerEnd.minute = endParts[1].toIntOrNull() ?: 0
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to load schedule: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun scheduleAutoQR(scheduleId: String, day: String, hour: Int, minute: Int) {
        // Schedule automatic QR generation for class time
        val calendar = Calendar.getInstance()
        val dayOfWeek = when (day) {
            "Sunday" -> Calendar.SUNDAY
            "Monday" -> Calendar.MONDAY
            "Tuesday" -> Calendar.TUESDAY
            "Wednesday" -> Calendar.WEDNESDAY
            "Thursday" -> Calendar.THURSDAY
            "Friday" -> Calendar.FRIDAY
            "Saturday" -> Calendar.SATURDAY
            else -> Calendar.MONDAY
        }
        
        calendar.set(Calendar.DAY_OF_WEEK, dayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        
        // If the time has passed this week, schedule for next week
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.WEEK_OF_YEAR, 1)
        }
        
        val delay = calendar.timeInMillis - System.currentTimeMillis()
        
        val data = Data.Builder()
            .putString("scheduleId", scheduleId)
            .build()
        
        val workRequest = OneTimeWorkRequestBuilder<TeacherReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()
        
        WorkManager.getInstance(this).enqueue(workRequest)
    }

    private fun setupBottomNavigation() {
        // Home button
        findViewById<LinearLayout>(R.id.nav_home_btn)?.setOnClickListener { 
            try {
                val intent = Intent(this, TeacherDashboardActivity::class.java)
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this, "Error opening dashboard: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Schedule button (current page - already selected)
        findViewById<LinearLayout>(R.id.nav_schedule_btn)?.setOnClickListener { 
            try {
                val intent = Intent(this, TeacherSchedulesListActivity::class.java)
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this, "Error opening schedules: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Attendance button
        findViewById<LinearLayout>(R.id.nav_attendance_btn)?.setOnClickListener { 
            try {
                val intent = Intent(this, TeacherAttendanceOverviewActivity::class.java)
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this, "Error opening attendance: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Analytics button
        findViewById<LinearLayout>(R.id.nav_analytics_btn)?.setOnClickListener { 
            try {
                val intent = Intent(this, AnalyticsActivity::class.java)
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this, "Error opening analytics: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Settings button
        findViewById<LinearLayout>(R.id.nav_settings_btn)?.setOnClickListener { 
            try {
                val intent = Intent(this, TeacherOptionsActivity::class.java)
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this, "Error opening settings: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}