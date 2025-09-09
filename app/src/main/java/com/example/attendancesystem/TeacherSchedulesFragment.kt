package com.example.attendancesystem

import android.content.Intent
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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class TeacherSchedulesFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: LinearLayout
    private lateinit var textTotalSchedules: TextView
    private val schedules = arrayListOf<TeacherScheduleRow>()
    private lateinit var adapter: TeacherSimpleScheduleAdapter
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_teacher_schedules, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.recyclerView)
        progressBar = view.findViewById(R.id.progressBar)
        emptyState = view.findViewById(R.id.emptyState)
        textTotalSchedules = view.findViewById(R.id.textTotalSchedules)
        adapter = TeacherSimpleScheduleAdapter(schedules)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        view.findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            startActivity(Intent(requireContext(), TeacherScheduleActivity::class.java))
        }

        loadSchedules()
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
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                recyclerView.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
            }
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


