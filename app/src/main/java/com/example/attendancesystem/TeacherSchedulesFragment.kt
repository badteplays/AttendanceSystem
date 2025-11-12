package com.example.attendancesystem

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class TeacherSchedulesFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: LinearLayout
    private lateinit var scheduleFormCard: MaterialCardView
    private lateinit var editSubject: TextInputEditText
    private lateinit var editSection: TextInputEditText
    private lateinit var editDay: AutoCompleteTextView
    private lateinit var editStartTime: MaterialButton
    private lateinit var editEndTime: MaterialButton
    private lateinit var btnAddSchedule: MaterialButton
    private lateinit var btnCancelSchedule: MaterialButton
    private val schedules = arrayListOf<TeacherScheduleRow>()
    private lateinit var adapter: TeacherSimpleScheduleAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_teacher_schedules, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            recyclerView = view.findViewById(R.id.recyclerView)
            progressBar = view.findViewById(R.id.progressBar)
            emptyState = view.findViewById(R.id.emptyState)
            scheduleFormCard = view.findViewById(R.id.scheduleFormCard)
            editSubject = view.findViewById(R.id.editSubject)
            editSection = view.findViewById(R.id.editSection)
            editDay = view.findViewById(R.id.editDay)
            editStartTime = view.findViewById(R.id.editStartTime)
            editEndTime = view.findViewById(R.id.editEndTime)
            btnAddSchedule = view.findViewById(R.id.btnAddSchedule)
            btnCancelSchedule = view.findViewById(R.id.btnCancelSchedule)
            
            adapter = TeacherSimpleScheduleAdapter(schedules) { row ->
                showScheduleActions(row)
            }
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = adapter

            setupDayDropdown()
            setupClickListeners()
            loadSchedules()
        } catch (e: Exception) {
            android.util.Log.e("TeacherSchedulesFragment", "Error in onViewCreated: ${e.message}", e)
            showEmptyState("Error loading schedules: ${e.message}")
        }
    }
    
    private fun setupDayDropdown() {
        val days = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, days)
        editDay.setAdapter(adapter)
    }
    
    private fun setupClickListeners() {
        // FAB Add button - show/hide schedule form
        view?.findViewById<FloatingActionButton>(R.id.fabAdd)?.setOnClickListener {
            if (scheduleFormCard.visibility == View.GONE) {
                scheduleFormCard.visibility = View.VISIBLE
            } else {
                scheduleFormCard.visibility = View.GONE
            }
        }
        
        // Time pickers
        editStartTime.setOnClickListener {
            showTimePicker { hour, minute ->
                selectedStartHour = hour
                selectedStartMinute = minute
                editStartTime.text = formatTimeTo12Hour(hour, minute)
            }
        }
        
        editEndTime.setOnClickListener {
            showTimePicker { hour, minute ->
                selectedEndHour = hour
                selectedEndMinute = minute
                editEndTime.text = formatTimeTo12Hour(hour, minute)
            }
        }
        
        // Add Schedule button
        btnAddSchedule.setOnClickListener {
            addSchedule()
        }
        
        // Cancel Schedule button
        btnCancelSchedule.setOnClickListener {
            hideScheduleForm()
        }
    }
    
    private fun showTimePicker(onTimeSelected: (Int, Int) -> Unit) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        
        TimePickerDialog(
            requireContext(),
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
    
    private var selectedStartHour = 0
    private var selectedStartMinute = 0
    private var selectedEndHour = 0
    private var selectedEndMinute = 0
    
    private fun addSchedule() {
        val subject = editSubject.text.toString().trim()
        val section = editSection.text.toString().trim()
        val day = editDay.text.toString().trim()
        val startTime = editStartTime.text.toString().trim()
        val endTime = editEndTime.text.toString().trim()

        android.util.Log.d("TeacherSchedules", "Add Schedule called - subject: $subject, section: $section, day: $day, startTime: $startTime, endTime: $endTime")
        android.util.Log.d("TeacherSchedules", "Selected hours/minutes - start: $selectedStartHour:$selectedStartMinute, end: $selectedEndHour:$selectedEndMinute")

        if (subject.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a subject", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (section.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a section", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (day.isEmpty()) {
            Toast.makeText(requireContext(), "Please select a day", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (startTime.isEmpty() || startTime.equals("Select start time", ignoreCase = true)) {
            Toast.makeText(requireContext(), "Please select a start time", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (endTime.isEmpty() || endTime.equals("Select end time", ignoreCase = true)) {
            Toast.makeText(requireContext(), "Please select an end time", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        // Store in 24-hour format for consistency
        val startTime24 = String.format("%02d:%02d", selectedStartHour, selectedStartMinute)
        val endTime24 = String.format("%02d:%02d", selectedEndHour, selectedEndMinute)

        android.util.Log.d("TeacherSchedules", "Creating schedule with times: $startTime24 - $endTime24")

        val schedule = hashMapOf(
            "teacherId" to currentUser.uid,
            "subject" to subject,
            "section" to section,
            "day" to day,
            "startTime" to startTime24,
            "endTime" to endTime24,
            "lastGeneratedDate" to ""
        )

        Toast.makeText(requireContext(), "Creating schedule...", Toast.LENGTH_SHORT).show()

        db.collection("schedules")
            .add(schedule)
            .addOnSuccessListener {
                android.util.Log.d("TeacherSchedules", "Schedule added successfully!")
                Toast.makeText(requireContext(), "Schedule created", Toast.LENGTH_SHORT).show()
                hideScheduleForm()
                loadSchedules() // Refresh the list
            }
            .addOnFailureListener { e ->
                android.util.Log.e("TeacherSchedules", "Failed to add schedule: ${e.message}", e)
                Toast.makeText(requireContext(), "Failed to create schedule: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
    
    private fun hideScheduleForm() {
        scheduleFormCard.visibility = View.GONE
        // Clear form fields
        editSubject.setText("")
        editSection.setText("")
        editDay.setText("")
        editStartTime.text = "Select start time"
        editEndTime.text = "Select end time"
    }

    fun loadSchedules() {
        progressBar.visibility = View.VISIBLE
        val user = FirebaseAuth.getInstance().currentUser ?: return
        db.collection("schedules")
            .whereEqualTo("teacherId", user.uid)
            .get()
            .addOnSuccessListener { docs ->
                schedules.clear()
                for (d in docs) {
                    schedules.add(
                        TeacherScheduleRow(
                            id = d.id,
                            subject = d.getString("subject") ?: "",
                            section = d.getString("section") ?: "",
                            day = d.getString("day") ?: "",
                            startTime = d.getString("startTime") ?: "",
                            endTime = d.getString("endTime") ?: ""
                        )
                    )
                }
                // Total header removed per request
                adapter.notifyDataSetChanged()
                recyclerView.visibility = if (schedules.isEmpty()) View.GONE else View.VISIBLE
                emptyState.visibility = if (schedules.isEmpty()) View.VISIBLE else View.GONE
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                recyclerView.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
                android.util.Log.e("TeacherSchedulesFragment", "Error loading schedules: ${e.message}", e)
            }
    }
    
    private fun showEmptyState(message: String) {
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        // Total header removed per request
        
        // Update empty state message if needed
        val emptyMessage = emptyState.findViewById<TextView>(android.R.id.text1)
        emptyMessage?.text = message
    }
}

data class TeacherScheduleRow(
    val id: String,
    val subject: String,
    val section: String,
    val day: String,
    val startTime: String,
    val endTime: String
)

class TeacherSimpleScheduleAdapter(private val items: List<TeacherScheduleRow>, private val onLongPress: (TeacherScheduleRow) -> Unit) : RecyclerView.Adapter<TeacherSimpleScheduleAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val subject: TextView = v.findViewById(R.id.subjectText)
        val section: TextView = v.findViewById(R.id.sectionText)
        val day: TextView = v.findViewById(R.id.dayText)
        val time: TextView = v.findViewById(R.id.timeText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_simple_schedule, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        holder.subject.text = s.subject
        holder.section.text = s.section.uppercase()
        holder.day.text = s.day
        holder.time.text = "${format24To12(s.startTime)} - ${format24To12(s.endTime)}"
        holder.itemView.setOnLongClickListener {
            onLongPress(items[holder.bindingAdapterPosition])
            true
        }
    }

    override fun getItemCount(): Int = items.size
}

private fun format24To12(time24: String): String {
    return try {
        val parts = time24.split(":")
        if (parts.size != 2) return time24
        var hour = parts[0].toInt()
        val minute = parts[1].toInt()
        val amPm = if (hour < 12) "AM" else "PM"
        val hour12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        String.format("%d:%02d %s", hour12, minute, amPm)
    } catch (_: Exception) { time24 }
}

private fun TeacherSchedulesFragment.showScheduleActions(row: TeacherScheduleRow) {
    androidx.appcompat.app.AlertDialog.Builder(requireContext())
        .setTitle(row.subject)
        .setItems(arrayOf("Edit", "Delete")) { dialog, which ->
            dialog.dismiss()
            when (which) {
                0 -> showEditDialog(row)
                1 -> confirmDelete(row)
            }
        }
        .show()
}

private fun TeacherSchedulesFragment.confirmDelete(row: TeacherScheduleRow) {
    androidx.appcompat.app.AlertDialog.Builder(requireContext())
        .setTitle("Delete schedule")
        .setMessage("Delete ${row.subject} (${row.section}) on ${row.day}?")
        .setPositiveButton("Delete") { _, _ ->
            FirebaseFirestore.getInstance().collection("schedules").document(row.id)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Schedule deleted", Toast.LENGTH_SHORT).show()
                    loadSchedules()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
        .setNegativeButton("Cancel", null)
        .show()
}

private fun TeacherSchedulesFragment.showEditDialog(row: TeacherScheduleRow) {
    val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_schedule, null)
    val subjectInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editSubject)
    val sectionInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editSection)
    val roomInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editRoom)
    val spinnerDay = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerDay)
    val timeStart = dialogView.findViewById<android.widget.TimePicker>(R.id.timePickerStart)
    val timeEnd = dialogView.findViewById<android.widget.TimePicker>(R.id.timePickerEnd)

    // Populate
    subjectInput.setText(row.subject)
    sectionInput.setText(row.section)
    val days = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    spinnerDay.adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, days)
    spinnerDay.setSelection(days.indexOfFirst { it.equals(row.day, true) }.coerceAtLeast(0))

    fun setPickerFrom24(picker: android.widget.TimePicker, value24: String) {
        picker.setIs24HourView(false)
        val parts = value24.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        picker.hour = h
        picker.minute = m
    }
    setPickerFrom24(timeStart, row.startTime)
    setPickerFrom24(timeEnd, row.endTime)

    val dlg = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        .setView(dialogView)
        .create()

    dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)?.setOnClickListener { dlg.dismiss() }
    dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave)?.setOnClickListener {
        val subject = subjectInput.text?.toString()?.trim().orEmpty()
        val section = sectionInput.text?.toString()?.trim().orEmpty()
        val day = spinnerDay.selectedItem?.toString()?.trim().orEmpty()
        val start24 = String.format("%02d:%02d", timeStart.hour, timeStart.minute)
        val end24 = String.format("%02d:%02d", timeEnd.hour, timeEnd.minute)

        if (subject.isBlank() || section.isBlank() || day.isBlank()) {
            Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
            return@setOnClickListener
        }

        val updates = mapOf(
            "subject" to subject,
            "section" to section,
            "day" to day,
            "startTime" to start24,
            "endTime" to end24
        )
        FirebaseFirestore.getInstance().collection("schedules").document(row.id)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Schedule updated", Toast.LENGTH_SHORT).show()
                dlg.dismiss()
                loadSchedules()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    dlg.show()
}


