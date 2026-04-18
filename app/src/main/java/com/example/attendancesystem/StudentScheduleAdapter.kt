package com.example.attendancesystem

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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
        holder.binding.textScheduleLocation.text = if (schedule.room.isNotBlank()) "${schedule.section} · Room ${schedule.room}" else schedule.section
        holder.binding.textScheduleTime.text = schedule.time

        val context = holder.itemView.context
        when (schedule.attendanceStatus.uppercase()) {
            "ATTENDED", "PRESENT", "LATE", "EXCUSED" -> {
                holder.binding.statusText.text = "Attended"
                holder.binding.statusIcon.setImageResource(R.drawable.ic_check_circle)
                holder.binding.statusIcon.setColorFilter(ContextCompat.getColor(context, R.color.status_present))
                holder.binding.statusIndicator.setBackgroundResource(R.drawable.bg_schedule_status_present)
            }
            "ABSENT", "CUTTING" -> {
                holder.binding.statusText.text = "Absent"
                holder.binding.statusIcon.setImageResource(R.drawable.ic_absent)
                holder.binding.statusIcon.setColorFilter(ContextCompat.getColor(context, R.color.status_absent))
                holder.binding.statusIndicator.setBackgroundResource(R.drawable.bg_schedule_status_absent)
            }
            else -> {
                holder.binding.statusText.text = "Scheduled"
                holder.binding.statusIcon.setImageResource(R.drawable.ic_checklist)
                holder.binding.statusIcon.setColorFilter(ContextCompat.getColor(context, R.color.primary_light))
                holder.binding.statusIndicator.setBackgroundResource(R.drawable.bg_schedule_status_scheduled)
            }
        }
    }

    override fun getItemCount() = schedules.size
}