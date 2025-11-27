package com.example.attendancesystem.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.attendancesystem.R
import com.example.attendancesystem.models.Routine

class RoutineAdapter(
    private val routines: MutableList<Routine>,
    private val onDeleteClick: (Routine) -> Unit
) : RecyclerView.Adapter<RoutineAdapter.RoutineViewHolder>() {

    class RoutineViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val colorIndicator: View = view.findViewById(R.id.colorIndicator)
        val textRoutineTitle: TextView = view.findViewById(R.id.textRoutineTitle)
        val textRoutineDescription: TextView = view.findViewById(R.id.textRoutineDescription)
        val textRoutineTime: TextView = view.findViewById(R.id.textRoutineTime)
        val textRoutineDay: TextView = view.findViewById(R.id.textRoutineDay)
        val buttonDelete: ImageView = view.findViewById(R.id.buttonDeleteRoutine)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoutineViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_routine, parent, false)
        return RoutineViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoutineViewHolder, position: Int) {
        val routine = routines[position]
        
        holder.textRoutineTitle.text = routine.title
        holder.textRoutineDescription.text = routine.description.ifEmpty { "No description" }
        holder.textRoutineTime.text = routine.getTimeRange()
        holder.textRoutineDay.text = routine.day
        
        // Set color indicator
        try {
            holder.colorIndicator.setBackgroundColor(Color.parseColor(routine.color))
        } catch (e: Exception) {
            holder.colorIndicator.setBackgroundColor(Color.parseColor("#4CAF50"))
        }
        
        holder.buttonDelete.setOnClickListener {
            onDeleteClick(routine)
        }
    }

    override fun getItemCount() = routines.size
    
    fun updateRoutines(newRoutines: List<Routine>) {
        routines.clear()
        routines.addAll(newRoutines)
        notifyDataSetChanged()
    }
}





