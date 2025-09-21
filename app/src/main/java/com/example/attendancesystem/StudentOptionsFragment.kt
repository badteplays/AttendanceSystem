package com.example.attendancesystem

import android.content.Intent
import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.provider.MediaStore
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.attendancesystem.utils.ProfilePictureManager

class StudentOptionsFragment : Fragment() {
    private lateinit var textUserName: TextView
    private lateinit var textUserEmail: TextView
    private lateinit var chipSection: TextView
    private lateinit var imageProfilePic: ImageView
    private lateinit var textInitials: TextView
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_student_options, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeViews(view)
        loadUserProfile()
        setupClickListeners(view)
    }

    private fun initializeViews(view: View) {
        textUserName = view.findViewById(R.id.textUserName)
        textUserEmail = view.findViewById(R.id.textUserEmail)
        chipSection = view.findViewById(R.id.chipSection)
        imageProfilePic = view.findViewById(R.id.imageProfilePic)
        textInitials = view.findViewById(R.id.textInitials)
    }

    private fun loadUserProfile() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Set email
            textUserEmail.text = currentUser.email ?: "No email"
            
            // Load user data from Firestore
            db.collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val name = document.getString("name") ?: "Student"
                        val section = document.getString("section") ?: "N/A"
                        
                        textUserName.text = name
                        chipSection.text = "Section $section"
                        
                        // Use ProfilePictureManager to load profile picture
                        val profileManager = ProfilePictureManager.getInstance()
                        profileManager.loadProfilePicture(requireContext(), imageProfilePic, textInitials, name, "ST")
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun setupClickListeners(view: View) {
        // Logout option
        view.findViewById<LinearLayout>(R.id.buttonLogout).setOnClickListener { confirmLogout() }

        // Change photo - reuse ProfilePictureManager
        view.findViewById<ImageView>(R.id.imageProfilePic).setOnClickListener { openImagePicker() }
        view.findViewById<TextView>(R.id.textInitials).setOnClickListener { openImagePicker() }

        // Notifications toggle
        val prefs = requireContext().getSharedPreferences("student_prefs", android.content.Context.MODE_PRIVATE)
        val switch = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchClassReminders)
        switch?.isChecked = prefs.getBoolean("notifications_enabled", true)
        switch?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications_enabled", isChecked).apply()
            if (isChecked) {
                scheduleStudentReminders()
                Toast.makeText(requireContext(), "Class reminders enabled", Toast.LENGTH_SHORT).show()
            } else {
                cancelStudentReminders()
                Toast.makeText(requireContext(), "Class reminders disabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                auth.signOut()
                val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                requireActivity().finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private val imagePickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                try {
                    val profileManager = ProfilePictureManager.getInstance()
                    val success = profileManager.saveProfilePicture(requireContext(), uri)
                    if (success) {
                        profileManager.loadProfilePicture(requireContext(), imageProfilePic, textInitials, textUserName.text.toString(), "ST")
                        Toast.makeText(requireContext(), "Profile picture updated", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to save profile picture", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        imagePickerLauncher.launch(intent)
    }

    private fun scheduleStudentReminders() {
        try {
            val wm = androidx.work.WorkManager.getInstance(requireContext())
            val req = androidx.work.PeriodicWorkRequestBuilder<StudentReminderWorker>(1, java.util.concurrent.TimeUnit.DAYS).build()
            wm.enqueueUniquePeriodicWork("student_reminder_work", androidx.work.ExistingPeriodicWorkPolicy.UPDATE, req)
        } catch (_: Exception) { }
    }

    private fun cancelStudentReminders() {
        try {
            val wm = androidx.work.WorkManager.getInstance(requireContext())
            wm.cancelUniqueWork("student_reminder_work")
        } catch (_: Exception) { }
    }
}
