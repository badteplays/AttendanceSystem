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
import com.example.attendancesystem.utils.ThemeManager

class StudentOptionsFragment : Fragment() {
    private lateinit var textUserName: TextView
    private lateinit var textUserEmail: TextView
    private lateinit var chipSection: TextView
    private lateinit var imageProfilePic: ImageView
    private lateinit var textInitials: TextView
    private lateinit var spinnerReminderTime: Spinner
    private lateinit var reminderTimeLayout: LinearLayout
    private lateinit var textCurrentTheme: TextView
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var themeManager: ThemeManager

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
        textCurrentTheme = view.findViewById(R.id.textCurrentTheme)
        
        themeManager = ThemeManager.getInstance(requireContext())
        setupReminderTimeSpinner()
        updateThemeDisplay()
    }
    
    private fun setupReminderTimeSpinner() {
        val reminderOptions = arrayOf("5 minutes", "10 minutes", "15 minutes", "20 minutes", "30 minutes", "1 hour")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, reminderOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerReminderTime.adapter = adapter
        
        val prefs = requireContext().getSharedPreferences("student_prefs", android.content.Context.MODE_PRIVATE)
        val savedMinutes = prefs.getInt("reminder_minutes", 10)
        val position = when (savedMinutes) {
            5 -> 0
            10 -> 1
            15 -> 2
            20 -> 3
            30 -> 4
            60 -> 5
            else -> 1
        }
        spinnerReminderTime.setSelection(position)
        
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
            textUserEmail.text = currentUser.email ?: "No email"
            
            db.collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val name = document.getString("name") ?: "Student"
                        val section = document.getString("section") ?: "N/A"
                        
                        textUserName.text = name
                        chipSection.text = "Section $section"
                        
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
        view.findViewById<LinearLayout>(R.id.buttonTheme)?.setOnClickListener {
            showThemePicker()
        }

        view.findViewById<LinearLayout>(R.id.buttonLogout).setOnClickListener { confirmLogout() }

        view.findViewById<LinearLayout>(R.id.buttonAboutUs)?.setOnClickListener {
            val url = "https://badteplays.github.io/FPL-WEBSITE/website.html"
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Unable to open link", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<ImageView>(R.id.imageProfilePic).setOnClickListener { openImagePicker() }
        view.findViewById<TextView>(R.id.textInitials).setOnClickListener { openImagePicker() }

        val prefs = requireContext().getSharedPreferences("student_prefs", android.content.Context.MODE_PRIVATE)
        val switch = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchClassReminders)
        switch?.isChecked = prefs.getBoolean("notifications_enabled", true)
        
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
                val prefs = requireContext().getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putBoolean("explicit_logout", true).apply()
                
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
            
            val req = androidx.work.PeriodicWorkRequestBuilder<StudentReminderWorker>(
                15, java.util.concurrent.TimeUnit.MINUTES
            ).build()
            
            wm.enqueueUniquePeriodicWork(
                "student_reminder_work",
                androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
                req
            )
            android.util.Log.d("StudentOptions", "Class reminders scheduled (15 min intervals)")
        } catch (e: Exception) {
            android.util.Log.e("StudentOptions", "Error scheduling reminders: ${e.message}", e)
        }
    }

    private fun cancelStudentReminders() {
        try {
            val wm = androidx.work.WorkManager.getInstance(requireContext())
            wm.cancelUniqueWork("student_reminder_work")
        } catch (_: Exception) { }
    }

    private fun showThemePicker() {
        val themes = arrayOf("Light", "Dark", "System")
        val currentTheme = themeManager.getCurrentTheme()
        
        AlertDialog.Builder(requireContext())
            .setTitle("Choose Theme")
            .setSingleChoiceItems(themes, currentTheme) { dialog, which ->
                val mode = when (which) {
                    0 -> ThemeManager.THEME_LIGHT
                    1 -> ThemeManager.THEME_DARK
                    else -> ThemeManager.THEME_SYSTEM
                }
                themeManager.setTheme(mode)
                updateThemeDisplay()
                dialog.dismiss()
                
                requireActivity().recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun updateThemeDisplay() {
        val currentTheme = themeManager.getCurrentTheme()
        textCurrentTheme.text = themeManager.getThemeName(currentTheme)
    }
}
