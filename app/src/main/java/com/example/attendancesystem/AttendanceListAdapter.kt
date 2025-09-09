package com.example.attendancesystem

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.attendancesystem.databinding.ItemAttendanceBinding
import com.example.attendancesystem.models.Attendance
import com.example.attendancesystem.models.AttendanceStatus
import java.text.SimpleDateFormat
import java.util.*

class AttendanceListAdapter(
    private val onItemClick: (Attendance) -> Unit,
    private val onManualAddClick: () -> Unit
) : ListAdapter<Attendance, AttendanceListAdapter.ViewHolder>(AttendanceDiffCallback()) {

    // For multi-select mode
    private val selectedItems = mutableSetOf<Int>()
    var multiSelectMode = false
    var onSelectionChanged: ((Set<Int>) -> Unit)? = null

    fun toggleSelection(position: Int) {
        if (selectedItems.contains(position)) selectedItems.remove(position)
        else selectedItems.add(position)
        notifyItemChanged(position)
        onSelectionChanged?.invoke(selectedItems)
    }
    fun clearSelection() {
        val prevSelected = selectedItems.toList()
        selectedItems.clear()
        prevSelected.forEach { notifyItemChanged(it) }
        onSelectionChanged?.invoke(selectedItems)
    }
    fun getSelectedAttendances(): List<Attendance> = selectedItems.map { getItem(it) }

    class ViewHolder(
        private val binding:   ItemAttendanceBinding,
        private val onItemClick: (Attendance) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            attendance: Attendance,
            onRemoveClick: (Attendance) -> Unit,
            isSelected: Boolean,
            onLongClick: (() -> Unit)?
        ) {
            android.util.Log.d("AttendanceAdapter", "Binding: ${attendance.studentName}, status: ${attendance.status}, timestamp: ${attendance.timestamp}")
            android.widget.Toast.makeText(binding.root.context, "Binding: ${attendance.studentName}", android.widget.Toast.LENGTH_SHORT).show()
            with(binding) {
                // Show Student Name
                txtStudentName.text = attendance.studentName
                // Show Status
                txtStatus.text = attendance.status.name
                // Show Timestamp formatted
                val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
                txtTimestamp.text = sdf.format(attendance.timestamp.toDate())

                // Show remove button
                btnRemoveStudent.visibility = android.view.View.VISIBLE
                btnRemoveStudent.setOnClickListener { onRemoveClick(attendance) }

                root.setOnClickListener { onItemClick(attendance) }
                root.setOnLongClickListener {
                    onLongClick?.invoke()
                    true
                }
                // Highlight if selected
                root.alpha = if (isSelected) 0.5f else 1.0f
            }
        }
        companion object {
            fun create(
                parent: ViewGroup,
                onItemClick: (Attendance) -> Unit
            ): ViewHolder {
                val binding = ItemAttendanceBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                return ViewHolder(binding, onItemClick)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder.create(parent, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.itemView.alpha = 0f
        holder.itemView.animate().alpha(1f).setDuration(400).start()
        val isSelected = selectedItems.contains(position)
        holder.bind(
            getItem(position),
            { attendance -> onRemoveStudent?.invoke(attendance) },
            isSelected,
            onLongClick = if (multiSelectMode) {
                { toggleSelection(position) }
            } else {
                {
                    multiSelectMode = true
                    toggleSelection(position)
                }
            }
        )
    }

    private class AttendanceDiffCallback : DiffUtil.ItemCallback<Attendance>() {
        override fun areItemsTheSame(oldItem: Attendance, newItem: Attendance): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Attendance, newItem: Attendance): Boolean {
            return oldItem == newItem
        }
    }

    var onRemoveStudent: ((Attendance) -> Unit)? = null

    fun submitAttendanceList(list: List<Attendance>) {
        submitList(list)
    }
}