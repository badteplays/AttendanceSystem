package com.example.attendancesystem

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.attendancesystem.adapters.RoutineAdapter
import com.example.attendancesystem.models.Routine
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class StudentRoutinesFragment : Fragment() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAddRoutine: FloatingActionButton
    private lateinit var emptyState: View
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: RoutineAdapter
    
    private val routinesList = mutableListOf<Routine>()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_student_routines, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeViews(view)
        setupRecyclerView()
        setupFab()
        loadRoutines()
    }
    
    private fun initializeViews(view: View) {
        recyclerView = view.findViewById(R.id.recyclerViewRoutines)
        fabAddRoutine = view.findViewById(R.id.fabAddRoutine)
        emptyState = view.findViewById(R.id.emptyStateRoutines)
        progressBar = view.findViewById(R.id.progressBarRoutines)
    }
    
    private fun setupRecyclerView() {
        adapter = RoutineAdapter(routinesList) { routine ->
            confirmDelete(routine)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }
    
    private fun setupFab() {
        fabAddRoutine.setOnClickListener {
            showAddRoutineDialog()
        }
    }
    
    private fun loadRoutines() {
        val currentUser = auth.currentUser ?: return
        
        progressBar.visibility = View.VISIBLE
        
        db.collection("routines")
            .whereEqualTo("userId", currentUser.uid)
            .get()
            .addOnSuccessListener { documents ->
                routinesList.clear()
                
                for (doc in documents) {
                    val routine = Routine.fromMap(doc.id, doc.data)
                    routinesList.add(routine)
                }
                
                // Sort by day and time
                routinesList.sortWith(compareBy({ getDayOrder(it.day) }, { it.startTime }))
                
                adapter.notifyDataSetChanged()
                updateEmptyState()
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load routines", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
            }
    }
    
    private fun getDayOrder(day: String): Int {
        return when (day) {
            "Monday" -> 1
            "Tuesday" -> 2
            "Wednesday" -> 3
            "Thursday" -> 4
            "Friday" -> 5
            "Saturday" -> 6
            "Sunday" -> 7
            else -> 8
        }
    }
    
    private fun updateEmptyState() {
        if (routinesList.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }
    
    private fun showAddRoutineDialog() {
        val dialog = AddRoutineDialog(requireContext()) { routine ->
            // Validate against class schedule before saving
            validateAndSaveRoutine(routine)
        }
        dialog.show()
    }
    
    private fun validateAndSaveRoutine(routine: Routine) {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                
                // Get student's section
                val currentUser = auth.currentUser ?: return@launch
                val userDoc = db.collection("users").document(currentUser.uid).get().await()
                val section = userDoc.getString("section")?.uppercase() ?: ""
                
                // Get class schedule for the selected day
                val schedules = db.collection("schedules")
                    .whereEqualTo("day", routine.day)
                    .whereEqualTo("section", section)
                    .get()
                    .await()
                
                // Check for conflicts
                val conflict = checkTimeConflict(routine, schedules.documents.map { doc ->
                    Triple(
                        doc.getString("subject") ?: "Class",
                        doc.getString("startTime") ?: "",
                        doc.getString("endTime") ?: ""
                    )
                })
                
                progressBar.visibility = View.GONE
                
                if (conflict != null) {
                    // Show conflict error
                    AlertDialog.Builder(requireContext())
                        .setTitle("Schedule Conflict")
                        .setMessage("Your routine conflicts with ${conflict.first} (${conflict.second} - ${conflict.third}). Please choose a different time slot.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    // No conflict, save routine
                    saveRoutine(routine)
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun checkTimeConflict(
        routine: Routine,
        classes: List<Triple<String, String, String>>
    ): Triple<String, String, String>? {
        val routineStart = timeToMinutes(routine.startTime)
        val routineEnd = timeToMinutes(routine.endTime)
        
        for (classInfo in classes) {
            val classStart = timeToMinutes(classInfo.second)
            val classEnd = timeToMinutes(classInfo.third)
            
            // Check if times overlap
            if (routineStart < classEnd && routineEnd > classStart) {
                return classInfo // Conflict found
            }
        }
        
        return null // No conflict
    }
    
    private fun timeToMinutes(time: String): Int {
        return try {
            val parts = time.split(":")
            val hours = parts[0].toInt()
            val minutes = parts[1].toInt()
            hours * 60 + minutes
        } catch (e: Exception) {
            0
        }
    }
    
    private fun saveRoutine(routine: Routine) {
        val currentUser = auth.currentUser ?: return
        
        val routineData = routine.copy(userId = currentUser.uid)
        
        db.collection("routines")
            .add(routineData.toMap())
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Routine added successfully!", Toast.LENGTH_SHORT).show()
                loadRoutines()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to add routine: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun confirmDelete(routine: Routine) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Routine")
            .setMessage("Are you sure you want to delete \"${routine.title}\"?")
            .setPositiveButton("Delete") { _, _ ->
                deleteRoutine(routine)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteRoutine(routine: Routine) {
        db.collection("routines")
            .document(routine.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Routine deleted", Toast.LENGTH_SHORT).show()
                loadRoutines()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}





