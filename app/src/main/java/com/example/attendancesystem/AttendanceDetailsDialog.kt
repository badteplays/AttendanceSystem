package com.example.attendancesystem

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Window
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatSpinner
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.AppCompatEditText
import android.widget.ArrayAdapter
import com.example.attendancesystem.models.Attendance
import com.example.attendancesystem.models.AttendanceStatus
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class AttendanceDetailsDialog(
    context: Context,
    private val attendance: Attendance,
    private val onUpdated: (Attendance) -> Unit
) : Dialog(context) {

    private lateinit var textStudentName: AppCompatTextView
    private lateinit var textTimestamp: AppCompatTextView
    private lateinit var textSubject: AppCompatTextView
    private lateinit var textSection: AppCompatTextView
    private lateinit var spinnerStatus: AppCompatSpinner
    private lateinit var editNotes: AppCompatEditText
    private lateinit var buttonSave: AppCompatButton
    private lateinit var buttonCancel: AppCompatButton

    private val db = FirebaseFirestore.getInstance()
    private val statusOptions = AttendanceStatus.values().map { it.name }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_attendance_details)

        initializeViews()
        setupData()
        setupClickListeners()
    }

    private fun initializeViews() {
        textStudentName = findViewById(R.id.textStudentName)
        textTimestamp = findViewById(R.id.textTimestamp)
        textSubject = findViewById(R.id.textSubject)
        textSection = findViewById(R.id.textSection)
        spinnerStatus = findViewById(R.id.spinnerStatus)
        editNotes = findViewById(R.id.editNotes)
        buttonSave = findViewById(R.id.buttonSave)
        buttonCancel = findViewById(R.id.buttonCancel)
    }

    private fun setupData() {
        // Set attendance details
        textStudentName.text = attendance.studentName
        
        val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
        textTimestamp.text = dateFormat.format(attendance.timestamp.toDate())
        
        textSubject.text = attendance.subject
        textSection.text = attendance.section
        editNotes.setText(attendance.notes)

        // Setup status spinner
        val statusAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, statusOptions)
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStatus.adapter = statusAdapter

        // Set current status
        val currentStatusIndex = statusOptions.indexOf(attendance.status.name)
        if (currentStatusIndex != -1) {
            spinnerStatus.setSelection(currentStatusIndex)
        }
    }

    private fun setupClickListeners() {
        buttonCancel.setOnClickListener {
            dismiss()
        }

        buttonSave.setOnClickListener {
            saveAttendanceChanges()
        }
    }

    private fun saveAttendanceChanges() {
        val selectedStatus = AttendanceStatus.valueOf(statusOptions[spinnerStatus.selectedItemPosition])
        val notes = editNotes.text.toString().trim()

        // Update attendance in Firestore
        val updateData: MutableMap<String, Any?> = hashMapOf(
            "status" to selectedStatus.name,
            "notes" to notes,
            "lastModified" to com.google.firebase.Timestamp.now()
        )

        db.collection("attendance")
            .document(attendance.id)
            .update(updateData)
            .addOnSuccessListener {
                // Update local attendance object
                attendance.status = selectedStatus
                attendance.notes = notes
                
                onUpdated(attendance)
                Toast.makeText(context, "Attendance updated successfully", Toast.LENGTH_SHORT).show()
                dismiss()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to update attendance: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
} 