package com.example.attendancesystem

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TimePicker
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.attendancesystem.adapters.ScheduleAdapter
import com.example.attendancesystem.models.Schedule
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ItemScheduleActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var scheduleAdapter: ScheduleAdapter
    private val scheduleList = mutableListOf<Schedule>()
    private lateinit var db: FirebaseFirestore
    private lateinit var teacherId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_schedule)

        db = FirebaseFirestore.getInstance()
        teacherId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        recyclerView = findViewById(R.id.recyclerViewSchedules)
        recyclerView.layoutManager = LinearLayoutManager(this)
        scheduleAdapter = ScheduleAdapter(scheduleList,
            onEditSchedule = { schedule -> showEditScheduleDialog(schedule) },
            onRemoveClass = { schedule -> removeSchedule(schedule) }
        )
        recyclerView.adapter = scheduleAdapter

        loadSchedules()

        // --- Setup bottom navigation ---
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> startActivity(Intent(this, TeacherMainActivity::class.java)).let { true }
                R.id.nav_schedule -> startActivity(Intent(this, TeacherMainActivity::class.java).putExtra("selected_tab", R.id.nav_schedule)).let { true }
                R.id.nav_settings -> startActivity(Intent(this, TeacherMainActivity::class.java).putExtra("selected_tab", R.id.nav_settings)).let { true }
                else -> false
            }
        }
    }

    private fun loadSchedules() {
        db.collection("schedules")
            .whereEqualTo("teacherId", teacherId)
            .get()
            .addOnSuccessListener { result ->
                scheduleList.clear()
                for (doc in result) {
                    val schedule = Schedule(
                        id = doc.id,
                        subject = doc.getString("subject") ?: "",
                        section = doc.getString("section") ?: "",
                        teacherId = doc.getString("teacherId") ?: "",
                        startTime = doc.getString("startTime") ?: "",
                        endTime = doc.getString("endTime") ?: "",
                        day = doc.getString("day") ?: "",
                        room = doc.getString("room") ?: "",
                        lastGeneratedDate = doc.getString("lastGeneratedDate") ?: ""
                    )
                    scheduleList.add(schedule)
                }
                scheduleAdapter.notifyDataSetChanged()
            }
    }

    private fun removeSchedule(schedule: Schedule) {
        AlertDialog.Builder(this)
            .setTitle("Delete Schedule")
            .setMessage("Are you sure you want to delete this schedule?")
            .setPositiveButton("Delete") { _, _ ->
                db.collection("schedules").document(schedule.id)
                    .delete()
                    .addOnSuccessListener {
                        loadSchedules()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditScheduleDialog(schedule: Schedule) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_schedule, null)
        val editSubject = dialogView.findViewById<EditText>(R.id.editSubject)
        val editSection = dialogView.findViewById<EditText>(R.id.editSection)
        val editRoom = dialogView.findViewById<EditText>(R.id.editRoom)
        val spinnerDay = dialogView.findViewById<Spinner>(R.id.spinnerDay)
        val timePickerStart = dialogView.findViewById<TimePicker>(R.id.timePickerStart)
        val timePickerEnd = dialogView.findViewById<TimePicker>(R.id.timePickerEnd)

        // Set up Spinner adapter for days
        val daysOfWeek = resources.getStringArray(R.array.days_of_week)
        val adapter = ArrayAdapter(dialogView.context, android.R.layout.simple_spinner_item, daysOfWeek)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDay.adapter = adapter

        editSubject.setText(schedule.subject)
        editSection.setText(schedule.section)
        editRoom.setText(schedule.room)
        // Set spinner selection to match day
        val dayIndex = daysOfWeek.indexOf(schedule.day)
        if (dayIndex >= 0) spinnerDay.setSelection(dayIndex)
        // Set time pickers
        val (startHour, startMinute) = schedule.startTime.split(":").let {
            (it.getOrNull(0)?.toIntOrNull() ?: 7) to (it.getOrNull(1)?.toIntOrNull() ?: 0)
        }
        val (endHour, endMinute) = schedule.endTime.split(":").let {
            (it.getOrNull(0)?.toIntOrNull() ?: 8) to (it.getOrNull(1)?.toIntOrNull() ?: 0)
        }
        timePickerStart.hour = startHour
        timePickerStart.minute = startMinute
        timePickerEnd.hour = endHour
        timePickerEnd.minute = endMinute

        AlertDialog.Builder(this)
            .setTitle("Edit Schedule")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val updatedSubject = editSubject.text.toString()
                val updatedSection = editSection.text.toString()
                val updatedRoom = editRoom.text.toString()
                val updatedDay = spinnerDay.selectedItem?.toString() ?: schedule.day
                val updatedStart = "%02d:%02d".format(timePickerStart.hour, timePickerStart.minute)
                val updatedEnd = "%02d:%02d".format(timePickerEnd.hour, timePickerEnd.minute)
                db.collection("schedules").document(schedule.id)
                    .update(
                        mapOf(
                            "subject" to updatedSubject,
                            "section" to updatedSection,
                            "room" to updatedRoom,
                            "day" to updatedDay,
                            "startTime" to updatedStart,
                            "endTime" to updatedEnd
                        )
                    ).addOnSuccessListener { loadSchedules() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
