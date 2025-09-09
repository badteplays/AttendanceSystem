package com.example.attendancesystem

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class StudentScheduleAdapter(
    private var schedules: List<StudentScheduleItem>
) : RecyclerView.Adapter<StudentScheduleAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val subjectText: TextView = view.findViewById(R.id.subjectText)
        val dayText: TextView = view.findViewById(R.id.dayText)
        val timeText: TextView = view.findViewById(R.id.timeText)
        val sectionText: TextView = view.findViewById(R.id.sectionText)
        val notesText: TextView = view.findViewById(R.id.notesText)
        val statusIndicator: View = view.findViewById(R.id.statusIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_schedule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val schedule = schedules[position]
        
        holder.subjectText.text = schedule.subject
        holder.dayText.text = schedule.day
        holder.timeText.text = schedule.time
        holder.sectionText.text = "Section: ${schedule.section.uppercase()}"
        
        // Show notes if available
        if (schedule.notes.isNotEmpty()) {
            holder.notesText.text = schedule.notes
            holder.notesText.visibility = View.VISIBLE
        } else {
            holder.notesText.visibility = View.GONE
        }
        
        // Set status indicator based on whether class is today and current time
        val isToday = isScheduleToday(schedule.day)
        val isCurrentTime = isCurrentTime(schedule.startTime, schedule.endTime)
        
        when {
            isToday && isCurrentTime -> {
                // Class is happening now - green
                holder.statusIndicator.setBackgroundResource(R.drawable.status_active_background)
            }
            isToday -> {
                // Class is today but not current - blue
                holder.statusIndicator.setBackgroundResource(R.drawable.status_today_background)
            }
            else -> {
                // Regular class - gray
                holder.statusIndicator.setBackgroundResource(R.drawable.status_default_background)
            }
        }
    }

    override fun getItemCount() = schedules.size

    private fun isScheduleToday(scheduleDay: String): Boolean {
        val calendar = Calendar.getInstance()
        val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
        val today = dayFormat.format(calendar.time)
        return scheduleDay.equals(today, ignoreCase = true)
    }

    private fun isCurrentTime(startTime: String, endTime: String): Boolean {
        return try {
            val currentTime = Calendar.getInstance()
            val currentHour = currentTime.get(Calendar.HOUR_OF_DAY)
            val currentMinute = currentTime.get(Calendar.MINUTE)
            val currentTimeInMinutes = currentHour * 60 + currentMinute
            
            val startParts = startTime.split(":")
            val startHour = startParts[0].toInt()
            val startMinute = startParts[1].toInt()
            val startTimeInMinutes = startHour * 60 + startMinute
            
            val endParts = endTime.split(":")
            val endHour = endParts[0].toInt()
            val endMinute = endParts[1].toInt()
            val endTimeInMinutes = endHour * 60 + endMinute
            
            currentTimeInMinutes in startTimeInMinutes..endTimeInMinutes
        } catch (e: Exception) {
            false
        }
    }

    fun updateSchedules(newSchedules: List<StudentScheduleItem>) {
        schedules = newSchedules
        notifyDataSetChanged()
    }
}
