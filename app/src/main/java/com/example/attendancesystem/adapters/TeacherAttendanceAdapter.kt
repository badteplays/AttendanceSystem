package com.example.attendancesystem.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.attendancesystem.R
import com.example.attendancesystem.models.TeacherAttendanceItem

class TeacherAttendanceAdapter(
    private val attendanceList: MutableList<TeacherAttendanceItem>,
    private var onRemove: ((TeacherAttendanceItem) -> Unit)? = null
) : RecyclerView.Adapter<TeacherAttendanceAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatarInitials: TextView = itemView.findViewById(R.id.textAvatarInitials)
        val studentName: TextView = itemView.findViewById(R.id.textStudentName)
        val section: TextView = itemView.findViewById(R.id.textSection)
        val status: TextView = itemView.findViewById(R.id.textStatus)
        val buttonRemove: TextView? = itemView.findViewById(R.id.buttonRemove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_teacher_attendance, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = attendanceList[position]
        val ctx = holder.itemView.context
        holder.studentName.text = item.studentName
        holder.section.text = item.section
        val normalizedStatus = item.status.uppercase()
        holder.status.text = normalizedStatus.lowercase().replaceFirstChar { it.uppercase() }

        holder.avatarInitials.text = item.studentName
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "ST" }

        when (normalizedStatus) {
            "PRESENT" -> {
                holder.avatarInitials.background = ContextCompat.getDrawable(ctx, R.drawable.bg_teacher_avatar_present)
                holder.avatarInitials.setTextColor(ContextCompat.getColor(ctx, R.color.teacher_ds_status_present_text))
                holder.status.background = ContextCompat.getDrawable(ctx, R.drawable.bg_teacher_status_badge_present)
                holder.status.setTextColor(ContextCompat.getColor(ctx, R.color.teacher_ds_status_present_text))
            }
            "LATE" -> {
                holder.avatarInitials.background = ContextCompat.getDrawable(ctx, R.drawable.bg_teacher_avatar_late)
                holder.avatarInitials.setTextColor(ContextCompat.getColor(ctx, R.color.teacher_ds_status_late_text))
                holder.status.background = ContextCompat.getDrawable(ctx, R.drawable.bg_teacher_status_badge_late)
                holder.status.setTextColor(ContextCompat.getColor(ctx, R.color.teacher_ds_status_late_text))
            }
            else -> {
                holder.avatarInitials.background = ContextCompat.getDrawable(ctx, R.drawable.bg_teacher_avatar_absent)
                holder.avatarInitials.setTextColor(ContextCompat.getColor(ctx, R.color.teacher_ds_status_absent_text))
                holder.status.background = ContextCompat.getDrawable(ctx, R.drawable.bg_teacher_status_badge_absent)
                holder.status.setTextColor(ContextCompat.getColor(ctx, R.color.teacher_ds_status_absent_text))
            }
        }

        holder.buttonRemove?.setOnClickListener {
            onRemove?.invoke(item)
        }
    }

    override fun getItemCount() = attendanceList.size

    fun updateOnRemoveListener(listener: (TeacherAttendanceItem) -> Unit) {
        this.onRemove = listener
    }
}
