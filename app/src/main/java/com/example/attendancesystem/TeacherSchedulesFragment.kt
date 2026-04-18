package com.example.attendancesystem

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

class TeacherSchedulesFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: LinearLayout
    private lateinit var scheduleFormCard: MaterialCardView
    private lateinit var editSubject: TextInputEditText
    private lateinit var editSection: TextInputEditText
    private lateinit var editRoom: TextInputEditText
    private lateinit var addDayToggleGroup: MaterialButtonToggleGroup
    private lateinit var editStartTime: MaterialButton
    private lateinit var editEndTime: MaterialButton
    private lateinit var btnAddSchedule: MaterialButton
    private lateinit var btnCancelSchedule: MaterialButton
    private val allSchedules = arrayListOf<TeacherScheduleRow>()
    private val filteredSchedules = arrayListOf<TeacherScheduleRow>()
    private lateinit var adapter: TeacherSimpleScheduleAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var selectedDay = "Monday"
    private lateinit var dayTabs: List<TextView>

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
            editRoom = view.findViewById(R.id.editRoom)
            addDayToggleGroup = view.findViewById(R.id.addDayToggleGroup)
            editStartTime = view.findViewById(R.id.editStartTime)
            editEndTime = view.findViewById(R.id.editEndTime)
            btnAddSchedule = view.findViewById(R.id.btnAddSchedule)
            btnCancelSchedule = view.findViewById(R.id.btnCancelSchedule)

            dayTabs = listOf(
                view.findViewById(R.id.tabMon),
                view.findViewById(R.id.tabTue),
                view.findViewById(R.id.tabWed),
                view.findViewById(R.id.tabThu),
                view.findViewById(R.id.tabFri),
                view.findViewById(R.id.tabSat),
                view.findViewById(R.id.tabSun)
            )

            adapter = TeacherSimpleScheduleAdapter(
                filteredSchedules,
                onEdit = { row -> showEditDialog(row) },
                onDelete = { row -> confirmDelete(row) }
            )
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = adapter

            val todayIndex = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 0; Calendar.TUESDAY -> 1; Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3; Calendar.FRIDAY -> 4; Calendar.SATURDAY -> 5
                Calendar.SUNDAY -> 6; else -> 0
            }
            val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
            selectedDay = dayNames[todayIndex]

            setupDayTabs(dayNames)
            selectDayTab(todayIndex)
            loadHeaderName(view)
            selectAddDay(selectedDay)
            setupClickListeners()
            loadSchedules()
        } catch (e: Exception) {
            showEmptyState("Error loading schedules: ${e.message}")
        }
    }

    private fun loadHeaderName(view: View) {
        val textName = view.findViewById<TextView>(R.id.textName)
        val textInitials = view.findViewById<TextView>(R.id.textInitials)
        auth.currentUser?.let { user ->
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { doc ->
                    val name = doc?.getString("name") ?: "Teacher"
                    textName?.text = name
                    val initials = name.split(" ").filter { it.isNotBlank() }.take(2)
                        .joinToString("") { it.first().uppercaseChar().toString() }
                    textInitials?.text = initials.ifBlank { "TC" }
                }
        }
    }

    private fun setupDayTabs(dayNames: List<String>) {
        dayTabs.forEachIndexed { i, tab ->
            tab.setOnClickListener {
                selectedDay = dayNames[i]
                selectDayTab(i)
                filterSchedulesByDay()
            }
        }
    }

    private fun selectDayTab(index: Int) {
        dayTabs.forEachIndexed { i, tab ->
            if (i == index) {
                tab.setBackgroundResource(R.drawable.bg_teacher_day_tab_active)
                tab.setTextColor(ContextCompat.getColor(requireContext(), R.color.teacher_ds_primary_dark))
            } else {
                tab.setBackgroundColor(Color.TRANSPARENT)
                tab.setTextColor(Color.parseColor("#80FFFFFF"))
            }
        }
    }

    private fun filterSchedulesByDay() {
        filteredSchedules.clear()
        filteredSchedules.addAll(
            allSchedules.filter { it.day.equals(selectedDay, ignoreCase = true) }
                .sortedWith(compareBy({ it.subject.lowercase() }, { it.startTime }))
        )
        adapter.notifyDataSetChanged()
        recyclerView.visibility = if (filteredSchedules.isEmpty()) View.GONE else View.VISIBLE
        emptyState.visibility = if (filteredSchedules.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupClickListeners() {
        view?.findViewById<ExtendedFloatingActionButton>(R.id.fabAdd)?.setOnClickListener {
            if (scheduleFormCard.visibility == View.GONE) {
                scheduleFormCard.visibility = View.VISIBLE
            } else {
                scheduleFormCard.visibility = View.GONE
            }
        }

        view?.findViewById<View>(R.id.btnHeaderAdd)?.setOnClickListener {
            if (scheduleFormCard.visibility == View.GONE) {
                scheduleFormCard.visibility = View.VISIBLE
            } else {
                scheduleFormCard.visibility = View.GONE
            }
        }

        editStartTime.setOnClickListener {
            showTimePicker(selectedStartHour, selectedStartMinute, "Start time") { hour, minute ->
                selectedStartHour = hour
                selectedStartMinute = minute
                editStartTime.text = formatTimeTo12Hour(hour, minute)
            }
        }

        editEndTime.setOnClickListener {
            showTimePicker(selectedEndHour, selectedEndMinute, "End time") { hour, minute ->
                selectedEndHour = hour
                selectedEndMinute = minute
                editEndTime.text = formatTimeTo12Hour(hour, minute)
            }
        }

        btnAddSchedule.setOnClickListener { addSchedule() }
        btnCancelSchedule.setOnClickListener { hideScheduleForm() }
    }

    private fun showTimePicker(currentH: Int, currentM: Int, title: String, onTimeSelected: (Int, Int) -> Unit) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(if (currentH == 0 && currentM == 0) Calendar.getInstance().get(Calendar.HOUR_OF_DAY) else currentH)
            .setMinute(if (currentH == 0 && currentM == 0) Calendar.getInstance().get(Calendar.MINUTE) else currentM)
            .setTitleText(title)
            .setInputMode(MaterialTimePicker.INPUT_MODE_KEYBOARD)
            .build()
        picker.addOnPositiveButtonClickListener {
            onTimeSelected(picker.hour, picker.minute)
        }
        picker.show(childFragmentManager, title)
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
        val room = editRoom.text.toString().trim()
        val day = getSelectedAddDay()
        val startTime = editStartTime.text.toString().trim()
        val endTime = editEndTime.text.toString().trim()

        if (subject.isEmpty()) { Toast.makeText(requireContext(), "Please enter a subject", Toast.LENGTH_SHORT).show(); return }
        if (section.isEmpty()) { Toast.makeText(requireContext(), "Please enter a section", Toast.LENGTH_SHORT).show(); return }
        if (day.isEmpty()) { Toast.makeText(requireContext(), "Please select a day", Toast.LENGTH_SHORT).show(); return }
        if (startTime.isEmpty() || startTime.equals("Start time", ignoreCase = true)) {
            Toast.makeText(requireContext(), "Please select a start time", Toast.LENGTH_SHORT).show(); return
        }
        if (endTime.isEmpty() || endTime.equals("End time", ignoreCase = true)) {
            Toast.makeText(requireContext(), "Please select an end time", Toast.LENGTH_SHORT).show(); return
        }

        val currentUser = auth.currentUser ?: run {
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show(); return
        }

        val startTime24 = String.format("%02d:%02d", selectedStartHour, selectedStartMinute)
        val endTime24 = String.format("%02d:%02d", selectedEndHour, selectedEndMinute)

        val schedule = hashMapOf(
            "teacherId" to currentUser.uid,
            "subject" to subject,
            "section" to section.uppercase(),
            "room" to room.uppercase(),
            "day" to day,
            "startTime" to startTime24,
            "endTime" to endTime24,
            "lastGeneratedDate" to ""
        )

        db.collection("schedules")
            .add(schedule)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Schedule created", Toast.LENGTH_SHORT).show()
                hideScheduleForm()
                loadSchedules()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun hideScheduleForm() {
        scheduleFormCard.visibility = View.GONE
        editSubject.setText("")
        editSection.setText("")
        editRoom.setText("")
        selectAddDay("Monday")
        editStartTime.text = "Start time"
        editEndTime.text = "End time"
        selectedStartHour = 0; selectedStartMinute = 0
        selectedEndHour = 0; selectedEndMinute = 0
    }

    private fun getSelectedAddDay(): String {
        return when (addDayToggleGroup.checkedButtonId) {
            R.id.addDayMon -> "Monday"; R.id.addDayTue -> "Tuesday"; R.id.addDayWed -> "Wednesday"
            R.id.addDayThu -> "Thursday"; R.id.addDayFri -> "Friday"
            R.id.addDaySat -> "Saturday"; R.id.addDaySun -> "Sunday"
            else -> ""
        }
    }

    private fun selectAddDay(day: String) {
        val id = when (day.lowercase()) {
            "monday" -> R.id.addDayMon; "tuesday" -> R.id.addDayTue; "wednesday" -> R.id.addDayWed
            "thursday" -> R.id.addDayThu; "friday" -> R.id.addDayFri
            "saturday" -> R.id.addDaySat; "sunday" -> R.id.addDaySun
            else -> R.id.addDayMon
        }
        addDayToggleGroup.check(id)
    }

    fun loadSchedules() {
        progressBar.visibility = View.VISIBLE
        val user = auth.currentUser ?: return
        db.collection("schedules")
            .whereEqualTo("teacherId", user.uid)
            .get()
            .addOnSuccessListener { docs ->
                allSchedules.clear()
                for (d in docs) {
                    allSchedules.add(TeacherScheduleRow(
                        id = d.id,
                        subject = d.getString("subject") ?: "",
                        section = d.getString("section") ?: "",
                        day = d.getString("day") ?: "",
                        startTime = d.getString("startTime") ?: "",
                        endTime = d.getString("endTime") ?: "",
                        room = d.getString("room") ?: ""
                    ))
                }
                filterSchedulesByDay()
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                recyclerView.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
            }
    }

    private fun showEmptyState(message: String) {
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
    }
}

data class TeacherScheduleRow(
    val id: String,
    val subject: String,
    val section: String,
    val day: String,
    val startTime: String,
    val endTime: String,
    val room: String = ""
)

class TeacherSimpleScheduleAdapter(
    private val items: List<TeacherScheduleRow>,
    private val onEdit: (TeacherScheduleRow) -> Unit,
    private val onDelete: (TeacherScheduleRow) -> Unit
) : RecyclerView.Adapter<TeacherSimpleScheduleAdapter.VH>() {

    private val subjectColors = listOf("#1D9E75", "#378ADD", "#D85A30", "#BA7517", "#7F77DD", "#D4537E")
    private val subjectColorMap = mutableMapOf<String, String>()

    private fun getSubjectColor(subject: String): String {
        return subjectColorMap.getOrPut(subject) {
            subjectColors[subjectColorMap.size % subjectColors.size]
        }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val colorBar: View = v.findViewById(R.id.colorBar)
        val subject: TextView = v.findViewById(R.id.subjectText)
        val section: TextView = v.findViewById(R.id.sectionText)
        val day: TextView = v.findViewById(R.id.dayText)
        val time: TextView = v.findViewById(R.id.timeText)
        val room: TextView = v.findViewById(R.id.roomText)
        val btnEdit: View = v.findViewById(R.id.btnEdit)
        val btnDelete: View = v.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_simple_schedule, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        val color = Color.parseColor(getSubjectColor(s.subject))

        holder.subject.text = s.subject
        holder.subject.setTextColor(color)
        holder.section.text = s.section.uppercase()
        holder.day.text = s.day.take(3)
        holder.time.text = "${format24To12(s.startTime)} – ${format24To12(s.endTime)}"

        if (s.room.isNotBlank()) {
            holder.room.text = s.room.uppercase()
            holder.room.visibility = View.VISIBLE
        } else {
            holder.room.visibility = View.GONE
        }

        val barBg = GradientDrawable().apply {
            setColor(color)
            cornerRadius = 6f
        }
        holder.colorBar.background = barBg

        holder.section.setTextColor(color)
        val pillBg = GradientDrawable().apply {
            setColor(Color.argb(30, Color.red(color), Color.green(color), Color.blue(color)))
            cornerRadius = 40f
        }
        holder.section.background = pillBg

        holder.btnEdit.setOnClickListener { onEdit(items[holder.bindingAdapterPosition]) }
        holder.btnDelete.setOnClickListener { onDelete(items[holder.bindingAdapterPosition]) }
    }

    override fun getItemCount(): Int = items.size
}

private fun format24To12(time24: String): String {
    return try {
        val parts = time24.split(":")
        if (parts.size != 2) return time24
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()
        val amPm = if (hour < 12) "AM" else "PM"
        val hour12 = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
        String.format("%d:%02d %s", hour12, minute, amPm)
    } catch (_: Exception) { time24 }
}

private fun TeacherSchedulesFragment.confirmDelete(row: TeacherScheduleRow) {
    androidx.appcompat.app.AlertDialog.Builder(requireContext())
        .setTitle("Remove ${row.subject}?")
        .setMessage("${row.section} · ${row.day}")
        .setPositiveButton("Yes, remove") { _, _ ->
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
    val editDayToggleGroup = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.editDayToggleGroup)
    val btnStartTime = dialogView.findViewById<MaterialButton>(R.id.btnStartTime)
    val btnEndTime = dialogView.findViewById<MaterialButton>(R.id.btnEndTime)

    subjectInput.setText(row.subject)
    sectionInput.setText(row.section)
    roomInput?.setText(row.room)
    fun selectedEditDay(): String {
        return when (editDayToggleGroup.checkedButtonId) {
            R.id.editDayMon -> "Monday"; R.id.editDayTue -> "Tuesday"; R.id.editDayWed -> "Wednesday"
            R.id.editDayThu -> "Thursday"; R.id.editDayFri -> "Friday"
            R.id.editDaySat -> "Saturday"; R.id.editDaySun -> "Sunday"
            else -> ""
        }
    }
    val editDayId = when (row.day.lowercase()) {
        "monday" -> R.id.editDayMon; "tuesday" -> R.id.editDayTue; "wednesday" -> R.id.editDayWed
        "thursday" -> R.id.editDayThu; "friday" -> R.id.editDayFri
        "saturday" -> R.id.editDaySat; "sunday" -> R.id.editDaySun
        else -> R.id.editDayMon
    }
    editDayToggleGroup.check(editDayId)

    var editStartH = row.startTime.split(":").getOrNull(0)?.toIntOrNull() ?: 8
    var editStartM = row.startTime.split(":").getOrNull(1)?.toIntOrNull() ?: 0
    var editEndH = row.endTime.split(":").getOrNull(0)?.toIntOrNull() ?: 9
    var editEndM = row.endTime.split(":").getOrNull(1)?.toIntOrNull() ?: 0

    fun fmt12(h: Int, m: Int): String {
        val amPm = if (h < 12) "AM" else "PM"
        val dh = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
        return String.format("%d:%02d %s", dh, m, amPm)
    }
    btnStartTime.text = fmt12(editStartH, editStartM)
    btnEndTime.text = fmt12(editEndH, editEndM)

    btnStartTime.setOnClickListener {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(editStartH).setMinute(editStartM)
            .setTitleText("Start time")
            .setInputMode(MaterialTimePicker.INPUT_MODE_KEYBOARD)
            .build()
        picker.addOnPositiveButtonClickListener {
            editStartH = picker.hour; editStartM = picker.minute
            btnStartTime.text = fmt12(picker.hour, picker.minute)
        }
        picker.show(childFragmentManager, "editStart")
    }
    btnEndTime.setOnClickListener {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(editEndH).setMinute(editEndM)
            .setTitleText("End time")
            .setInputMode(MaterialTimePicker.INPUT_MODE_KEYBOARD)
            .build()
        picker.addOnPositiveButtonClickListener {
            editEndH = picker.hour; editEndM = picker.minute
            btnEndTime.text = fmt12(picker.hour, picker.minute)
        }
        picker.show(childFragmentManager, "editEnd")
    }

    val dlg = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        .setView(dialogView)
        .create()

    dialogView.findViewById<MaterialButton>(R.id.btnCancel)?.setOnClickListener { dlg.dismiss() }
    dialogView.findViewById<MaterialButton>(R.id.btnSave)?.setOnClickListener {
        val subject = subjectInput.text?.toString()?.trim().orEmpty()
        val section = sectionInput.text?.toString()?.trim().orEmpty()
        val room = roomInput?.text?.toString()?.trim().orEmpty()
        val day = selectedEditDay()
        val start24 = String.format("%02d:%02d", editStartH, editStartM)
        val end24 = String.format("%02d:%02d", editEndH, editEndM)

        if (subject.isBlank() || section.isBlank() || day.isBlank()) {
            Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
            return@setOnClickListener
        }

        FirebaseFirestore.getInstance().collection("schedules").document(row.id)
            .update(mapOf("subject" to subject, "section" to section.uppercase(), "room" to room.uppercase(), "day" to day, "startTime" to start24, "endTime" to end24))
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
