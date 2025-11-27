package com.example.attendancesystem

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.attendancesystem.databinding.ItemStudentScheduleBinding
import com.example.attendancesystem.models.StudentScheduleItem

class StudentScheduleAdapter(
    private val schedules: List<StudentScheduleItem>
) : RecyclerView.Adapter<StudentScheduleAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemStudentScheduleBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStudentScheduleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val schedule = schedules[position]
        holder.binding.textScheduleName.text = schedule.subject
        holder.binding.textScheduleTime.text = "${schedule.day} ${schedule.time}"
        holder.binding.textScheduleLocation.text = schedule.section
    }

    override fun getItemCount() = schedules.size
}