package com.example.attendancesystem

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
// import com.example.attendancesystem.databinding.ActivityScheduleBinding
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.attendancesystem.adapters.ScheduleAdapter
import com.example.attendancesystem.models.Schedule
import java.text.SimpleDateFormat
import java.util.*

class ScheduleActivity : AppCompatActivity() {
    // private lateinit var binding: ActivityScheduleBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var recyclerSchedules: RecyclerView
    private lateinit var adapter: ScheduleAdapter
    private val scheduleList = mutableListOf<Schedule>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // binding = ActivityScheduleBinding.inflate(layoutInflater)
        setContentView(R.layout.activity_schedule)

        // Remove the btnBack reference since it doesn't exist in the layout
        // Back navigation is handled by the toolbar's navigation icon
        
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        recyclerSchedules = findViewById(R.id.recyclerSchedules)
        recyclerSchedules.layoutManager = LinearLayoutManager(this)
        adapter = ScheduleAdapter(scheduleList,
            onEditSchedule = { },
            onRemoveClass = { schedule -> showRemoveScheduleDialog(schedule) }
        )
        recyclerSchedules.adapter = adapter

        findViewById<Button>(R.id.btnAddSchedule).setOnClickListener {
            val subject = findViewById<EditText>(R.id.editSubject).text.toString().trim()
            val section = findViewById<EditText>(R.id.editSection).text.toString().trim()
            val day = findViewById<EditText>(R.id.editDay).text.toString().trim()
            val startTime = findViewById<EditText>(R.id.editStartTime).text.toString().trim()
            val endTime = findViewById<EditText>(R.id.editEndTime).text.toString().trim()

            if (subject.isEmpty() || section.isEmpty() || day.isEmpty() || startTime.isEmpty() || endTime.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val schedule = hashMapOf(
                "teacherId" to auth.currentUser?.uid,
                "subject" to subject,
                "section" to section,
                "day" to day,
                "startTime" to startTime,
                "endTime" to endTime,
                "lastGeneratedDate" to ""
            )

            db.collection("schedules")
                .add(schedule)
                .addOnSuccessListener {
                    Toast.makeText(this, "Schedule added!", Toast.LENGTH_SHORT).show()
                    fetchSchedules()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        fetchSchedules()
        checkAndAutoGenerateQR()
    }

    private fun fetchSchedules() {
        db.collection("schedules")
            .whereEqualTo("teacherId", auth.currentUser?.uid)
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
                        lastGeneratedDate = doc.getString("lastGeneratedDate") ?: ""
                    )
                    scheduleList.add(schedule)
                }
                adapter.notifyDataSetChanged()
            }
    }

    private fun generateQRCodeForSchedule(schedule: Schedule) {
        // TODO: Implement QR code generation logic, reuse MainActivity's logic if needed
        Toast.makeText(this, "Generated QR for ${schedule.subject}", Toast.LENGTH_SHORT).show()
        // After generating, update lastGeneratedDate
        val today = dateFormat.format(Date())
        db.collection("schedules").document(schedule.id)
            .update("lastGeneratedDate", today)
    }

    private fun checkAndAutoGenerateQR() {
        val calendar = Calendar.getInstance()
        val today = dateFormat.format(calendar.time)
        val dayOfWeek = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()) ?: ""
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.time)

        db.collection("schedules")
            .whereEqualTo("teacherId", auth.currentUser?.uid)
            .whereEqualTo("day", dayOfWeek)
            .get()
            .addOnSuccessListener { result ->
                for (doc in result) {
                    val startTime = doc.getString("startTime") ?: continue
                    val lastGeneratedDate = doc.getString("lastGeneratedDate") ?: ""
                    if (currentTime >= startTime && lastGeneratedDate != today) {
                        val schedule = Schedule(
                            id = doc.id,
                            subject = doc.getString("subject") ?: "",
                            section = doc.getString("section") ?: "",
                            teacherId = doc.getString("teacherId") ?: "",
                            startTime = startTime,
                            endTime = doc.getString("endTime") ?: "",
                            day = doc.getString("day") ?: "",
                            lastGeneratedDate = lastGeneratedDate
                        )
                        generateQRCodeForSchedule(schedule)
                        Toast.makeText(this, "Auto-generated QR for ${schedule.subject}", Toast.LENGTH_LONG).show()
                    }
                }
            }
    }

    private fun showRemoveScheduleDialog(schedule: Schedule) {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_remove_class_title)
            .setMessage(getString(R.string.confirm_remove_class_message, schedule.subject, schedule.section))
            .setPositiveButton(R.string.remove) { _, _ ->
                removeSchedule(schedule)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun removeSchedule(schedule: Schedule) {
        db.collection("schedules")
            .document(schedule.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, getString(R.string.class_removed), Toast.LENGTH_SHORT).show()
                fetchSchedules()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, getString(R.string.error_removing_class, e.message), Toast.LENGTH_SHORT).show()
            }
    }
}
