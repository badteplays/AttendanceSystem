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
                filteredSchedules
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
    private val items: List<TeacherScheduleRow>
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

