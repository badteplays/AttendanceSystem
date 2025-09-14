package com.example.attendancesystem

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.attendancesystem.databinding.ItemAttendanceHistoryBinding
import com.example.attendancesystem.models.AttendanceHistoryItem

class AttendanceHistoryAdapter(
    private val attendanceRecords: List<AttendanceHistoryItem>
) : RecyclerView.Adapter<AttendanceHistoryAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemAttendanceHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAttendanceHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = attendanceRecords[position]
        holder.binding.subjectText.text = record.subject
        holder.binding.dateText.text = record.date
        holder.binding.statusText.text = record.status
    }

    override fun getItemCount() = attendanceRecords.size
}