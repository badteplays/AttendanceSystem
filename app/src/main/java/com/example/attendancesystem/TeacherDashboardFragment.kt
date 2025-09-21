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

class TeacherDashboardFragment : Fragment() {
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
    private lateinit var attendanceRecyclerView: RecyclerView
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

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
        attendanceRecyclerView = view.findViewById(R.id.attendanceRecyclerView)
        
        // Setup RecyclerView
        attendanceRecyclerView.layoutManager = LinearLayoutManager(requireContext())
    }
    
    private fun setupClickListeners() {
        buttonShowQr.setOnClickListener {
            showQRCode()
        }
        
        buttonRenewQr.setOnClickListener {
            renewQRCode()
        }
        
        buttonRefreshAttendance.setOnClickListener {
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
    
    private fun showQRCode() {
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
            // First, get the current schedule for this hour
            val currentTime = java.util.Calendar.getInstance()
            val currentHour = currentTime.get(java.util.Calendar.HOUR_OF_DAY)
            val currentDay = getCurrentDayOfWeek()
            
            // Find the current schedule
            db.collection("schedules")
                .whereEqualTo("teacherId", currentUser.uid)
                .whereEqualTo("day", currentDay)
                .get()
                .addOnSuccessListener { scheduleDocs ->
                    if (scheduleDocs.isEmpty()) {
                        // No schedule for today, show empty state
                        val adapter = TeacherAttendanceAdapter(emptyList())
                        attendanceRecyclerView.adapter = adapter
                        textQrExpiry.text = "No classes scheduled for today"
                        return@addOnSuccessListener
                    }
                    
                    // Find the current class based on time
                    val currentSchedule = scheduleDocs.documents.find { doc ->
                        val startTime = doc.getString("startTime") ?: ""
                        val endTime = doc.getString("endTime") ?: ""
                        
                        if (startTime.isNotEmpty() && endTime.isNotEmpty()) {
                            val startHour = parseTimeToHour(startTime)
                            val endHour = parseTimeToHour(endTime)
                            currentHour >= startHour && currentHour < endHour
                        } else {
                            false
                        }
                    }
                    
                    if (currentSchedule != null) {
                        val scheduleId = currentSchedule.id
                        val subject = currentSchedule.getString("subject") ?: ""
                        val section = currentSchedule.getString("section") ?: ""
                        
                        // Update QR status
                        textQrExpiry.text = "Current Class: $subject ($section)"
                        
                        // Load attendance for this specific schedule
                        db.collection("attendance")
                            .whereEqualTo("teacherId", currentUser.uid)
                            .whereEqualTo("scheduleId", scheduleId)
                            .whereEqualTo("subject", subject)
                            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                            .get()
                            .addOnSuccessListener { attendanceDocs ->
                                val attendanceList = attendanceDocs.map { doc ->
                                    TeacherAttendanceItem(
                                        studentName = doc.getString("studentName") ?: "Unknown Student",
                                        timeTaken = formatTimestamp(doc.getTimestamp("timestamp")),
                                        section = doc.getString("section") ?: "",
                                        status = doc.getString("status") ?: "PRESENT"
                                    )
                                }
                                
                                val adapter = TeacherAttendanceAdapter(attendanceList)
                                attendanceRecyclerView.adapter = adapter
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(requireContext(), "Error loading attendance: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        // No current class, show next class or empty state
                        val nextSchedule = scheduleDocs.documents.minByOrNull { doc ->
                            val startTime = doc.getString("startTime") ?: "00:00"
                            parseTimeToHour(startTime)
                        }
                        
                        if (nextSchedule != null) {
                            val subject = nextSchedule.getString("subject") ?: ""
                            val section = nextSchedule.getString("section") ?: ""
                            val startTime = nextSchedule.getString("startTime") ?: ""
                            textQrExpiry.text = "Next Class: $subject ($section) at $startTime"
                        } else {
                            textQrExpiry.text = "No classes scheduled for today"
                        }
                        
                        val adapter = TeacherAttendanceAdapter(emptyList())
                        attendanceRecyclerView.adapter = adapter
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Error loading schedule: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
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
                radioPresent.isChecked -> "PRESENT"
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
        
        students.forEach { student ->
            val attendanceData = hashMapOf(
                "userId" to student.id,
                "studentName" to student.name,
                "sessionId" to "",
                "teacherId" to currentUser.uid,
                "scheduleId" to scheduleId,
                "subject" to subject,
                "section" to student.section,
                "timestamp" to currentTime,
                "status" to status,
                "location" to "",
                "notes" to "Manually added by teacher - Status: $status"
            )
            
            db.collection("attendance").add(attendanceData)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Added ${student.name} to attendance", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Failed to add ${student.name}: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
        
        // Refresh attendance list
        loadRecentAttendance()
    }
}


