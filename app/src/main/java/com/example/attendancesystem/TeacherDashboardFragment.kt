package com.example.attendancesystem

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.attendancesystem.utils.ProfilePictureManager
import com.example.attendancesystem.models.TeacherAttendanceItem
import com.example.attendancesystem.models.StudentItem
import com.example.attendancesystem.adapters.TeacherAttendanceAdapter
import com.example.attendancesystem.adapters.StudentSelectionAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.app.AlertDialog
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.RadioButton
import com.google.firebase.firestore.ListenerRegistration
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class TeacherDashboardFragment : Fragment() {
    companion object {
        private const val RECENT_ATTENDANCE_LIMIT = 100
        private const val TAG = "TeacherDashboard"
    }
    private lateinit var imageProfilePic: ImageView
    private lateinit var textInitials: TextView
    private lateinit var textName: TextView
    private lateinit var textDepartment: TextView
    private lateinit var textQrExpiry: TextView
    private lateinit var buttonShowQr: Button
    private lateinit var buttonRenewQr: Button
    private lateinit var buttonRefreshAttendance: Button
    private lateinit var buttonManualAdd: Button
    private lateinit var buttonAnalytics: Button
    private lateinit var buttonEndClass: Button
    private lateinit var attendanceRecyclerView: RecyclerView
    private lateinit var textAttendanceCount: TextView
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var attendanceListener: ListenerRegistration? = null
    private var listeningScheduleId: String? = null
    private var listeningSubject: String? = null
    private var currentScheduleEndTime: String? = null
    private val classEndHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var classEndRunnable: Runnable? = null
    private var attendanceAdapter: TeacherAttendanceAdapter? = null
    private val attendanceList = mutableListOf<TeacherAttendanceItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_teacher_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupClickListeners()
        loadUserData()
        loadRecentAttendance()
    }
    
    private fun initializeViews(view: View) {
        imageProfilePic = view.findViewById(R.id.imageProfilePic)
        textInitials = view.findViewById(R.id.textInitials)
        textName = view.findViewById(R.id.textName)
        textDepartment = view.findViewById(R.id.textDepartment)
        textQrExpiry = view.findViewById(R.id.textQrExpiry)
        buttonShowQr = view.findViewById(R.id.buttonShowQr)
        buttonRenewQr = view.findViewById(R.id.buttonRenewQr)
        buttonRefreshAttendance = view.findViewById(R.id.buttonRefreshAttendance)
        buttonManualAdd = view.findViewById(R.id.buttonManualAdd)
        buttonAnalytics = view.findViewById(R.id.buttonAnalytics)
        buttonEndClass = view.findViewById(R.id.buttonEndClass)
        attendanceRecyclerView = view.findViewById(R.id.attendanceRecyclerView)
        textAttendanceCount = view.findViewById(R.id.textAttendanceCount)
        
        // Setup RecyclerView with adapter
        attendanceRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        attendanceAdapter = TeacherAttendanceAdapter(attendanceList) { item ->
            confirmAndRemoveAttendance(item)
        }
        attendanceRecyclerView.adapter = attendanceAdapter
    }
    
    private fun setupClickListeners() {
        buttonShowQr.setOnClickListener {
            // Show QR: Open QR activity to show existing QR (or create if none exists)
            showQRCode(forceNew = false)
        }
        
        buttonRenewQr.setOnClickListener {
            // Renew QR: Force delete old sessions and create fresh QR code
            showQRCode(forceNew = true)
        }
        
        buttonRefreshAttendance.setOnClickListener {
            // Force remove existing listener and reload fresh
            attendanceListener?.remove()
            attendanceListener = null
            listeningScheduleId = null
            listeningSubject = null
            loadRecentAttendance()
        }
        
        buttonManualAdd.setOnClickListener {
            showManualAddDialog()
        }
        
        buttonAnalytics.setOnClickListener {
            val intent = Intent(requireContext(), TeacherMainActivity::class.java)
            intent.putExtra("open", "analytics")
            startActivity(intent)
        }
        
        buttonEndClass.setOnClickListener {
            confirmEndClass()
        }
    }
    
    private fun loadUserData() {
        val currentUser = auth.currentUser
        currentUser?.let {
            db.collection("users").document(it.uid)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null && snapshot.exists()) {
                        val name = snapshot.getString("name") ?: "Teacher"
                        val department = snapshot.getString("department") ?: "Department"
                        textName.text = name
                        textDepartment.text = department
                        ProfilePictureManager.getInstance().loadProfilePicture(requireContext(), imageProfilePic, textInitials, name, "TC")
                    }
                }
        }
    }
    
    private fun showQRCode(forceNew: Boolean = false) {
        val currentUser = auth.currentUser ?: run {
            Toast.makeText(requireContext(), "Not signed in", Toast.LENGTH_SHORT).show(); return
        }

        val currentDay = getCurrentDayOfWeek()
        val nowMinutes = getCurrentTimeInMinutes()

        db.collection("schedules")
            .whereEqualTo("teacherId", currentUser.uid)
            .whereEqualTo("day", currentDay)
            .get()
            .addOnSuccessListener { scheduleDocs ->
                val todays = scheduleDocs.documents
                val match = todays.firstOrNull { doc ->
                    val start = doc.getString("startTime") ?: ""
                    val end = doc.getString("endTime") ?: ""
                    isNowWithinRange(nowMinutes, start, end)
                }

                fun launchFor(doc: com.google.firebase.firestore.DocumentSnapshot) {
                    val intent = android.content.Intent(requireContext(), QRActivity::class.java).apply {
                        putExtra("scheduleId", doc.id)
                        putExtra("subject", doc.getString("subject") ?: "Attendance")
                        putExtra("section", doc.getString("section") ?: "")
                        putExtra("forceNew", forceNew) // Pass flag to force new QR
                    }
                    startActivity(intent)
                }

                if (match != null) {
                    launchFor(match)
                } else {
                    // No current class — find next upcoming today and inform the teacher
                    val next = todays
                        .mapNotNull { doc ->
                            val start = doc.getString("startTime") ?: return@mapNotNull null
                            val startMin = parseTimeToMinutes24(start) ?: return@mapNotNull null
                            Pair(startMin, doc)
                        }
                        .filter { it.first > nowMinutes }
                        .minByOrNull { it.first }
                        ?.second

                    if (next != null) {
                        val subj = next.getString("subject") ?: ""
                        val sec = next.getString("section") ?: ""
                        val start = next.getString("startTime") ?: ""
                        Toast.makeText(requireContext(), "No current class. Next: $subj ($sec) at $start", Toast.LENGTH_LONG).show()
                    } else if (todays.isNotEmpty()) {
                        Toast.makeText(requireContext(), "No more classes today", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), "No schedules found for today", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error loading schedule: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun renewQRCode() {
        // Simply re-open QRActivity to generate a fresh QR for the current class
        showQRCode()
    }
    
    private fun loadRecentAttendance() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val currentDay = getCurrentDayOfWeek()
            val nowMinutes = getCurrentTimeInMinutes()
            // Find today's schedules and determine the current class with minute precision
            db.collection("schedules")
                .whereEqualTo("teacherId", currentUser.uid)
                .whereEqualTo("day", currentDay)
                .get()
                .addOnSuccessListener { scheduleDocs ->
                    if (scheduleDocs.isEmpty()) {
                        // No schedule for today — show recent attendance fallback
                        textQrExpiry.text = "No classes scheduled for today"
                        loadRecentAttendanceFallback()
                        return@addOnSuccessListener
                    }
                    
                    // Find the current class based on precise minutes (supports crossing midnight)
                    val currentSchedule = scheduleDocs.documents.firstOrNull { doc ->
                        val startTime = doc.getString("startTime") ?: ""
                        val endTime = doc.getString("endTime") ?: ""
                        isNowWithinRange(nowMinutes, startTime, endTime)
                    }
                    
                    if (currentSchedule != null) {
                        val scheduleId = currentSchedule.id
                        val subject = currentSchedule.getString("subject") ?: ""
                        val section = currentSchedule.getString("section") ?: ""
                        
                        // Update QR status
                        textQrExpiry.text = "Current Class: $subject ($section)"
                        // Start realtime listener for this class
                        startAttendanceListener(scheduleId, subject)
                    } else {
                        // No current class — archive any old attendance from ended classes today
                        val endedSchedules = scheduleDocs.documents
                            .filter { doc ->
                                val endTime = doc.getString("endTime") ?: return@filter false
                                val endMin = parseTimeToMinutes24(endTime) ?: return@filter false
                                // Check if class has ended
                                if (endMin < parseTimeToMinutes24(doc.getString("startTime") ?: "00:00") ?: 0) {
                                    // Class crosses midnight - check if we're past the end time in the new day
                                    nowMinutes > endMin && nowMinutes < 720 // Before noon means new day
                                } else {
                                    nowMinutes > endMin
                                }
                            }
                        
                        // Archive attendance from all ended classes today
                        if (endedSchedules.isNotEmpty()) {
                            archiveEndedClassesAttendance(endedSchedules)
                        }
                        
                        // Show next class info
                        val nextSchedule = scheduleDocs.documents
                            .mapNotNull { doc ->
                                val startTime = doc.getString("startTime") ?: return@mapNotNull null
                                val startMin = parseTimeToMinutes24(startTime) ?: return@mapNotNull null
                                Pair(startMin, doc)
                            }
                            .filter { it.first > nowMinutes }
                            .minByOrNull { it.first }
                            ?.second
                        
                        if (nextSchedule != null) {
                            val subject = nextSchedule.getString("subject") ?: ""
                            val section = nextSchedule.getString("section") ?: ""
                            val startTime = nextSchedule.getString("startTime") ?: ""
                            textQrExpiry.text = "Next Class: $subject ($section) at $startTime"
                        } else {
                            textQrExpiry.text = "No classes scheduled for today"
                        }
                        
                        // Stop any existing listener since no current class
                        attendanceListener?.remove()
                        attendanceListener = null
                        listeningScheduleId = null
                        listeningSubject = null
                        buttonEndClass.visibility = View.GONE
                        loadRecentAttendanceFallback()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Error loading schedule: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun startAttendanceListener(scheduleId: String, subject: String) {
        val currentUser = auth.currentUser ?: return
        if (listeningScheduleId == scheduleId && listeningSubject == subject && attendanceListener != null) {
            return
        }
        attendanceListener?.remove()
        listeningScheduleId = scheduleId
        listeningSubject = subject
        
        // Get the class start and end time for filtering and auto-archiving
        db.collection("schedules").document(scheduleId)
            .get()
            .addOnSuccessListener { scheduleDoc ->
                val startTime = scheduleDoc.getString("startTime") ?: "00:00"
                val endTime = scheduleDoc.getString("endTime") ?: "23:59"
                
                // Store end time for scheduling
                currentScheduleEndTime = endTime
                
                // Show the "End Class" button for current class
                buttonEndClass.visibility = View.VISIBLE
                
                // Schedule auto-archive when class ends
                scheduleAutoArchiveAtClassEnd(endTime)
                
                // Calculate today's class start timestamp
                val calendar = java.util.Calendar.getInstance()
                val (startHour, startMin) = parseTime24(startTime)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, startHour)
                calendar.set(java.util.Calendar.MINUTE, startMin)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                val classStartTimestamp = com.google.firebase.Timestamp(calendar.time)
                
                // Listen for attendance from this class session only (after class start time)
                // Exclude archived records by only querying the attendance collection (not archived_attendance)
                android.util.Log.d(TAG, "=== TEACHER QUERYING ATTENDANCE ===")
                android.util.Log.d(TAG, "teacherId: ${currentUser.uid}")
                android.util.Log.d(TAG, "scheduleId: $scheduleId")
                android.util.Log.d(TAG, "subject: $subject")
                android.util.Log.d(TAG, "timestamp >= $classStartTimestamp")
                attendanceListener = db.collection("attendance")
                    .whereEqualTo("teacherId", currentUser.uid)
                    .whereEqualTo("scheduleId", scheduleId)
                    .whereEqualTo("subject", subject)
                    .whereGreaterThanOrEqualTo("timestamp", classStartTimestamp)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            android.util.Log.e(TAG, "Attendance listen failed: ${e.message}", e)
                            Toast.makeText(requireContext(), "Attendance listen failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            return@addSnapshotListener
                        }
                        
                        // Sort by timestamp in memory to avoid composite index requirement
                        val newAttendanceList = snapshot?.documents
                            ?.sortedByDescending { doc -> 
                                doc.getTimestamp("timestamp")?.seconds ?: 0L 
                            }
                            ?.map { doc ->
                                TeacherAttendanceItem(
                                    documentId = doc.id,
                                    studentName = doc.getString("studentName") ?: "Unknown Student",
                                    timeTaken = formatTimestamp(doc.getTimestamp("timestamp")),
                                    section = doc.getString("section") ?: "",
                                    status = doc.getString("status") ?: "PRESENT"
                                )
                            } ?: emptyList()
                        
                        // Update the adapter's data and notify changes
                        val oldSize = attendanceList.size
                        attendanceList.clear()
                        attendanceList.addAll(newAttendanceList)
                        attendanceAdapter?.notifyDataSetChanged()
                        
                        // Scroll to top if new items were added
                        if (newAttendanceList.size > oldSize) {
                            attendanceRecyclerView.smoothScrollToPosition(0)
                        }
                        
                        textAttendanceCount.text = "Total: ${attendanceList.size}"
                        
                        android.util.Log.d(TAG, "✓✓✓ TEACHER LOADED ${attendanceList.size} ATTENDANCE RECORDS (LIVE UPDATE) ✓✓✓")
                        android.util.Log.d(TAG, "Subject: $subject, from ${formatTimestamp(classStartTimestamp)}")
                        attendanceList.forEach { 
                            android.util.Log.d(TAG, "  → ${it.studentName} at ${it.timeTaken}")
                        }
                    }
            }
            .addOnFailureListener { e ->
                android.util.Log.e(TAG, "Error getting schedule: ${e.message}", e)
            }
    }
    
    private fun parseTime24(time: String): Pair<Int, Int> {
        return try {
            val parts = time.split(":")
            Pair(parts[0].toInt(), parts[1].toInt())
        } catch (e: Exception) {
            Pair(0, 0)
        }
    }

    private fun loadRecentAttendanceFallback() {
        // Load recent attendance with REAL-TIME updates - shows both active and archived
        val uid = auth.currentUser?.uid ?: return
        
        // Remove any existing listener first
        attendanceListener?.remove()
        listeningScheduleId = null
        listeningSubject = null
        
        android.util.Log.d(TAG, "Loading fallback with REAL-TIME listener for active/archived attendance")
        
        // Get today's start for filtering archived records
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val todayMillis = calendar.timeInMillis
        
        // Set up REAL-TIME listener on active attendance (NO LIMIT to avoid index requirement)
        attendanceListener = db.collection("attendance")
            .whereEqualTo("teacherId", uid)
            .addSnapshotListener { activeSnapshot, e ->
                if (e != null) {
                    android.util.Log.e(TAG, "Attendance fallback listen failed: ${e.message}", e)
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                
                // Also check archived attendance (NO date filter to avoid composite index requirement)
                db.collection("archived_attendance")
                    .whereEqualTo("teacherId", uid)
                    .get()
                    .addOnSuccessListener { archivedSnapshot ->
                        // Filter archived records to only today (in memory)
                        val archivedFromToday = archivedSnapshot.documents.filter {
                            val archivedAt = it.getLong("archivedAt") ?: 0L
                            archivedAt >= todayMillis
                        }
                        
                        // Combine both active and archived
                        val allDocs = (activeSnapshot?.documents ?: emptyList()) + archivedFromToday
                        
                        // Sort by timestamp and limit in memory
                        val sortedDocs = allDocs
                            .sortedByDescending { it.getTimestamp("timestamp")?.seconds ?: 0 }
                            .take(RECENT_ATTENDANCE_LIMIT)
                        
                        val newAttendanceList = sortedDocs.map { doc ->
                            TeacherAttendanceItem(
                                documentId = doc.id,
                                studentName = doc.getString("studentName") ?: "Unknown Student",
                                timeTaken = formatTimestamp(doc.getTimestamp("timestamp")),
                                section = doc.getString("section") ?: "",
                                status = doc.getString("status") ?: "PRESENT"
                            )
                        }
                        
                        // Update the adapter's data and notify changes
                        val oldSize = attendanceList.size
                        attendanceList.clear()
                        attendanceList.addAll(newAttendanceList)
                        
                        // Update the adapter's click handler to check archived status
                        attendanceAdapter = TeacherAttendanceAdapter(attendanceList) { item ->
                            // Check if it's archived
                            if (archivedFromToday.any { it.id == item.documentId }) {
                                Toast.makeText(requireContext(), "Cannot modify archived attendance", Toast.LENGTH_SHORT).show()
                            } else {
                                confirmAndRemoveAttendance(item)
                            }
                        }
                        attendanceRecyclerView.adapter = attendanceAdapter
                        
                        // Scroll to top if new items were added
                        if (newAttendanceList.size > oldSize) {
                            attendanceRecyclerView.smoothScrollToPosition(0)
                        }
                        
                        val activeCount = activeSnapshot?.documents?.size ?: 0
                        val archivedCount = archivedFromToday.size
                        val label = if (archivedCount > 0) " ($activeCount active, $archivedCount archived)" else ""
                        textAttendanceCount.text = "Total: ${attendanceList.size}$label"
                        
                        android.util.Log.d(TAG, "Loaded ${attendanceList.size} attendance records ($activeCount active, $archivedCount archived) with REAL-TIME updates")
                    }
                    .addOnFailureListener { archiveError ->
                        android.util.Log.e(TAG, "Error loading archived attendance: ${archiveError.message}", archiveError)
                        // Just show active attendance if archived query fails
                        val sortedDocs = (activeSnapshot?.documents ?: emptyList())
                            .sortedByDescending { it.getTimestamp("timestamp")?.seconds ?: 0 }
                            .take(RECENT_ATTENDANCE_LIMIT)
                        
                        val newAttendanceList = sortedDocs.map { doc ->
                            TeacherAttendanceItem(
                                documentId = doc.id,
                                studentName = doc.getString("studentName") ?: "Unknown Student",
                                timeTaken = formatTimestamp(doc.getTimestamp("timestamp")),
                                section = doc.getString("section") ?: "",
                                status = doc.getString("status") ?: "PRESENT"
                            )
                        }
                        
                        // Update the adapter's data
                        attendanceList.clear()
                        attendanceList.addAll(newAttendanceList)
                        
                        // Recreate adapter for consistency
                        attendanceAdapter = TeacherAttendanceAdapter(attendanceList) { item ->
                            confirmAndRemoveAttendance(item)
                        }
                        attendanceRecyclerView.adapter = attendanceAdapter
                        textAttendanceCount.text = "Total: ${attendanceList.size}"
                    }
            }
    }

    private fun confirmAndRemoveAttendance(item: TeacherAttendanceItem) {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove attendance")
            .setMessage("Remove ${item.studentName}'s attendance?")
            .setPositiveButton("Remove") { _, _ ->
                val db = FirebaseFirestore.getInstance()
                val attendanceId = item.documentId
                
                // First, get the attendance document to archive it
                db.collection("attendance")
                    .document(attendanceId)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            // Archive the document for historical analytics
                            val archiveData = document.data?.toMutableMap() ?: mutableMapOf()
                            archiveData["archivedAt"] = System.currentTimeMillis()
                            archiveData["originalId"] = attendanceId
                            
                            db.collection("archived_attendance")
                                .document(attendanceId)
                                .set(archiveData)
                                .addOnSuccessListener {
                                    // Now delete from the active collection
                                    db.collection("attendance")
                                        .document(attendanceId)
                                        .delete()
                                        .addOnSuccessListener {
                                            Toast.makeText(requireContext(), "Removed ${item.studentName}", Toast.LENGTH_SHORT).show()
                                        }
                                        .addOnFailureListener { ex ->
                                            Toast.makeText(requireContext(), "Failed: ${ex.message}", Toast.LENGTH_SHORT).show()
                                        }
                                }
                                .addOnFailureListener { ex ->
                                    // Even if archiving fails, still try to delete
                                    db.collection("attendance")
                                        .document(attendanceId)
                                        .delete()
                                        .addOnSuccessListener {
                                            Toast.makeText(requireContext(), "Removed ${item.studentName}", Toast.LENGTH_SHORT).show()
                                        }
                                }
                        }
                    }
                    .addOnFailureListener { ex ->
                        Toast.makeText(requireContext(), "Failed: ${ex.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun getCurrentDayOfWeek(): String {
        val calendar = java.util.Calendar.getInstance()
        val dayFormat = java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault())
        return dayFormat.format(calendar.time)
    }
    
    private fun parseTimeToHour(timeString: String): Int {
        return try {
            val parts = timeString.split(":")
            parts[0].toInt()
        } catch (e: Exception) {
            0
        }
    }
    
    private fun formatTimestamp(timestamp: com.google.firebase.Timestamp?): String {
        return timestamp?.toDate()?.let { date ->
            val format = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            format.format(date)
        } ?: ""
    }
    
    private fun showManualAddDialog() {
        val currentUser = auth.currentUser ?: return
        val nowMinutes = getCurrentTimeInMinutes()
        val currentDay = getCurrentDayOfWeek()
        
        db.collection("schedules")
            .whereEqualTo("teacherId", currentUser.uid)
            .whereEqualTo("day", currentDay)
            .get()
            .addOnSuccessListener { scheduleDocs ->
                val todays = scheduleDocs.documents
                val currentSchedule = todays.firstOrNull { doc ->
                    val start = doc.getString("startTime") ?: ""
                    val end = doc.getString("endTime") ?: ""
                    isNowWithinRange(nowMinutes, start, end)
                }

                fun proceedWith(doc: com.google.firebase.firestore.DocumentSnapshot) {
                    val subject = doc.getString("subject") ?: ""
                    val section = doc.getString("section") ?: ""
                    loadStudentsForSection(section, subject, doc.id)
                }

                if (currentSchedule != null) {
                    proceedWith(currentSchedule)
                } else if (todays.isNotEmpty()) {
                    showSchedulePicker(todays, title = "Select class for manual add") { chosen ->
                        proceedWith(chosen)
                    }
                } else {
                    Toast.makeText(requireContext(), "No schedules found for today", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error loading schedule: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getCurrentTimeInMinutes(): Int {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    }

    private fun parseTimeToMinutes24(time: String): Int? {
        return try {
            val parts = time.split(":")
            val hour = parts.getOrNull(0)?.toInt() ?: return null
            val minute = parts.getOrNull(1)?.toInt() ?: 0
            hour * 60 + minute
        } catch (_: Exception) { null }
    }

    private fun isNowWithinRange(nowMinutes: Int, start: String, end: String): Boolean {
        val s = parseTimeToMinutes24(start) ?: return false
        val e = parseTimeToMinutes24(end) ?: return false
        return if (e < s) {
            nowMinutes >= s || nowMinutes <= e
        } else {
            nowMinutes in s until e
        }
    }

    private fun showSchedulePicker(
        schedules: List<com.google.firebase.firestore.DocumentSnapshot>,
        title: String,
        onChosen: (com.google.firebase.firestore.DocumentSnapshot) -> Unit
    ) {
        val items = schedules.map { doc ->
            val subject = doc.getString("subject") ?: ""
            val section = doc.getString("section") ?: ""
            val start = doc.getString("startTime") ?: ""
            val end = doc.getString("endTime") ?: ""
            "$subject ($section)  $start-$end"
        }.toTypedArray()

        android.app.AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setItems(items) { dialog, which ->
                onChosen(schedules[which])
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun loadStudentsForSection(section: String, subject: String, scheduleId: String) {
        val target = section.trim().lowercase()
        db.collection("users")
            .whereEqualTo("role", "student")
            .get()
            .addOnSuccessListener { studentDocs ->
                val students = studentDocs
                    .filter { (it.getString("role") ?: "") == "student" }
                    .mapNotNull { doc ->
                        val studentSection = (doc.getString("section") ?: "").trim().lowercase()
                        if (studentSection == target) {
                            StudentItem(
                                id = doc.id,
                                name = doc.getString("name") ?: "Unknown Student",
                                email = doc.getString("email") ?: "",
                                section = doc.getString("section") ?: ""
                            )
                        } else null
                    }
                
                if (students.isNotEmpty()) {
                    showStudentSelectionDialog(students, subject, scheduleId)
                } else {
                    Toast.makeText(requireContext(), "No students found in section $section", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error loading students: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun showStudentSelectionDialog(students: List<StudentItem>, subject: String, scheduleId: String) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_manual_add_student, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        
        val textCurrentSession = dialogView.findViewById<TextView>(R.id.textCurrentSession)
        val radioPresent = dialogView.findViewById<RadioButton>(R.id.radioPresent)
        val radioExcused = dialogView.findViewById<RadioButton>(R.id.radioExcused)
        val radioCutting = dialogView.findViewById<RadioButton>(R.id.radioCutting)

        // RadioGroup is defined in XML now; nothing needed here
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnAddStudent = dialogView.findViewById<Button>(R.id.btnAddStudent)
        
        textCurrentSession.text = "Current Session: $subject"
        
        // Setup autocomplete for student names
        val autoComplete = dialogView.findViewById<AutoCompleteTextView>(R.id.editStudentName)
        val studentNames = students.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, studentNames)
        autoComplete.setAdapter(adapter)
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        btnAddStudent.setOnClickListener {
            val studentName = dialogView.findViewById<AutoCompleteTextView>(R.id.editStudentName).text.toString().trim()
            
            if (studentName.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a student name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Get selected status
            val status = when {
                radioExcused.isChecked -> "EXCUSED"
                radioCutting.isChecked -> "CUTTING"
                else -> "PRESENT"
            }
            
            // Find the student in our list
            val selectedStudent = students.find { it.name.equals(studentName, ignoreCase = true) }
            if (selectedStudent != null) {
                addStudentsManually(listOf(selectedStudent), subject, scheduleId, status)
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Student '$studentName' not found in current section", Toast.LENGTH_SHORT).show()
            }
        }
        
        dialog.show()
    }
    
    private fun addStudentsManually(students: List<StudentItem>, subject: String, scheduleId: String, status: String) {
        val currentUser = auth.currentUser ?: return
        val currentTime = com.google.firebase.Timestamp.now()
        
        android.util.Log.d(TAG, "Manually adding ${students.size} students to attendance - Subject: $subject, Status: $status")
        
        students.forEach { student ->
            val attendanceData = hashMapOf(
                "userId" to student.id,
                "studentName" to student.name,
                "sessionId" to "MANUAL_${System.currentTimeMillis()}", // Unique session ID for manual entries
                "teacherId" to currentUser.uid,
                "scheduleId" to scheduleId,
                "subject" to subject,
                "section" to student.section,
                "timestamp" to currentTime,
                "status" to status,
                "location" to "",
                "notes" to "Manually added by teacher - Status: $status",
                "isManualEntry" to true // Flag to identify manual entries
            )
            
            android.util.Log.d(TAG, "Adding manual attendance: ${student.name} - $subject (${student.section}) - $status")
            
            db.collection("attendance").add(attendanceData)
                .addOnSuccessListener { docRef ->
                    android.util.Log.d(TAG, "Successfully added manual attendance for ${student.name} with ID: ${docRef.id}")
                    Toast.makeText(requireContext(), "Added ${student.name} to attendance", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    android.util.Log.e(TAG, "Failed to add manual attendance for ${student.name}: ${e.message}", e)
                    Toast.makeText(requireContext(), "Failed to add ${student.name}: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
        // Ensure we are listening to updates for this schedule so the UI reflects changes instantly
        startAttendanceListener(scheduleId, subject)
    }

    private fun confirmEndClass() {
        AlertDialog.Builder(requireContext())
            .setTitle("End Class")
            .setMessage("This will archive all attendance from this class session. The attendance will be removed from Recent Attendance but kept for analytics. Continue?")
            .setPositiveButton("End Class") { _, _ ->
                archiveCurrentClassAttendance()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun archiveCurrentClassAttendance() {
        val scheduleId = listeningScheduleId ?: return
        val subject = listeningSubject ?: return
        val currentUser = auth.currentUser ?: return
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Get all attendance records for the current class session
                val attendanceSnapshot = db.collection("attendance")
                    .whereEqualTo("teacherId", currentUser.uid)
                    .whereEqualTo("scheduleId", scheduleId)
                    .whereEqualTo("subject", subject)
                    .get()
                    .await()
                
                android.util.Log.d(TAG, "Archiving ${attendanceSnapshot.size()} attendance records for $subject")
                
                var archivedCount = 0
                // Archive each record
                for (doc in attendanceSnapshot.documents) {
                    val archiveData = doc.data?.toMutableMap() ?: continue
                    archiveData["archivedAt"] = System.currentTimeMillis()
                    archiveData["originalId"] = doc.id
                    
                    // Add to archived collection
                    db.collection("archived_attendance")
                        .document(doc.id)
                        .set(archiveData)
                        .await()
                    
                    // Delete from active collection
                    doc.reference.delete().await()
                    archivedCount++
                }
                
                // Hide the End Class button
                buttonEndClass.visibility = View.GONE
                
                // Clear the current listening state
                attendanceListener?.remove()
                listeningScheduleId = null
                listeningSubject = null
                
                // Reload to show empty/next class
                loadRecentAttendance()
                
                Toast.makeText(requireContext(), "Class ended. $archivedCount records archived.", Toast.LENGTH_SHORT).show()
                android.util.Log.d(TAG, "Successfully archived $archivedCount attendance records")
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error archiving attendance: ${e.message}", e)
                Toast.makeText(requireContext(), "Error archiving attendance: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun archiveEndedClassesAttendance(endedSchedules: List<com.google.firebase.firestore.DocumentSnapshot>) {
        val currentUser = auth.currentUser ?: return
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                var totalArchived = 0
                
                for (schedule in endedSchedules) {
                    val scheduleId = schedule.id
                    val subject = schedule.getString("subject") ?: continue
                    
                    // Get attendance records for this ended class from today
                    val calendar = java.util.Calendar.getInstance()
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    calendar.set(java.util.Calendar.MINUTE, 0)
                    calendar.set(java.util.Calendar.SECOND, 0)
                    calendar.set(java.util.Calendar.MILLISECOND, 0)
                    val todayStart = com.google.firebase.Timestamp(calendar.time)
                    
                    val attendanceSnapshot = db.collection("attendance")
                        .whereEqualTo("teacherId", currentUser.uid)
                        .whereEqualTo("scheduleId", scheduleId)
                        .whereEqualTo("subject", subject)
                        .whereGreaterThanOrEqualTo("timestamp", todayStart)
                        .get()
                        .await()
                    
                    // Archive each record
                    for (doc in attendanceSnapshot.documents) {
                        val archiveData = doc.data?.toMutableMap() ?: continue
                        archiveData["archivedAt"] = System.currentTimeMillis()
                        archiveData["originalId"] = doc.id
                        archiveData["autoArchived"] = true
                        
                        // Add to archived collection
                        db.collection("archived_attendance")
                            .document(doc.id)
                            .set(archiveData)
                            .await()
                        
                        // Delete from active collection
                        doc.reference.delete().await()
                        totalArchived++
                    }
                    
                    android.util.Log.d(TAG, "Auto-archived ${attendanceSnapshot.size()} records from ended class: $subject")
                }
                
                if (totalArchived > 0) {
                    android.util.Log.d(TAG, "Auto-archived total of $totalArchived attendance records from ${endedSchedules.size} ended classes")
                }
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error auto-archiving ended classes: ${e.message}", e)
            }
        }
    }
    
    private fun scheduleAutoArchiveAtClassEnd(endTime: String) {
        // Cancel any existing scheduled archiving
        classEndRunnable?.let { classEndHandler.removeCallbacks(it) }
        
        try {
            // Calculate milliseconds until class ends
            val (endHour, endMin) = parseTime24(endTime)
            val now = java.util.Calendar.getInstance()
            val classEnd = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, endHour)
                set(java.util.Calendar.MINUTE, endMin)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            
            val millisUntilEnd = classEnd.timeInMillis - now.timeInMillis
            
            if (millisUntilEnd > 0) {
                classEndRunnable = Runnable {
                    android.util.Log.d(TAG, "Class end time reached - auto-archiving attendance")
                    archiveCurrentClassAttendance()
                }
                classEndHandler.postDelayed(classEndRunnable!!, millisUntilEnd)
                android.util.Log.d(TAG, "Scheduled auto-archive in ${millisUntilEnd / 1000 / 60} minutes at $endTime")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error scheduling auto-archive: ${e.message}", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        attendanceListener?.remove()
        attendanceListener = null
        listeningScheduleId = null
        listeningSubject = null
        attendanceAdapter = null
        attendanceList.clear()
        classEndRunnable?.let { classEndHandler.removeCallbacks(it) }
    }
}


