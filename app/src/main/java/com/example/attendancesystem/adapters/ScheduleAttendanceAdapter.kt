package com.example.attendancesystem.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.attendancesystem.databinding.ItemScheduleAttendanceBinding
import com.example.attendancesystem.models.Schedule

class ScheduleAttendanceAdapter(
    private val schedules: List<Schedule>,
    private val onItemClick: (Schedule) -> Unit
) : RecyclerView.Adapter<ScheduleAttendanceAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemScheduleAttendanceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScheduleAttendanceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val schedule = schedules[position]
        holder.binding.textScheduleName.text = schedule.subject
        holder.binding.textScheduleTime.text = "${schedule.startTime} - ${schedule.endTime}"
        holder.binding.textScheduleLocation.text = schedule.section
        holder.itemView.setOnClickListener { onItemClick(schedule) }
    }

    override fun getItemCount() = schedules.size
}