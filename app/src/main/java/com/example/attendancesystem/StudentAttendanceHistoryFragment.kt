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
import com.google.firebase.firestore.ListenerRegistration
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
    private var attendanceListener: ListenerRegistration? = null
    private var archivedListener: ListenerRegistration? = null

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

    override fun onDestroyView() {
        super.onDestroyView()
        attendanceListener?.remove()
        attendanceListener = null
        archivedListener?.remove()
        archivedListener = null
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
            val tempActiveList = mutableListOf<AttendanceHistoryItem>()
            val tempArchivedList = mutableListOf<AttendanceHistoryItem>()
            
            attendanceListener?.remove()
            attendanceListener = db.collection("attendance")
                .whereEqualTo("userId", user.uid)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        android.util.Log.e("StudentAttendanceHistory", "Error loading active attendance: ${e.message}", e)
                    } else {
                        tempActiveList.clear()
                        snapshot?.documents?.forEach { document ->
                            val timestamp = document.getTimestamp("timestamp")
                            val subject = document.getString("subject") ?: ""
                            val statusString = document.getString("status") ?: "ABSENT"
                            
                            timestamp?.let {
                                tempActiveList.add(AttendanceHistoryItem(
                                    date = formatDate(it),
                                    subject = subject,
                                    status = statusString
                                ))
                            }
                        }
                    }
                    updateCombinedList(tempActiveList, tempArchivedList)
                }
            
            archivedListener?.remove()
            archivedListener = db.collection("archived_attendance")
                .whereEqualTo("userId", user.uid)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        android.util.Log.e("StudentAttendanceHistory", "Error loading archived attendance: ${e.message}", e)
                    } else {
                        tempArchivedList.clear()
                        snapshot?.documents?.forEach { document ->
                            val timestamp = document.getTimestamp("timestamp")
                            val subject = document.getString("subject") ?: ""
                            val statusString = document.getString("status") ?: "ABSENT"
                            
                            timestamp?.let {
                                tempArchivedList.add(AttendanceHistoryItem(
                                    date = formatDate(it),
                                    subject = subject,
                                    status = statusString
                                ))
                            }
                        }
                    }
                    updateCombinedList(tempActiveList, tempArchivedList)
                }
        }
    }
    
    private fun updateCombinedList(activeList: List<AttendanceHistoryItem>, archivedList: List<AttendanceHistoryItem>) {
        attendanceList.clear()
        attendanceList.addAll(activeList)
        attendanceList.addAll(archivedList)
        
        attendanceList.sortByDescending { it.date }
        
        var totalClasses = attendanceList.size
        var attendedClasses = attendanceList.count { it.status == "PRESENT" }

        val percentage = if (totalClasses > 0) {
            (attendedClasses.toDouble() / totalClasses.toDouble() * 100)
        } else {
            0.0
        }

        if (totalClasses > 0) {
            attendancePercentage.text = "Attendance: ${String.format("%.1f", percentage)}%"
            recyclerView.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            
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
        
        android.util.Log.d("StudentAttendanceHistory", "Loaded ${attendanceList.size} total records (${activeList.size} active + ${archivedList.size} archived)")
    }

    private fun formatDate(timestamp: Timestamp): String {
        val date = timestamp.toDate()
        val format = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return format.format(date)
    }
}
