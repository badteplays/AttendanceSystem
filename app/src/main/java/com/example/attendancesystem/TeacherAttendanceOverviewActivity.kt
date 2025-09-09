package com.example.attendancesystem

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.attendancesystem.adapters.ScheduleAttendanceAdapter
import com.example.attendancesystem.models.Schedule
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class TeacherAttendanceOverviewActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ScheduleAttendanceAdapter
    private val scheduleList = mutableListOf<Schedule>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_attendance_overview)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupRecyclerView()
        setupBottomNavigation()
        loadSchedules()
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerSchedules)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ScheduleAttendanceAdapter(scheduleList) { schedule ->
            // Open attendance list for this schedule
            val intent = Intent(this, AttendanceListActivity::class.java)
            intent.putExtra("scheduleId", schedule.id)
            startActivity(intent)
        }
        recyclerView.adapter = adapter
    }

    private fun loadSchedules() {
        auth.currentUser?.uid?.let { teacherId ->
            db.collection("schedules")
                .whereEqualTo("teacherId", teacherId)
                .get()
                .addOnSuccessListener { documents ->
                    scheduleList.clear()
                    for (document in documents) {
                        val schedule = Schedule(
                            id = document.id,
                            subject = document.getString("subject") ?: "",
                            section = document.getString("section") ?: "",
                            teacherId = document.getString("teacherId") ?: "",
                            startTime = document.getString("startTime") ?: "",
                            endTime = document.getString("endTime") ?: "",
                            day = document.getString("day") ?: "",
                            lastGeneratedDate = document.getString("lastGeneratedDate") ?: ""
                        )
                        scheduleList.add(schedule)
                    }
                    adapter.notifyDataSetChanged()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error loading schedules: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
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
        
        // Attendance button (current page - already selected)
        findViewById<LinearLayout>(R.id.nav_attendance_btn)?.setOnClickListener { 
            Toast.makeText(this, "Already on Attendance", Toast.LENGTH_SHORT).show()
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
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this, "Error opening settings: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
