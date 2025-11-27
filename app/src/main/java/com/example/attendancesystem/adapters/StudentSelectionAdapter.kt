package com.example.attendancesystem.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.attendancesystem.R
import com.example.attendancesystem.models.StudentItem

class StudentSelectionAdapter(
    private val students: List<StudentItem>,
    private val onStudentSelected: (StudentItem, Boolean) -> Unit
) : RecyclerView.Adapter<StudentSelectionAdapter.ViewHolder>() {

    private val selectedStudents = mutableSetOf<String>()

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkBox: CheckBox = itemView.findViewById(R.id.checkBoxStudent)
        val textStudentName: TextView = itemView.findViewById(R.id.textStudentName)
        val textStudentEmail: TextView = itemView.findViewById(R.id.textStudentEmail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_selection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val student = students[position]
        holder.textStudentName.text = student.name
        holder.textStudentEmail.text = student.email
        holder.checkBox.isChecked = selectedStudents.contains(student.id)

        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedStudents.add(student.id)
            } else {
                selectedStudents.remove(student.id)
            }
            onStudentSelected(student, isChecked)
        }
    }

    override fun getItemCount() = students.size

    fun getSelectedStudents(): List<StudentItem> {
        return students.filter { selectedStudents.contains(it.id) }
    }
}
