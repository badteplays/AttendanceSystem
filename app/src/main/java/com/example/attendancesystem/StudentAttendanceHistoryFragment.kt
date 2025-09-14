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
import com.example.attendancesystem.models.AttendanceHistoryItem
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class StudentAttendanceHistoryFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var attendancePercentage: TextView
    private lateinit var warningText: TextView
    private lateinit var emptyState: LinearLayout
    private lateinit var adapter: AttendanceHistoryAdapter
    private val attendanceList = mutableListOf<AttendanceHistoryItem>()
    private val db = FirebaseFirestore.getInstance()
    private val currentUser = FirebaseAuth.getInstance().currentUser

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_student_attendance_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeViews(view)
        setupRecyclerView()
        loadAttendanceHistory()
    }

    private fun initializeViews(view: View) {
        recyclerView = view.findViewById(R.id.attendanceHistoryRecyclerView)
        progressBar = view.findViewById(R.id.progressBar)
        attendancePercentage = view.findViewById(R.id.attendancePercentage)
        warningText = view.findViewById(R.id.warningText)
        emptyState = view.findViewById(R.id.emptyState)
    }

    private fun setupRecyclerView() {
        adapter = AttendanceHistoryAdapter(attendanceList)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
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

                    // Update UI based on data
                    if (totalClasses > 0) {
                        attendancePercentage.text = "Attendance: ${String.format("%.1f", percentage)}%"
                        recyclerView.visibility = View.VISIBLE
                        emptyState.visibility = View.GONE
                        
                        // Show warning if attendance is below 75%
                        if (percentage < 75) {
                            warningText.visibility = View.VISIBLE
                            warningText.text = "Warning: Your attendance is below 75%"
                        } else {
                            warningText.visibility = View.GONE
                        }
                    } else {
                        attendancePercentage.text = "No attendance data yet"
                        recyclerView.visibility = View.GONE
                        emptyState.visibility = View.VISIBLE
                        warningText.visibility = View.GONE
                    }

                    adapter.notifyDataSetChanged()
                    progressBar.visibility = View.GONE
                }
                .addOnFailureListener { e ->
                    // Handle error
                    progressBar.visibility = View.GONE
                    recyclerView.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                    attendancePercentage.text = "No attendance data yet"
                }
        }
    }

    private fun formatDate(timestamp: Timestamp): String {
        val date = timestamp.toDate()
        val format = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return format.format(date)
    }
}
