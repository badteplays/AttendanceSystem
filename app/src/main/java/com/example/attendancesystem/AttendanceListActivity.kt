package com.example.attendancesystem

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
// import com.example.attendancesystem.databinding.ActivityAttendanceListBinding
import com.example.attendancesystem.models.Attendance
import com.example.attendancesystem.models.AttendanceStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.view.View
import android.app.DatePickerDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import android.widget.Button
import android.widget.TextView

class AttendanceListActivity : AppCompatActivity() {
    // private lateinit var binding: ActivityAttendanceListBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: AttendanceListAdapter
    private var scheduleId: String = ""
    private var multiSelectSnackbar: com.google.android.material.snackbar.Snackbar? = null
    private var confirmDialog: androidx.appcompat.app.AlertDialog? = null
    private var allAttendanceList: List<Attendance> = emptyList()
    private var allCourses: List<String> = emptyList()
    private var allStudents: List<String> = emptyList()
    private var selectedCourse: String? = null
    private var selectedStudent: String? = null
    private var selectedStartDate: Long? = null
    private var selectedEndDate: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // binding = ActivityAttendanceListBinding.inflate(layoutInflater)
        setContentView(R.layout.activity_attendance_list)

        // Check user role
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener { doc ->
                    val isTeacher = doc.getBoolean("isTeacher") ?: false
                    android.util.Log.d("AttendanceList", "Current user role - isTeacher: $isTeacher")
                    Toast.makeText(this, "User role - isTeacher: $isTeacher", Toast.LENGTH_SHORT).show()
                }
        }

        scheduleId = intent.getStringExtra("scheduleId") ?: ""
        if (scheduleId.isEmpty()) {
            // If no specific schedule ID provided, show all attendance for this teacher
            android.util.Log.d("AttendanceList", "No scheduleId provided, showing all attendance")
            Toast.makeText(this, "Showing all attendance records", Toast.LENGTH_SHORT).show()
        }

        db = Firebase.firestore
        setupRecyclerView()
        loadAttendanceList()
        setupStatistics()

        val spinner: Spinner = findViewById(R.id.spinnerStatusFilter)
        val statusOptions = listOf("All", "PRESENT", "LATE", "ABSENT", "EXCUSED")
        val adapterSpinner = ArrayAdapter(this, android.R.layout.simple_spinner_item, statusOptions)
        adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapterSpinner
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                filterAttendanceList(statusOptions[position])
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }

        val spinnerCourse: Spinner = findViewById(R.id.spinnerCourseFilter)
        val spinnerStudent: Spinner = findViewById(R.id.spinnerStudentFilter)
        val btnStartDate: Button = findViewById(R.id.btnStartDate)
        val btnEndDate: Button = findViewById(R.id.btnEndDate)
        // Load courses and students from Firestore
        db.collection("schedules").get().addOnSuccessListener { docs ->
            allCourses = docs.mapNotNull { it.getString("name") }
            val courseAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("All") + allCourses)
            courseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerCourse.adapter = courseAdapter
        }
        db.collection("users").whereEqualTo("isTeacher", false).get().addOnSuccessListener { docs ->
            allStudents = docs.mapNotNull { it.getString("name") }
            val studentAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("All") + allStudents)
            studentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerStudent.adapter = studentAdapter
        }
        spinnerCourse.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedCourse = if (position == 0) null else allCourses.getOrNull(position - 1)
                filterAttendanceList(findViewById<Spinner>(R.id.spinnerStatusFilter).selectedItem?.toString() ?: "All")
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }
        spinnerStudent.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedStudent = if (position == 0) null else allStudents.getOrNull(position - 1)
                filterAttendanceList(findViewById<Spinner>(R.id.spinnerStatusFilter).selectedItem?.toString() ?: "All")
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        btnStartDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                cal.set(year, month, day, 0, 0, 0)
                selectedStartDate = cal.timeInMillis
                btnStartDate.text = dateFormat.format(cal.time)
                filterAttendanceList(findViewById<Spinner>(R.id.spinnerStatusFilter).selectedItem?.toString() ?: "All")
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        btnEndDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                cal.set(year, month, day, 23, 59, 59)
                selectedEndDate = cal.timeInMillis
                btnEndDate.text = dateFormat.format(cal.time)
                filterAttendanceList(findViewById<Spinner>(R.id.spinnerStatusFilter).selectedItem?.toString() ?: "All")
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun setupRecyclerView() {
        adapter = AttendanceListAdapter(
            onItemClick = { attendance ->
                if (adapter.multiSelectMode) {
                    val pos = adapter.currentList.indexOf(attendance)
                    if (pos != -1) adapter.toggleSelection(pos)
                } else {
                    val intent = Intent(this, StudentProfileActivity::class.java)
                    intent.putExtra("studentId", attendance.studentId)
                    intent.putExtra("studentName", attendance.studentName)
                    startActivity(intent)
                }
            },
            onManualAddClick = {
                showManualAddDialog()
            }
        )

        // Enable remove student callback with confirmation
        adapter.onRemoveStudent = { attendance ->
            showRemoveConfirmationDialog(attendance)
        }

        // Listen for selection changes
        adapter.onSelectionChanged = { selected ->
            if (selected.isNotEmpty()) {
                showBulkActionSnackbar(selected.size)
            } else {
                multiSelectSnackbar?.dismiss()
                adapter.multiSelectMode = false
            }
        }

        val recyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadAttendanceList() {
        android.util.Log.d("AttendanceLoad", "Starting to load attendance list for scheduleId: $scheduleId")
        
        val query = if (scheduleId.isEmpty()) {
            // Show all attendance for this teacher
            val currentUser = FirebaseAuth.getInstance().currentUser
            db.collection("attendance")
                .orderBy("timestamp", Query.Direction.DESCENDING)
        } else {
            // Show attendance for specific schedule
            db.collection("attendance")
                .whereEqualTo("scheduleId", scheduleId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
        }
        
        query
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    android.util.Log.e("AttendanceLoad", "Error loading attendance: ${e.message}", e)
                    Toast.makeText(this, getString(R.string.error_loading_attendance, e.message), 
                        Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                android.util.Log.d("AttendanceLoad", "Received ${snapshot?.documents?.size ?: 0} documents")
                val newList = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Attendance::class.java)?.apply {
                        id = doc.id
                        android.util.Log.d("AttendanceLoad", "Loaded attendance with id: $id, name: $studentName")
                    }
                } ?: emptyList()
                allAttendanceList = newList
                filterAttendanceList(findViewById<Spinner>(R.id.spinnerStatusFilter).selectedItem?.toString() ?: "All")
                updateStatistics(newList)
            }
    }

    private fun setupStatistics() {
        // Initialize statistics views
        findViewById<TextView>(R.id.txtTotalStudents).text = getString(R.string.total_students, 0)
        findViewById<TextView>(R.id.txtPresentStudents).text = getString(R.string.present_students, 0)
        findViewById<TextView>(R.id.txtLateStudents).text = getString(R.string.late_students, 0)
        findViewById<TextView>(R.id.txtAbsentStudents).text = getString(R.string.absent_students, 0)
        findViewById<TextView>(R.id.txtExcusedStudents).text = getString(R.string.excused_students, 0)
    }

    private fun updateStatistics(attendanceList: List<Attendance>) {
        val total = attendanceList.size
        val present = attendanceList.count { it.status == AttendanceStatus.PRESENT }
        val late = attendanceList.count { it.status == AttendanceStatus.LATE }
        val absent = attendanceList.count { it.status == AttendanceStatus.ABSENT }
        val excused = attendanceList.count { it.status == AttendanceStatus.EXCUSED }

        findViewById<TextView>(R.id.txtTotalStudents).text = getString(R.string.total_students, total)
        findViewById<TextView>(R.id.txtPresentStudents).text = getString(R.string.present_students, present)
        findViewById<TextView>(R.id.txtLateStudents).text = getString(R.string.late_students, late)
        findViewById<TextView>(R.id.txtAbsentStudents).text = getString(R.string.absent_students, absent)
        findViewById<TextView>(R.id.txtExcusedStudents).text = getString(R.string.excused_students, excused)
    }

    private fun showAttendanceDetails(attendance: Attendance) {
        val dialog = AttendanceDetailsDialog(this, attendance) { _ ->
            // Refresh the attendance list to show the updated data
            loadAttendanceList()
        }
        dialog.show()
    }

    private fun showManualAddDialog() {
        try {
            if (scheduleId.isEmpty()) {
                Toast.makeText(this, "Manual add is only available for specific class sessions", Toast.LENGTH_SHORT).show()
                return
            }
            val sessionId = intent.getStringExtra("sessionId") ?: ""
            android.util.Log.d("AttendanceList", "Opening manual add dialog with scheduleId: $scheduleId, sessionId: $sessionId")
            val dialog = ManualAddStudentDialog(this, scheduleId, sessionId)
            dialog.show()
        } catch (e: Exception) {
            android.util.Log.e("AttendanceList", "Error opening manual add dialog", e)
            Toast.makeText(this, "Error opening student selection: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRemoveConfirmationDialog(attendance: Attendance) {
        confirmDialog?.dismiss()
        confirmDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.confirm_remove_title)
            .setMessage(getString(R.string.confirm_remove_message, attendance.studentName))
            .setPositiveButton(R.string.remove) { _, _ ->
                actuallyRemoveStudent(attendance)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showBulkActionSnackbar(selectedCount: Int) {
        multiSelectSnackbar?.dismiss()
        multiSelectSnackbar = com.google.android.material.snackbar.Snackbar.make(
            findViewById(android.R.id.content),
            getString(R.string.selected_count, selectedCount),
            com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE
        ).setAction(R.string.remove) {
            showBulkRemoveConfirmation()
        }.setActionTextColor(getColor(R.color.status_absent))
        multiSelectSnackbar?.show()
    }

    private fun showBulkRemoveConfirmation() {
        confirmDialog?.dismiss()
        val selected = adapter.getSelectedAttendances()
        confirmDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.confirm_bulk_remove_title)
            .setMessage(getString(R.string.confirm_bulk_remove_message, selected.size))
            .setPositiveButton(R.string.remove) { _, _ ->
                actuallyRemoveStudents(selected)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun actuallyRemoveStudent(attendance: Attendance) {
        removeStudentFromAttendance(attendance, showUndo = true)
    }
    private fun actuallyRemoveStudents(attendances: List<Attendance>) {
        attendances.forEach { removeStudentFromAttendance(it, showUndo = true) }
        adapter.clearSelection()
    }

    // Updated to support undo
    private fun removeStudentFromAttendance(attendance: Attendance, showUndo: Boolean = false) {
        // Debug log to check the ID being used
        android.util.Log.d("AttendanceDelete", "Attempting to delete attendance with id: ${attendance.id}")
        android.util.Log.d("AttendanceDelete", "Full attendance object: $attendance")
        Toast.makeText(this, "Deleting id: ${attendance.id}", Toast.LENGTH_SHORT).show()
        
        if (attendance.id.isBlank()) {
            android.util.Log.e("AttendanceDelete", "Error: Attendance ID is blank!")
            Toast.makeText(this, "Error: Invalid attendance ID", Toast.LENGTH_SHORT).show()
            return
        }

        val attendanceId = attendance.id
        db.collection("attendance")
            .document(attendanceId)
            .delete()
            .addOnSuccessListener {
                android.util.Log.d("AttendanceDelete", "Successfully deleted attendance with id: $attendanceId")
                if (showUndo) showUndoSnackbar(attendance)
                Toast.makeText(this, getString(R.string.student_removed), Toast.LENGTH_SHORT).show()
                loadAttendanceList() // Force reload after delete
            }
            .addOnFailureListener { e ->
                android.util.Log.e("AttendanceDelete", "Failed to delete attendance: ${e.message}", e)
                Toast.makeText(this, getString(R.string.error_removing_student, e.message), Toast.LENGTH_SHORT).show()
            }
    }

    private fun showUndoSnackbar(removed: Attendance) {
        com.google.android.material.snackbar.Snackbar.make(
            findViewById(android.R.id.content),
            getString(R.string.student_removed_undo, removed.studentName),
            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
        ).setAction(R.string.undo) {
            restoreAttendance(removed)
        }.show()
    }

    private fun restoreAttendance(attendance: Attendance) {
        val parts = attendance.id.split("/")
        if (parts.size == 2) {
            val scheduleId = parts[0]
            val attendanceId = parts[1]
            db.collection("schedules")
                .document(scheduleId)
                .collection("attendance")
                .document(attendanceId)
                .set(attendance)
        }
    }

    private fun filterAttendanceList(status: String) {
        var filtered = allAttendanceList
        if (selectedCourse != null) {
            filtered = filtered.filter { it.subject == selectedCourse }
        }
        if (selectedStudent != null) {
            filtered = filtered.filter { it.studentName == selectedStudent }
        }
        if (selectedStartDate != null) {
            filtered = filtered.filter { it.timestamp.toDate().time >= selectedStartDate!! }
        }
        if (selectedEndDate != null) {
            filtered = filtered.filter { it.timestamp.toDate().time <= selectedEndDate!! }
        }
        filtered = if (status == "All") filtered else filtered.filter { it.status.name == status }
        adapter.submitAttendanceList(filtered)
    }
} 