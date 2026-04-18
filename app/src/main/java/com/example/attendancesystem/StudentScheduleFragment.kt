package com.example.attendancesystem

import android.app.DatePickerDialog
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
import com.example.attendancesystem.models.StudentScheduleItem
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StudentScheduleFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: LinearLayout
    private lateinit var textTotalClasses: TextView
    private lateinit var textTodayClasses: TextView
    private lateinit var todayButton: MaterialButton
    private lateinit var monthButton: MaterialButton
    private lateinit var dayToggleGroup: MaterialButtonToggleGroup
    private lateinit var adapter: StudentScheduleAdapter
    private val scheduleList = ArrayList<StudentScheduleItem>()
    private val db = FirebaseFirestore.getInstance()
    private val currentUser = FirebaseAuth.getInstance().currentUser
    private var studentSection: String = ""
    private val selectedDate = Calendar.getInstance()
    private val dayButtons = mutableListOf<MaterialButton>()
    private val dayCalendars = mutableListOf<Calendar>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_student_schedule, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            initializeViews(view)
            setupRecyclerView()
            setupDateControls()
            showEmptyState("Schedule loading...")
            loadStudentSection()
        } catch (e: Exception) {
            showEmptyState("Error loading schedule: ${e.message}")
        }
    }

    private fun initializeViews(view: View) {
        try {
            recyclerView = view.findViewById(R.id.studentScheduleRecyclerView)
            progressBar = view.findViewById(R.id.progressBar)
            emptyState = view.findViewById(R.id.emptyState)
            textTotalClasses = view.findViewById(R.id.textTotalClasses)
            textTodayClasses = view.findViewById(R.id.textTodayClasses)
            todayButton = view.findViewById(R.id.todayButton)
            monthButton = view.findViewById(R.id.monthButton)
            dayToggleGroup = view.findViewById(R.id.dayToggleGroup)
            dayButtons.clear()
            dayButtons.add(view.findViewById(R.id.daySun))
            dayButtons.add(view.findViewById(R.id.dayMon))
            dayButtons.add(view.findViewById(R.id.dayTue))
            dayButtons.add(view.findViewById(R.id.dayWed))
            dayButtons.add(view.findViewById(R.id.dayThu))
            dayButtons.add(view.findViewById(R.id.dayFri))
            dayButtons.add(view.findViewById(R.id.daySat))
        } catch (e: Exception) {
            throw e
        }
    }

    private fun setupRecyclerView() {
        try {
            adapter = StudentScheduleAdapter(scheduleList)
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = adapter
        } catch (e: Exception) {
            throw e
        }
    }

    private fun setupDateControls() {
        todayButton.setOnClickListener {
            selectedDate.timeInMillis = System.currentTimeMillis()
            updateDateUi()
            loadStudentSchedules()
        }
        monthButton.setOnClickListener {
            showDatePicker()
        }
        dayToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val index = dayButtons.indexOfFirst { it.id == checkedId }
            if (index >= 0 && index < dayCalendars.size) {
                selectedDate.time = dayCalendars[index].time
                updateDateUi()
                loadStudentSchedules()
            }
        }
        updateDateUi()
    }

    private fun showDatePicker() {
        val y = selectedDate.get(Calendar.YEAR)
        val m = selectedDate.get(Calendar.MONTH)
        val d = selectedDate.get(Calendar.DAY_OF_MONTH)
        DatePickerDialog(requireContext(), { _, year, month, day ->
            selectedDate.set(Calendar.YEAR, year)
            selectedDate.set(Calendar.MONTH, month)
            selectedDate.set(Calendar.DAY_OF_MONTH, day)
            updateDateUi()
            loadStudentSchedules()
        }, y, m, d).show()
    }

    private fun updateDateUi() {
        monthButton.text = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(selectedDate.time)
        dayCalendars.clear()

        val weekStart = Calendar.getInstance().apply {
            time = selectedDate.time
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        }
        dayButtons.forEachIndexed { index, button ->
            val day = Calendar.getInstance().apply {
                time = weekStart.time
                add(Calendar.DAY_OF_YEAR, index)
            }
            dayCalendars.add(day)
            button.text = day.get(Calendar.DAY_OF_MONTH).toString()
        }
        val selectedIndex = (selectedDate.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY).coerceIn(0, 6)
        dayButtons.getOrNull(selectedIndex)?.let {
            dayToggleGroup.check(it.id)
        }
    }

    private fun loadStudentSection() {
        if (currentUser == null) {
            showEmptyState("User not authenticated")
            return
        }

        try {
            db.collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        studentSection = document.getString("section") ?: ""
                        if (studentSection.isNotEmpty()) {
                            loadStudentSchedules()
                        } else {
                            showEmptyState("No section assigned")
                        }
                    } else {
                        showEmptyState("User data not found")
                    }
                }
                .addOnFailureListener { e ->
                    showEmptyState("Error loading user data: ${e.message}")
                }
        } catch (e: Exception) {
            showEmptyState("Error: ${e.message}")
        }
    }

    private fun loadStudentSchedules() {
        progressBar.visibility = View.VISIBLE

        try {
            val normalizedSection = studentSection.trim().uppercase()
            val selectedDay = getSelectedDayOfWeek()
            db.collection("schedules")
                .whereEqualTo("section", normalizedSection)
                .get()
                .addOnSuccessListener { documents ->
                    if (documents.isEmpty) {
                        tryLowercaseQuery(normalizedSection.lowercase(), selectedDay)
                    } else {
                        processScheduleDocuments(documents, selectedDay)
                    }
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    showEmptyState("Error loading schedules: ${e.message}")
                }
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            showEmptyState("Error: ${e.message}")
        }
    }

    private fun tryLowercaseQuery(lowercaseSection: String, selectedDay: String) {
        db.collection("schedules")
            .whereEqualTo("section", lowercaseSection)
            .get()
            .addOnSuccessListener { documents ->
                processScheduleDocuments(documents, selectedDay)
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                showEmptyState("Error loading schedules: ${e.message}")
            }
    }

    private fun processScheduleDocuments(documents: com.google.firebase.firestore.QuerySnapshot, selectedDay: String) {
        val daySchedules = documents.documents.filter {
            it.getString("day")?.equals(selectedDay, ignoreCase = true) == true
        }
        if (daySchedules.isEmpty()) {
            scheduleList.clear()
            adapter.notifyDataSetChanged()
            showEmptyState("No classes for ${selectedDay.lowercase().replaceFirstChar { c -> c.uppercase() }}")
            return
        }

        val dayStart = Calendar.getInstance().apply {
            time = selectedDate.time
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dayEnd = Calendar.getInstance().apply {
            time = dayStart.time
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val dayStartTs = Timestamp(dayStart.time)
        val dayEndTs = Timestamp(dayEnd.time)
        val uid = currentUser?.uid
        if (uid == null) {
            renderSchedules(daySchedules, emptyMap())
            return
        }

        db.collection("attendance")
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { attendanceDocs ->
                val statusBySchedule = mutableMapOf<String, String>()
                attendanceDocs.documents.forEach { doc ->
                    val ts = doc.getTimestamp("timestamp") ?: return@forEach
                    if (ts < dayStartTs || ts >= dayEndTs) return@forEach
                    val scheduleId = doc.getString("scheduleId") ?: return@forEach
                    val status = doc.getString("status")?.uppercase() ?: "PRESENT"
                    statusBySchedule[scheduleId] = status
                }
                renderSchedules(daySchedules, statusBySchedule)
            }
            .addOnFailureListener {
                renderSchedules(daySchedules, emptyMap())
            }
    }

    private fun showEmptyState(message: String) {
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        progressBar.visibility = View.GONE

        textTotalClasses.text = "0"
        textTodayClasses.text = "0"

        val emptyMessage = emptyState.findViewById<TextView>(R.id.emptyMessage)
        emptyMessage?.text = message
    }

    private fun renderSchedules(
        daySchedules: List<com.google.firebase.firestore.DocumentSnapshot>,
        statusBySchedule: Map<String, String>
    ) {
        scheduleList.clear()
        daySchedules.forEach { document ->
            val subject = document.getString("subject") ?: ""
            val section = document.getString("section") ?: ""
            val room = document.getString("room") ?: ""
            val day = document.getString("day") ?: ""
            val startTime = document.getString("startTime") ?: ""
            val endTime = document.getString("endTime") ?: ""
            val notes = document.getString("notes") ?: ""
            val teacherId = document.getString("teacherId") ?: ""
            val dbStatus = statusBySchedule[document.id]

            scheduleList.add(
                StudentScheduleItem(
                    id = document.id,
                    subject = subject,
                    section = section,
                    room = room,
                    day = day,
                    time = convertTo12HourFormat(startTime, endTime),
                    startTime = startTime,
                    endTime = endTime,
                    notes = notes,
                    teacherId = teacherId,
                    attendanceStatus = resolveScheduleStatus(dbStatus, startTime)
                )
            )
        }
        scheduleList.sortBy { parseTimeToMinutes(it.startTime) }
        recyclerView.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        textTodayClasses.text = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(selectedDate.time)
        textTotalClasses.text = if (scheduleList.size == 1) "1 class" else "${scheduleList.size} classes"
        adapter.notifyDataSetChanged()
        progressBar.visibility = View.GONE
    }

    private fun resolveScheduleStatus(dbStatus: String?, startTime: String): String {
        if (!dbStatus.isNullOrBlank()) {
            return when (dbStatus.uppercase()) {
                "PRESENT", "LATE", "EXCUSED" -> "ATTENDED"
                "ABSENT", "CUTTING" -> "ABSENT"
                else -> "SCHEDULED"
            }
        }
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val selectedStart = Calendar.getInstance().apply {
            time = selectedDate.time
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return when {
            selectedStart.after(todayStart) -> "SCHEDULED"
            selectedStart.before(todayStart) -> "ABSENT"
            else -> {
                if (parseTimeToMinutes(startTime) > currentTimeInMinutes()) "SCHEDULED" else "ABSENT"
            }
        }
    }

    private fun getSelectedDayOfWeek(): String {
        val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
        return dayFormat.format(selectedDate.time)
    }

    private fun currentTimeInMinutes(): Int {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }

    private fun convertTo12HourFormat(startTime: String, endTime: String): String {
        return try {
            val inputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val outputFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

            val startDate = inputFormat.parse(startTime)
            val endDate = inputFormat.parse(endTime)

            val formattedStart = outputFormat.format(startDate ?: Date())
            val formattedEnd = outputFormat.format(endDate ?: Date())

            "$formattedStart - $formattedEnd"
        } catch (e: Exception) {
            "$startTime - $endTime"
        }
    }

    private fun parseTimeToMinutes(time: String): Int {
        return try {
            val parts = time.split(":")
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            hour * 60 + minute
        } catch (e: Exception) {
            0
        }
    }
}

