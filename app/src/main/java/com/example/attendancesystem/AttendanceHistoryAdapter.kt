package com.example.attendancesystem

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AttendanceHistoryAdapter(private val items: List<AttendanceHistoryItem>) :
    RecyclerView.Adapter<AttendanceHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dateText: TextView = view.findViewById(R.id.dateText)
        val subjectText: TextView = view.findViewById(R.id.subjectText)
        val statusText: TextView = view.findViewById(R.id.statusText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.dateText.text = item.date
        holder.subjectText.text = item.subject
        holder.statusText.text = item.status
        holder.statusText.setTextColor(
            if (item.status == "Present") android.graphics.Color.GREEN
            else android.graphics.Color.RED
        )
    }

    override fun getItemCount() = items.size
} 