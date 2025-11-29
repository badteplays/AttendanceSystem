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
        val studentName: TextView = itemView.findViewById(R.id.textStudentName)
        val timeTaken: TextView = itemView.findViewById(R.id.textTimeTaken)
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
        holder.studentName.text = item.studentName
        holder.timeTaken.text = item.timeTaken
        holder.section.text = item.section
        holder.status.text = item.status

        // Set status background color
            "PRESENT" -> R.drawable.status_present_bg
            "EXCUSED" -> R.drawable.status_excused_bg
            "CUTTING" -> R.drawable.status_cutting_bg
            "ABSENT" -> R.drawable.status_absent_bg
        }



        holder.buttonRemove?.setOnClickListener {
            onRemove?.invoke(item)
        }
    }

    override fun getItemCount() = attendanceList.size
}

    fun updateOnRemoveListener(listener: (TeacherAttendanceItem) -> Unit) {
        this.onRemove = listener
    }
