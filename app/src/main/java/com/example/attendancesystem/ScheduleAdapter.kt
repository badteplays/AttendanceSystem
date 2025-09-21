package com.example.attendancesystem

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Data class for a schedule item
 data class ScheduleItem(
    val id: String = "",
    val subject: String = "",
    val section: String = "",
    val day: String = "",
    val time: String = "",
    val room: String = ""
)

class ScheduleAdapter(
    private var schedules: List<ScheduleItem>,
    private val onGenerateQR: (ScheduleItem) -> Unit,
    private val onRemove: (ScheduleItem) -> Unit,
    private val onEdit: (ScheduleItem) -> Unit
) : RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder>() {

    inner class ScheduleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtSubject: TextView = itemView.findViewById(R.id.txtSubject)
        val txtSection: TextView = itemView.findViewById(R.id.txtSection)
        val txtDay: TextView = itemView.findViewById(R.id.txtDay)
        val txtTime: TextView = itemView.findViewById(R.id.txtTime)
        val btnRemoveClass: Button = itemView.findViewById(R.id.btnRemoveClass)
        val btnEditSchedule: Button? = itemView.findViewById(R.id.btnEditSchedule)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_schedule, parent, false)
        return ScheduleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        val schedule = schedules[position]
        holder.txtSubject.text = schedule.subject
        holder.txtSection.text = schedule.section
        holder.txtDay.text = schedule.day
        holder.txtTime.text = schedule.time
        holder.btnRemoveClass.setOnClickListener { onRemove(schedule) }
        holder.btnEditSchedule?.setOnClickListener { onEdit(schedule) }
        holder.itemView.setOnClickListener { onEdit(schedule) }
    }

    override fun getItemCount(): Int = schedules.size

    fun updateSchedules(newSchedules: List<ScheduleItem>) {
        this.schedules = newSchedules
        notifyDataSetChanged()
    }
}
