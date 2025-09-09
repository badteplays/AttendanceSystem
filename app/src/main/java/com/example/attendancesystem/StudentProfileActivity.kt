package com.example.attendancesystem

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class StudentProfileActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var txtStudentName: TextView
    private lateinit var txtStudentId: TextView
    private lateinit var txtStats: TextView
    private lateinit var listAttendance: ListView
    private val attendanceList = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_profile)

        db = FirebaseFirestore.getInstance()
        
        // Get student info from intent
        val studentId = intent.getStringExtra("studentId") ?: ""
        val studentName = intent.getStringExtra("studentName") ?: "Unknown Student"

        if (studentId.isEmpty()) {
            Toast.makeText(this, "Student ID not provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize views
        txtStudentName = findViewById(R.id.txtStudentName)
        txtStudentId = findViewById(R.id.txtStudentId)
        txtStats = findViewById(R.id.txtStats)
        listAttendance = findViewById(R.id.listAttendance)

        // Set student info
        txtStudentName.text = studentName
        txtStudentId.text = "ID: $studentId"

        // Setup attendance list
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, attendanceList)
        listAttendance.adapter = adapter

        // Load attendance data
        loadAttendance(studentId)

        // Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Student Profile"
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun loadAttendance(studentId: String) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        val thirtyDaysAgo = calendar.time

        db.collection("attendance")
            .whereEqualTo("studentId", studentId)
            .whereGreaterThanOrEqualTo("timestamp", thirtyDaysAgo)
            .get()
            .addOnSuccessListener { documents ->
                var present = 0
                var late = 0
                var absent = 0
                var excused = 0
                
                attendanceList.clear()
                
                for (doc in documents) {
                    val status = doc.getString("status") ?: ""
                    val date = doc.getTimestamp("timestamp")?.toDate()
                    val subject = doc.getString("subject") ?: ""
                    val section = doc.getString("section") ?: ""
                    
                    val dateStr = if (date != null) {
                        android.text.format.DateFormat.format("MMM dd, yyyy HH:mm", date).toString()
                    } else {
                        "Unknown date"
                    }
                    
                    when (status) {
                        "PRESENT" -> present++
                        "LATE" -> late++
                        "ABSENT" -> absent++
                        "EXCUSED" -> excused++
                    }
                    
                    attendanceList.add("$dateStr\n$subject ($section) - $status")
                }
                
                val total = present + late + absent + excused
                val attendancePercent = if (total > 0) {
                    ((present + late) * 100 / total)
                } else {
                    0
                }
                
                txtStats.text = buildString {
                    append("Last 30 Days:\n")
                    append("Present: $present  ")
                    append("Late: $late  ")
                    append("Absent: $absent  ")
                    append("Excused: $excused\n")
                    append("Attendance Rate: $attendancePercent%")
                    
                    if (attendancePercent < 75) {
                        append("\n⚠️ Below 75% threshold")
                    }
                }
                
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load attendance: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
} 