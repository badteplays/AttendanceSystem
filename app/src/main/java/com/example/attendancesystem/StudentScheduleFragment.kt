package com.example.attendancesystem

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.example.attendancesystem.models.StudentScheduleItem
import java.text.SimpleDateFormat
import java.util.*

class StudentScheduleFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: LinearLayout
    private lateinit var textTotalClasses: TextView
    private lateinit var textTodayClasses: TextView
    private lateinit var adapter: StudentScheduleAdapter
    private val scheduleList = ArrayList<StudentScheduleItem>()
    private val db = FirebaseFirestore.getInstance()
    private val currentUser = FirebaseAuth.getInstance().currentUser
    private var studentSection: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        android.util.Log.d("StudentScheduleFragment", "Creating view")
        return inflater.inflate(R.layout.fragment_student_schedule, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            initializeViews(view)
            setupRecyclerView()
            // Show a simple message first to test if fragment loads
            showEmptyState("Schedule loading...")
            loadStudentSection()
        } catch (e: Exception) {
            android.util.Log.e("StudentScheduleFragment", "Error in onViewCreated: ${e.message}", e)
            showEmptyState("Error loading schedule: ${e.message}")
        }
    }

    private fun initializeViews(view: View) {
        try {
            recyclerView = view.findViewById(R.id.studentScheduleRecyclerView)
            progressBar = view.findViewById(R.id.progressBar)
            emptyState = view.findViewById(R.id.emptyState)
            textTotalClasses = view.findViewById(R.id.textTotalClasses)
            textTodayClasses = view.findViewById(R.id.textTodayClasses)
        } catch (e: Exception) {
            android.util.Log.e("StudentScheduleFragment", "Error initializing views: ${e.message}", e)
            throw e
        }
    }

    private fun setupRecyclerView() {
        try {
            adapter = StudentScheduleAdapter(scheduleList)
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = adapter
        } catch (e: Exception) {
            android.util.Log.e("StudentScheduleFragment", "Error setting up recycler view: ${e.message}", e)
            throw e
        }
    }

    private fun loadStudentSection() {
        if (currentUser == null) {
            showEmptyState("User not authenticated")
            return
        }
        
        try {
            // First get the student's section
            db.collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        studentSection = document.getString("section") ?: ""
                        if (studentSection.isNotEmpty()) {
                            loadStudentSchedules()
                        } else {
                            showEmptyState("No section assigned")
                        }
                    } else {
                        showEmptyState("User data not found")
                    }
                }
                .addOnFailureListener { e ->
                    showEmptyState("Error loading user data: ${e.message}")
                }
        } catch (e: Exception) {
            showEmptyState("Error: ${e.message}")
        }
    }

    private fun loadStudentSchedules() {
        progressBar.visibility = View.VISIBLE
        
        try {
            // Query schedules that match the student's section (case-insensitive)
            db.collection("schedules")
                .whereEqualTo("section", studentSection.lowercase())
                .get()
                .addOnSuccessListener { documents ->
                scheduleList.clear()
                var todayClassesCount = 0
                val today = getCurrentDayOfWeek()

                for (document in documents) {
                    val subject = document.getString("subject") ?: ""
                    val section = document.getString("section") ?: ""
                    val day = document.getString("day") ?: ""
                    val startTime = document.getString("startTime") ?: ""
                    val endTime = document.getString("endTime") ?: ""
                    val notes = document.getString("notes") ?: ""
                    val teacherId = document.getString("teacherId") ?: ""

                    // Convert to 12-hour format for display
                    val timeString = convertTo12HourFormat(startTime, endTime)
                    
                    // Check if this is today's class
                    if (day.equals(today, ignoreCase = true)) {
                        todayClassesCount++
                    }

                    scheduleList.add(StudentScheduleItem(
                        id = document.id,
                        subject = subject,
                        section = section,
                        day = day,
                        time = timeString,
                        startTime = startTime,
                        endTime = endTime,
                        notes = notes,
                        teacherId = teacherId
                    ))
                }

                // Update UI
                if (scheduleList.isNotEmpty()) {
                    recyclerView.visibility = View.VISIBLE
                    emptyState.visibility = View.GONE
                    
                    // Format header text
                    val totalText = if (scheduleList.size == 1) "1 class" else "${scheduleList.size} classes"
                    textTotalClasses.text = totalText
                    
                    val todayText = when (todayClassesCount) {
                        0 -> "No classes today"
                        1 -> "1 class today"
                        else -> "$todayClassesCount classes today"
                    }
                    textTodayClasses.text = todayText
                } else {
                    showEmptyState("No classes scheduled for your section")
                }

                adapter.notifyDataSetChanged()
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                showEmptyState("Error loading schedules: ${e.message}")
            }
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            showEmptyState("Error: ${e.message}")
        }
    }

    private fun showEmptyState(message: String) {
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        
        textTotalClasses.text = "0"
        textTodayClasses.text = "0"
        
        // Update empty state message if needed
        val emptyMessage = emptyState.findViewById<TextView>(R.id.emptyMessage)
        emptyMessage?.text = message
    }

    private fun getCurrentDayOfWeek(): String {
        val calendar = Calendar.getInstance()
        val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
        return dayFormat.format(calendar.time)
    }

    private fun convertTo12HourFormat(startTime: String, endTime: String): String {
        return try {
            val inputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val outputFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            
            val startDate = inputFormat.parse(startTime)
            val endDate = inputFormat.parse(endTime)
            
            val formattedStart = outputFormat.format(startDate ?: Date())
            val formattedEnd = outputFormat.format(endDate ?: Date())
            
            "$formattedStart - $formattedEnd"
        } catch (e: Exception) {
            "$startTime - $endTime"
        }
    }
}

