package com.example.attendancesystem.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.attendancesystem.R
import com.example.attendancesystem.models.Schedule

class ScheduleAttendanceAdapter(
    private val schedules: List<Schedule>,
    private val onScheduleClick: (Schedule) -> Unit
) : RecyclerView.Adapter<ScheduleAttendanceAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textSubject: TextView = itemView.findViewById(R.id.textSubject)
        val textSection: TextView = itemView.findViewById(R.id.textSection)
        val textTime: TextView = itemView.findViewById(R.id.textTime)
        val textDay: TextView = itemView.findViewById(R.id.textDay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule_attendance, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val schedule = schedules[position]
        
        holder.textSubject.text = schedule.subject
        holder.textSection.text = schedule.section
        holder.textTime.text = "${schedule.startTime} - ${schedule.endTime}"
        holder.textDay.text = schedule.day
        
        holder.itemView.setOnClickListener {
            onScheduleClick(schedule)
        }
    }

    override fun getItemCount() = schedules.size
}
