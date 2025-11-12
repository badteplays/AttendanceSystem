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
    private lateinit var spinnerReminderTime: Spinner
    private lateinit var reminderTimeLayout: LinearLayout
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
        spinnerReminderTime = view.findViewById(R.id.spinnerReminderTime)
        reminderTimeLayout = view.findViewById(R.id.reminderTimeLayout)
        
        setupReminderTimeSpinner()
    }
    
    private fun setupReminderTimeSpinner() {
        val reminderOptions = arrayOf("5 minutes", "10 minutes", "15 minutes", "20 minutes", "30 minutes", "1 hour")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, reminderOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerReminderTime.adapter = adapter
        
        // Load saved reminder time
        val prefs = requireContext().getSharedPreferences("student_prefs", android.content.Context.MODE_PRIVATE)
        val savedMinutes = prefs.getInt("reminder_minutes", 10)
        val position = when (savedMinutes) {
            5 -> 0
            10 -> 1
            15 -> 2
            20 -> 3
            30 -> 4
            60 -> 5
            else -> 1 // Default to 10 minutes
        }
        spinnerReminderTime.setSelection(position)
        
        // Save when changed
        spinnerReminderTime.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val minutes = when (position) {
                    0 -> 5
                    1 -> 10
                    2 -> 15
                    3 -> 20
                    4 -> 30
                    5 -> 60
                    else -> 10
                }
                prefs.edit().putInt("reminder_minutes", minutes).apply()
                
                // Reschedule reminders if enabled
                if (prefs.getBoolean("notifications_enabled", true)) {
                    scheduleStudentReminders()
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
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

        // About us
        view.findViewById<LinearLayout>(R.id.buttonAboutUs)?.setOnClickListener {
            val url = "https://badteplays.github.io/FPL-WEBSITE/website.html"
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Unable to open link", Toast.LENGTH_SHORT).show()
            }
        }

        // Change photo - reuse ProfilePictureManager
        view.findViewById<ImageView>(R.id.imageProfilePic).setOnClickListener { openImagePicker() }
        view.findViewById<TextView>(R.id.textInitials).setOnClickListener { openImagePicker() }

        // Notifications toggle
        val prefs = requireContext().getSharedPreferences("student_prefs", android.content.Context.MODE_PRIVATE)
        val switch = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchClassReminders)
        switch?.isChecked = prefs.getBoolean("notifications_enabled", true)
        
        // Show/hide reminder time setting based on switch state
        reminderTimeLayout.visibility = if (switch?.isChecked == true) View.VISIBLE else View.GONE
        
        switch?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications_enabled", isChecked).apply()
            reminderTimeLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
            
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
            
            // Schedule to run every 15 minutes to catch reminder windows
            val req = androidx.work.PeriodicWorkRequestBuilder<StudentReminderWorker>(
                15, java.util.concurrent.TimeUnit.MINUTES
            ).build()
            
            wm.enqueueUniquePeriodicWork(
                "student_reminder_work",
                androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
                req
            )
        } catch (_: Exception) { }
    }

    private fun cancelStudentReminders() {
        try {
            val wm = androidx.work.WorkManager.getInstance(requireContext())
            wm.cancelUniqueWork("student_reminder_work")
        } catch (_: Exception) { }
    }
}
