package com.example.attendancesystem

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.attendancesystem.models.AttendanceHistoryItem
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class StudentAttendanceHistoryActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var attendancePercentage: TextView
    private lateinit var warningText: TextView
    private lateinit var adapter: AttendanceHistoryAdapter
    private val attendanceList = mutableListOf<AttendanceHistoryItem>()
    private val db = FirebaseFirestore.getInstance()
    private val currentUser = FirebaseAuth.getInstance().currentUser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_attendance_history)

        recyclerView = findViewById(R.id.attendanceHistoryRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        attendancePercentage = findViewById(R.id.attendancePercentage)
        warningText = findViewById(R.id.warningText)

        setupRecyclerView()
        setupBottomNavigation()
        loadAttendanceHistory()
    }

    private fun setupRecyclerView() {
        adapter = AttendanceHistoryAdapter(attendanceList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupBottomNavigation() {
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNavigationView.setOnItemSelectedListener { item ->
            val intent = Intent(this, StudentMainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intent.putExtra("selected_tab", item.itemId)
            startActivity(intent)
            finish()
            true
        }
        bottomNavigationView.selectedItemId = R.id.nav_history
    }

    private fun loadAttendanceHistory() {
        progressBar.visibility = View.VISIBLE
        currentUser?.let { user ->
            db.collection("attendance")
                .whereEqualTo("studentId", user.uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener { documents ->
                    attendanceList.clear()
                    var totalClasses = 0
                    var attendedClasses = 0

                    for (document in documents) {
                        val timestamp = document.getTimestamp("timestamp")
                        val subject = document.getString("subject") ?: ""
                        val status = document.getBoolean("verified") ?: false
                        
                        if (status) attendedClasses++
                        totalClasses++

                        timestamp?.let {
                            attendanceList.add(AttendanceHistoryItem(
                                date = formatDate(it),
                                subject = subject,
                                status = if (status) "Present" else "Absent"
                            ))
                        }
                    }

                    // Calculate attendance percentage
                    val percentage = if (totalClasses > 0) {
                        (attendedClasses.toDouble() / totalClasses.toDouble() * 100)
                    } else {
                        0.0
                    }

                    attendancePercentage.text = "Attendance: ${String.format("%.1f", percentage)}%"
                    
                    // Show warning if attendance is below 75%
                    if (percentage < 75) {
                        warningText.visibility = View.VISIBLE
                        warningText.text = "Warning: Your attendance is below 75%"
                    }

                    adapter.notifyDataSetChanged()
                    progressBar.visibility = View.GONE
                }
                .addOnFailureListener { e ->
                    // Handle error
                    progressBar.visibility = View.GONE
                }
        }
    }

    private fun formatDate(timestamp: Timestamp): String {
        val date = timestamp.toDate()
        val format = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return format.format(date)
    }
}