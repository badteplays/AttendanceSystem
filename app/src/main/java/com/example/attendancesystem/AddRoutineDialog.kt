package com.example.attendancesystem

import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.*
import com.example.attendancesystem.models.Routine
import com.google.android.material.textfield.TextInputEditText
import java.util.*

class AddRoutineDialog(
    context: Context,
    private val onRoutineCreated: (Routine) -> Unit
) : Dialog(context) {

    private lateinit var editTitle: TextInputEditText
    private lateinit var editDescription: TextInputEditText
    private lateinit var spinnerDay: Spinner
    private lateinit var layoutStartTime: View
    private lateinit var layoutEndTime: View
    private lateinit var textStartTime: TextView
    private lateinit var textEndTime: TextView
    private lateinit var buttonSave: Button
    private lateinit var buttonCancel: Button
    
    private var selectedStartTime = ""
    private var selectedEndTime = ""
    private var selectedColor = "#4CAF50"
    
    private val colors = listOf(
        "#4CAF50", "#2196F3", "#FF9800", "#E91E63", "#9C27B0"
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_add_routine)
        
        initializeViews()
        setupDaySpinner()
        setupTimePickers()
        setupColorPicker()
        setupButtons()
    }
    
    private fun initializeViews() {
        editTitle = findViewById(R.id.editRoutineTitle)
        editDescription = findViewById(R.id.editRoutineDescription)
        spinnerDay = findViewById(R.id.spinnerDay)
        layoutStartTime = findViewById(R.id.layoutStartTime)
        layoutEndTime = findViewById(R.id.layoutEndTime)
        textStartTime = findViewById(R.id.textStartTime)
        textEndTime = findViewById(R.id.textEndTime)
        buttonSave = findViewById(R.id.buttonSave)
        buttonCancel = findViewById(R.id.buttonCancel)
    }
    
    private fun setupDaySpinner() {
        val days = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, days)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDay.adapter = adapter
    }
    
    private fun setupTimePickers() {
        layoutStartTime.setOnClickListener {
            showTimePicker { hour, minute ->
                selectedStartTime = String.format("%02d:%02d", hour, minute)
                textStartTime.text = formatTimeTo12Hour(hour, minute)
            }
        }
        
        layoutEndTime.setOnClickListener {
            showTimePicker { hour, minute ->
                selectedEndTime = String.format("%02d:%02d", hour, minute)
                textEndTime.text = formatTimeTo12Hour(hour, minute)
            }
        }
    }
    
    private fun showTimePicker(onTimeSelected: (Int, Int) -> Unit) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        
        TimePickerDialog(
            context,
            { _, selectedHour, selectedMinute ->
                onTimeSelected(selectedHour, selectedMinute)
            },
            hour,
            minute,
            false // 12-hour format with AM/PM
        ).show()
    }
    
    private fun formatTimeTo12Hour(hour: Int, minute: Int): String {
        val amPm = if (hour < 12) "AM" else "PM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format("%d:%02d %s", displayHour, minute, amPm)
    }
    
    private fun setupColorPicker() {
        val colorViews = listOf(
            findViewById<View>(R.id.colorOption1),
            findViewById<View>(R.id.colorOption2),
            findViewById<View>(R.id.colorOption3),
            findViewById<View>(R.id.colorOption4),
            findViewById<View>(R.id.colorOption5)
        )
        
        colorViews.forEachIndexed { index, view ->
            view.setOnClickListener {
                selectedColor = colors[index]
                // Highlight selected color
                colorViews.forEach { it.alpha = 0.5f }
                view.alpha = 1.0f
            }
        }
        
        // Select first color by default
        colorViews[0].alpha = 1.0f
        colorViews.drop(1).forEach { it.alpha = 0.5f }
    }
    
    private fun setupButtons() {
        buttonCancel.setOnClickListener {
            dismiss()
        }
        
        buttonSave.setOnClickListener {
            validateAndSave()
        }
    }
    
    private fun validateAndSave() {
        val title = editTitle.text?.toString()?.trim() ?: ""
        val description = editDescription.text?.toString()?.trim() ?: ""
        val day = spinnerDay.selectedItem?.toString() ?: ""
        
        when {
            title.isEmpty() -> {
                Toast.makeText(context, "Please enter a title", Toast.LENGTH_SHORT).show()
                return
            }
            selectedStartTime.isEmpty() -> {
                Toast.makeText(context, "Please select a start time", Toast.LENGTH_SHORT).show()
                return
            }
            selectedEndTime.isEmpty() -> {
                Toast.makeText(context, "Please select an end time", Toast.LENGTH_SHORT).show()
                return
            }
            !isValidTimeRange(selectedStartTime, selectedEndTime) -> {
                Toast.makeText(context, "End time must be after start time", Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        val routine = Routine(
            title = title,
            description = description,
            day = day,
            startTime = selectedStartTime,
            endTime = selectedEndTime,
            color = selectedColor
        )
        
        onRoutineCreated(routine)
        dismiss()
    }
    
    private fun isValidTimeRange(start: String, end: String): Boolean {
        return try {
            val startParts = start.split(":")
            val endParts = end.split(":")
            val startMinutes = startParts[0].toInt() * 60 + startParts[1].toInt()
            val endMinutes = endParts[0].toInt() * 60 + endParts[1].toInt()
            endMinutes > startMinutes
        } catch (e: Exception) {
            false
        }
    }
}

