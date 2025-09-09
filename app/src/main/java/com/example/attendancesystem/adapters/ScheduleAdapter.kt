package com.example.attendancesystem.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.attendancesystem.R
import com.example.attendancesystem.models.Schedule

class ScheduleAdapter(
    private val schedules: List<Schedule>,
    private val onEditSchedule: (Schedule) -> Unit,
    private val onRemoveClass: (Schedule) -> Unit
) : RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_schedule, parent, false)
        return ScheduleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        val schedule = schedules[position]
        holder.bind(schedule)
        holder.btnEditSchedule.setOnClickListener { onEditSchedule(schedule) }
        holder.btnRemoveClass.setOnClickListener { onRemoveClass(schedule) }
    }

    override fun getItemCount(): Int = schedules.size

    class ScheduleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtSubject: TextView = itemView.findViewById(R.id.txtSubject)
        val txtSection: TextView = itemView.findViewById(R.id.txtSection)
        val txtDay: TextView = itemView.findViewById(R.id.txtDay)
        val txtTime: TextView = itemView.findViewById(R.id.txtTime)
        val txtRoom: TextView = itemView.findViewById(R.id.txtRoom)
        val btnEditSchedule: Button = itemView.findViewById(R.id.btnEditSchedule)
        val btnRemoveClass: View = itemView.findViewById(R.id.btnRemoveClass)

        fun bind(schedule: Schedule) {
            txtSubject.text = schedule.subject
            txtSection.text = "Section: ${schedule.section}"
            txtDay.text = "Day: ${schedule.day}"
            txtRoom.text = "Room: ${schedule.room}".takeIf { schedule.room.isNotBlank() } ?: ""

            fun formatTo12Hour(time: String): String {
                if (time.isBlank()) return ""
                val parts = time.split(":")
                if (parts.size != 2) return time
                val hour = parts[0].toIntOrNull() ?: return time
                val minute = parts[1].toIntOrNull() ?: 0
                val ampm = if (hour >= 12) "PM" else "AM"
                val hour12 = when {
                    hour == 0 -> 12
                    hour > 12 -> hour - 12
                    else -> hour
                }
                return String.format("%d:%02d %s", hour12, minute, ampm)
            }

            val start = formatTo12Hour(schedule.startTime)
            val end = formatTo12Hour(schedule.endTime)
            txtTime.text = when {
                start.isNotBlank() && end.isNotBlank() -> "Time: $start - $end"
                start.isNotBlank() -> "Time: $start"
                else -> "Time: -"
            }
        }
    }
}
