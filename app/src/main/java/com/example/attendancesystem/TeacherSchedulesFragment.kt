package com.example.attendancesystem

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

class TeacherSchedulesFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: LinearLayout
    private lateinit var textTotalSchedules: TextView
    private lateinit var scheduleFormCard: MaterialCardView
    private lateinit var editSubject: TextInputEditText
    private lateinit var editSection: TextInputEditText
    private lateinit var editDay: AutoCompleteTextView
    private lateinit var editStartTime: TextInputEditText
    private lateinit var editEndTime: TextInputEditText
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
            textTotalSchedules = view.findViewById(R.id.textTotalSchedules)
            scheduleFormCard = view.findViewById(R.id.scheduleFormCard)
            editSubject = view.findViewById(R.id.editSubject)
            editSection = view.findViewById(R.id.editSection)
            editDay = view.findViewById(R.id.editDay)
            editStartTime = view.findViewById(R.id.editStartTime)
            editEndTime = view.findViewById(R.id.editEndTime)
            btnAddSchedule = view.findViewById(R.id.btnAddSchedule)
            btnCancelSchedule = view.findViewById(R.id.btnCancelSchedule)
            
            adapter = TeacherSimpleScheduleAdapter(schedules)
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
        
        // Add Schedule button
        btnAddSchedule.setOnClickListener {
            addSchedule()
        }
        
        // Cancel Schedule button
        btnCancelSchedule.setOnClickListener {
            hideScheduleForm()
        }
    }
    
    private fun addSchedule() {
        val subject = editSubject.text.toString().trim()
        val section = editSection.text.toString().trim()
        val day = editDay.text.toString().trim()
        val startTime = editStartTime.text.toString().trim()
        val endTime = editEndTime.text.toString().trim()

        if (subject.isEmpty() || section.isEmpty() || day.isEmpty() || startTime.isEmpty() || endTime.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val schedule = hashMapOf(
            "teacherId" to currentUser.uid,
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
                Toast.makeText(requireContext(), "Schedule added!", Toast.LENGTH_SHORT).show()
                hideScheduleForm()
                loadSchedules() // Refresh the list
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to add schedule: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun hideScheduleForm() {
        scheduleFormCard.visibility = View.GONE
        // Clear form fields
        editSubject.setText("")
        editSection.setText("")
        editDay.setText("")
        editStartTime.setText("")
        editEndTime.setText("")
    }

    private fun loadSchedules() {
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
                            subject = d.getString("subject") ?: "",
                            section = d.getString("section") ?: "",
                            day = d.getString("day") ?: "",
                            startTime = d.getString("startTime") ?: "",
                            endTime = d.getString("endTime") ?: ""
                        )
                    )
                }
                textTotalSchedules.text = schedules.size.toString()
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
        textTotalSchedules.text = "0"
        
        // Update empty state message if needed
        val emptyMessage = emptyState.findViewById<TextView>(android.R.id.text1)
        emptyMessage?.text = message
    }
}

data class TeacherScheduleRow(
    val subject: String,
    val section: String,
    val day: String,
    val startTime: String,
    val endTime: String
)

class TeacherSimpleScheduleAdapter(private val items: List<TeacherScheduleRow>) : RecyclerView.Adapter<TeacherSimpleScheduleAdapter.VH>() {
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
        holder.time.text = "${s.startTime} - ${s.endTime}"
    }

    override fun getItemCount(): Int = items.size
}


