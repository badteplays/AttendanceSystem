package com.example.attendancesystem

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Window
import android.widget.Toast

import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import com.example.attendancesystem.models.AttendanceStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import android.widget.ArrayAdapter
import android.widget.Spinner
import com.google.firebase.firestore.FieldPath

class ManualAddStudentDialog(context: Context, private val scheduleId: String, private val sessionId: String) : Dialog(context) {

    private val db = FirebaseFirestore.getInstance()
    private var studentIdList: List<String> = emptyList()
    private var studentNameList: List<String> = emptyList()
    private var selectedStudentId: String? = null
    private var classSubject: String = ""
    private var classSection: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        setContentView(R.layout.dialog_manual_add_student)

        loadEligibleStudents()
        setupClickListeners()
    }

    private fun loadEligibleStudents() {
        db.collection("schedules").document(scheduleId).get().addOnSuccessListener { doc ->

            classSubject = doc.getString("subject") ?: "Unknown Subject"
            classSection = doc.getString("section") ?: "Unknown Section"

            findViewById<TextView>(R.id.textCurrentSession).text = "Current Session: $classSubject ($classSection)"

            db.collection("attendance")
                .whereEqualTo("scheduleId", scheduleId)
                .whereEqualTo("sessionId", sessionId)
                .whereEqualTo("status", "PRESENT")
                .get().addOnSuccessListener { attendanceSnapshot ->
                    val presentStudentIds = attendanceSnapshot.documents.mapNotNull { it.getString("studentId") }

                    db.collection("users")
                        .whereEqualTo("isStudent", true)
                        .get().addOnSuccessListener { usersSnapshot ->
                            val eligibleStudents = usersSnapshot.documents
                                .filter { doc ->

                                    val studentSection = doc.getString("section") ?: ""
                                    studentSection.equals(classSection, ignoreCase = true) &&
                                    !presentStudentIds.contains(doc.id)
                                }
                                .map { doc -> (doc.getString("name") ?: "Unnamed") to doc.id }

                            if (eligibleStudents.isEmpty()) {
                                Toast.makeText(context, "No students found in Section $classSection or all students are already present.", Toast.LENGTH_SHORT).show()
                                dismiss()
                                return@addOnSuccessListener
                            }

                            studentNameList = eligibleStudents.map { pair -> pair.first }.sorted()
                            studentIdList = eligibleStudents.map { pair -> pair.second }

                            Log.d("ManualAddDialog", "Found ${eligibleStudents.size} eligible students in Section $classSection")

                            setupAutoCompleteTextView()
                        }
                        .addOnFailureListener { e ->
                            Log.e("ManualAddDialog", "Error loading students from Section $classSection: ${e.message}")
                            Toast.makeText(context, "Error loading students from Section $classSection", Toast.LENGTH_SHORT).show()
                            dismiss()
                        }
                }
                .addOnFailureListener { e ->
                    Log.e("ManualAddDialog", "Error loading attendance data: ${e.message}")
                    Toast.makeText(context, "Error loading attendance data", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
        }.addOnFailureListener { e ->
            Log.e("ManualAddDialog", "Error loading schedule: ${e.message}")
            Toast.makeText(context, "Error loading class information", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    private fun setupAutoCompleteTextView() {
        val autoComplete = findViewById<AutoCompleteTextView>(R.id.editStudentName)

        val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, studentNameList)
        autoComplete.setAdapter(adapter)

        autoComplete.setOnItemClickListener { _, _, position, _ ->
            val selectedName = studentNameList.getOrNull(position)
            if (selectedName != null) {
                val index = studentNameList.indexOf(selectedName)
                selectedStudentId = if (index >= 0) studentIdList.getOrNull(index) else null
                Log.d("ManualAddDialog", "Selected from dropdown: $selectedName (ID: $selectedStudentId)")
            }
        }

        autoComplete.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {

                selectedStudentId = null
            }
        }

        Log.d("ManualAddDialog", "Loaded ${studentNameList.size} eligible students from Section $classSection (Subject: $classSubject)")
    }

    private fun setupClickListeners() {
        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dismiss()
        }
        findViewById<Button>(R.id.btnAddStudent).setOnClickListener {
            val enteredName = findViewById<AutoCompleteTextView>(R.id.editStudentName).text.toString().trim()

            if (enteredName.isEmpty()) {
                Toast.makeText(context, "Please enter or select a student name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedStudentId != null) {

                val index = studentIdList.indexOf(selectedStudentId)
                if (index >= 0) {
                    addStudentToAttendance(studentIdList[index], studentNameList[index], isFromClass = true)
                } else {
                    Toast.makeText(context, "Error: Selected student not found", Toast.LENGTH_SHORT).show()
                }
            } else {

                val matchingIndex = studentNameList.indexOfFirst { it.equals(enteredName, ignoreCase = true) }
                if (matchingIndex >= 0) {

                    addStudentToAttendance(studentIdList[matchingIndex], studentNameList[matchingIndex], isFromClass = true)
                } else {

                    addStudentToAttendance(generateStudentId(enteredName), enteredName, isFromClass = false)
                }
            }
        }
    }

    private fun generateStudentId(studentName: String): String {

        return "manual_${studentName.replace(" ", "_").lowercase()}_${System.currentTimeMillis()}"
    }

    private fun addStudentToAttendance(studentId: String, studentName: String, isFromClass: Boolean) {
        val attendanceData = hashMapOf(
            "studentId" to studentId,
            "studentName" to studentName,
            "timestamp" to Timestamp.now(),
            "location" to "",
            "status" to AttendanceStatus.PRESENT.name,
            "notes" to if (isFromClass) "" else "Manually added (not in Section $classSection roster)",
            "scheduleId" to scheduleId,
            "subject" to classSubject,
            "section" to classSection.lowercase(),
            "sessionId" to sessionId,
            "isManualEntry" to true,
            "isFromClassRoster" to isFromClass
        )
        db.collection("attendance")
            .add(attendanceData)
            .addOnSuccessListener {
                Log.d("ManualAddStudentDialog", "Successfully added manual attendance: $attendanceData")
                val message = if (isFromClass) {
                    "$studentName from Section $classSection added successfully!"
                } else {
                    "$studentName manually added (not in Section $classSection roster)"
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                dismiss()
            }
            .addOnFailureListener { e ->
                Log.e("ManualAddStudentDialog", "Failed to add manual attendance: ${e.message}", e)
                Toast.makeText(context, "Failed to add student: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}