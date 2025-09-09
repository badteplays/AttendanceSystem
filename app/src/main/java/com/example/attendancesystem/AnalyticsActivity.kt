package com.example.attendancesystem


import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.Toast
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.attendancesystem.models.AttendanceStatus

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class AnalyticsActivity : AppCompatActivity() {
    
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    
    // Teacher schedule data
    private val teacherSubjects = mutableListOf<String>()
    private val subjectSections = mutableMapOf<String, MutableList<String>>()


    
    // Statistics TextViews
    private lateinit var totalClassesText: TextView
    private lateinit var avgAttendanceText: TextView
    private lateinit var totalStudentsText: TextView
    private lateinit var bestPerformingClassText: TextView
    private lateinit var attendanceRateText: TextView
    private lateinit var punctualityRateText: TextView
    
    // Filters
    private lateinit var periodSpinner: Spinner
    private lateinit var subjectSpinner: Spinner
    private lateinit var sectionSpinner: Spinner
    
    // Cards
    private lateinit var overviewCard: CardView
    

    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)
        
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        
        setupViews()
        loadTeacherSchedules()
        setupBottomNavigation()
    }
    
    private fun setupViews() {
        // Statistics
        totalClassesText = findViewById(R.id.totalClassesText)
        avgAttendanceText = findViewById(R.id.avgAttendanceText)
        totalStudentsText = findViewById(R.id.totalStudentsText)
        bestPerformingClassText = findViewById(R.id.bestPerformingClassText)
        attendanceRateText = findViewById(R.id.attendanceRateText)
        punctualityRateText = findViewById(R.id.punctualityRateText)
        
        // Filters
        periodSpinner = findViewById(R.id.periodSpinner)
        subjectSpinner = findViewById(R.id.subjectSpinner)
        sectionSpinner = findViewById(R.id.sectionSpinner)
        
        // Cards
        overviewCard = findViewById(R.id.overviewCard)
    }
    
    private fun loadTeacherSchedules() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }
        
        db.collection("schedules")
            .whereEqualTo("teacherId", currentUser.uid)
            .get()
            .addOnSuccessListener { documents ->
                teacherSubjects.clear()
                subjectSections.clear()
                
                for (document in documents) {
                    val subject = document.getString("subject") ?: continue
                    val section = document.getString("section") ?: continue
                    
                    // Add subject if not already present
                    if (!teacherSubjects.contains(subject)) {
                        teacherSubjects.add(subject)
                    }
                    
                    // Add section to subject's section list
                    if (!subjectSections.containsKey(subject)) {
                        subjectSections[subject] = mutableListOf()
                    }
                    if (!subjectSections[subject]!!.contains(section)) {
                        subjectSections[subject]!!.add(section)
                    }
                }
                
                // Sort subjects alphabetically
                teacherSubjects.sort()
                
                // Sort sections for each subject
                subjectSections.values.forEach { it.sort() }
                
                setupSpinners()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading teacher schedules: ${e.message}", Toast.LENGTH_SHORT).show()
                setupSpinners() // Setup with empty data as fallback
            }
    }
    
    private fun setupSpinners() {
        // Period filter
        val periods = listOf("Last 7 Days", "Last 30 Days", "Last 3 Months", "All Time")
        val periodAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, periods)
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        periodSpinner.adapter = periodAdapter
        
        // Subject filter - use teacher's actual subjects
        val subjects = mutableListOf("All Subjects")
        subjects.addAll(teacherSubjects)
        val subjectAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, subjects)
        subjectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        subjectSpinner.adapter = subjectAdapter
        
        // Section filter - initially show all sections
        updateSectionSpinner("All Subjects")
        
        // Set listeners
        val periodListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadAnalyticsData()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        val subjectListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedSubject = parent?.getItemAtPosition(position).toString()
                updateSectionSpinner(selectedSubject)
                loadAnalyticsData()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        val sectionListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadAnalyticsData()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        periodSpinner.onItemSelectedListener = periodListener
        subjectSpinner.onItemSelectedListener = subjectListener
        sectionSpinner.onItemSelectedListener = sectionListener
        
        // Load initial analytics data
        loadAnalyticsData()
    }
    
    private fun updateSectionSpinner(selectedSubject: String) {
        val sections = mutableListOf("All Sections")
        
        if (selectedSubject == "All Subjects") {
            // Show all sections from all subjects
            val allSections = mutableSetOf<String>()
            subjectSections.values.forEach { allSections.addAll(it) }
            sections.addAll(allSections.sorted())
        } else {
            // Show only sections for the selected subject
            subjectSections[selectedSubject]?.let { sections.addAll(it) }
        }
        
        val sectionAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sections)
        sectionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sectionSpinner.adapter = sectionAdapter
    }
    

    
    private fun loadAnalyticsData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }
        
        val selectedPeriod = periodSpinner.selectedItem?.toString() ?: "Last 30 Days"
        val selectedSubject = subjectSpinner.selectedItem?.toString() ?: "All Subjects"
        val selectedSection = sectionSpinner.selectedItem?.toString() ?: "All Sections"
        
        val calendar = Calendar.getInstance()
        val endDate = calendar.time
        
        // Calculate start date based on selected period
        when (selectedPeriod) {
            "Last 7 Days" -> calendar.add(Calendar.DAY_OF_YEAR, -7)
            "Last 30 Days" -> calendar.add(Calendar.DAY_OF_YEAR, -30)
            "Last 3 Months" -> calendar.add(Calendar.MONTH, -3)
            "All Time" -> calendar.add(Calendar.YEAR, -1)
        }
        val startDate = calendar.time
        
        // Build query - filter by teacher's classes only
        var query: Query = db.collection("attendance")
            .whereGreaterThanOrEqualTo("timestamp", startDate)
            .whereLessThanOrEqualTo("timestamp", endDate)
            .orderBy("timestamp")
        
        // Apply subject filter if specific subject is selected
        if (selectedSubject != "All Subjects") {
            query = query.whereEqualTo("subject", selectedSubject)
        }
        
        // Execute query
        query.get().addOnSuccessListener { documents ->
            var attendanceData = documents.map { doc ->
                AttendanceData(
                    studentName = doc.getString("studentName") ?: "",
                    subject = doc.getString("subject") ?: "",
                    section = doc.getString("section") ?: "",
                    status = AttendanceStatus.valueOf(doc.getString("status") ?: "PRESENT"),
                    timestamp = doc.getTimestamp("timestamp")?.toDate() ?: Date()
                )
            }
            
            // Filter to only show data for subjects/sections the teacher handles
            attendanceData = attendanceData.filter { attendance ->
                teacherSubjects.contains(attendance.subject) && 
                subjectSections[attendance.subject]?.contains(attendance.section) == true
            }
            
            // Apply section filtering if specific section is selected
            if (selectedSection != "All Sections") {
                attendanceData = attendanceData.filter { 
                    it.section.equals(selectedSection, ignoreCase = true) 
                }
            }
            
            updateAnalytics(attendanceData)
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Error loading analytics data: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateAnalytics(attendanceData: List<AttendanceData>) {
        updateStatistics(attendanceData)
    }
    
    private fun updateStatistics(attendanceData: List<AttendanceData>) {
        val totalRecords = attendanceData.size
        val uniqueStudents = attendanceData.map { it.studentName }.distinct().size
        val uniqueSubjects = attendanceData.map { it.subject }.distinct().size
        
        val presentCount = attendanceData.count { it.status == AttendanceStatus.PRESENT }
        val lateCount = attendanceData.count { it.status == AttendanceStatus.LATE }
        val absentCount = attendanceData.count { it.status == AttendanceStatus.ABSENT }
        val excusedCount = attendanceData.count { it.status == AttendanceStatus.EXCUSED }
        
        val attendanceRate = if (totalRecords > 0) (presentCount + lateCount).toFloat() / totalRecords * 100 else 0f
        val punctualityRate = if (totalRecords > 0) presentCount.toFloat() / totalRecords * 100 else 0f
        
        totalClassesText.text = totalRecords.toString()
        totalStudentsText.text = uniqueStudents.toString()
        attendanceRateText.text = String.format("%.1f%%", attendanceRate)
        punctualityRateText.text = String.format("%.1f%%", punctualityRate)
        
        // Best performing class
        val bestClass = attendanceData.groupBy { "${it.subject} - ${it.section}" }
            .map { (className, records) ->
                val classAttendanceRate = records.count { it.status == AttendanceStatus.PRESENT }.toFloat() / records.size * 100
                className to classAttendanceRate
            }
            .maxByOrNull { it.second }
        
        bestPerformingClassText.text = bestClass?.first ?: "N/A"
        avgAttendanceText.text = String.format("%.1f%%", bestClass?.second ?: 0f)
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
        
        // Schedule button
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
        
        // Analytics button (current page - already selected)
        findViewById<LinearLayout>(R.id.nav_analytics_btn)?.setOnClickListener { 
            Toast.makeText(this, "Already on Analytics", Toast.LENGTH_SHORT).show()
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
    
    data class AttendanceData(
        val studentName: String,
        val subject: String,
        val section: String,
        val status: AttendanceStatus,
        val timestamp: Date
    )
} 