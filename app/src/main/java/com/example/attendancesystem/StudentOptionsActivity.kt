package com.example.attendancesystem

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
// import com.google.android.material.materialswitch.MaterialSwitch
import androidx.appcompat.widget.SwitchCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.example.attendancesystem.utils.ProfilePictureManager
import java.util.*

class StudentOptionsActivity : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()
    
    private lateinit var imageProfilePic: ImageView
    private lateinit var textInitials: TextView
    private lateinit var textUserName: TextView
    private lateinit var textUserEmail: TextView
    private lateinit var chipSection: Chip
    private lateinit var fabChangePhoto: FloatingActionButton
    private lateinit var switchClassReminders: SwitchCompat

    // Activity result launcher for image selection
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                uploadProfilePicture(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.student_options)

        initializeViews()
        loadUserProfile()
        setupClickListeners()
        setupBottomNavigation()
    }

    private fun initializeViews() {
        imageProfilePic = findViewById(R.id.imageProfilePic)
        textInitials = findViewById(R.id.textInitials)
        textUserName = findViewById(R.id.textUserName)
        textUserEmail = findViewById(R.id.textUserEmail)
        chipSection = findViewById(R.id.chipSection)
        fabChangePhoto = findViewById(R.id.fabChangePhoto)
        switchClassReminders = findViewById(R.id.switchClassReminders)
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
                        profileManager.loadProfilePicture(this, imageProfilePic, textInitials, name, "ST")
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }



    private fun setupClickListeners() {
        // Profile picture change
        fabChangePhoto.setOnClickListener {
            openImagePicker()
        }

        // Logout option
        findViewById<LinearLayout>(R.id.buttonLogout).setOnClickListener {
            performLogout()
        }

        // Notification toggle
        val prefs = getSharedPreferences("student_prefs", MODE_PRIVATE)
        val enabled = prefs.getBoolean("notifications_enabled", true)
        switchClassReminders.isChecked = enabled
        switchClassReminders.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications_enabled", isChecked).apply()
            if (isChecked) {
                scheduleStudentReminders()
                Toast.makeText(this, "Class reminders enabled", Toast.LENGTH_SHORT).show()
            } else {
                cancelStudentReminders()
                Toast.makeText(this, "Class reminders disabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun scheduleStudentReminders() {
        try {
            val workManager = androidx.work.WorkManager.getInstance(this)
            val request = androidx.work.PeriodicWorkRequestBuilder<StudentReminderWorker>(1, java.util.concurrent.TimeUnit.DAYS)
                .build()
            workManager.enqueueUniquePeriodicWork(
                "student_reminder_work",
                androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } catch (_: Exception) { }
    }

    private fun cancelStudentReminders() {
        try {
            val workManager = androidx.work.WorkManager.getInstance(this)
            workManager.cancelUniqueWork("student_reminder_work")
        } catch (_: Exception) { }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        imagePickerLauncher.launch(intent)
    }

    private fun uploadProfilePicture(imageUri: Uri) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated. Please log in again.", Toast.LENGTH_LONG).show()
            return
        }
        
        try {
            // Use ProfilePictureManager to save globally
            val profileManager = ProfilePictureManager.getInstance()
            val success = profileManager.saveProfilePicture(this, imageUri)
            
            if (success) {
                android.util.Log.d("ProfileUpload", "Saved profile picture locally for user: ${currentUser.uid}")
                
                // Update UI immediately using the manager
                val userName = textUserName.text.toString()
                profileManager.loadProfilePicture(this, imageProfilePic, textInitials, userName, "ST")
                
                // Update Firestore with a flag indicating local storage
                db.collection("users").document(currentUser.uid)
                    .update("hasLocalProfilePic", true)
                    .addOnSuccessListener {
                        android.util.Log.d("ProfileUpload", "Firestore updated successfully")
                        Toast.makeText(this, "Profile picture updated across the entire app!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("ProfileUpload", "Failed to update Firestore flag", e)
                        // Still show success since the image is saved locally
                        Toast.makeText(this, "Profile picture updated across the entire app!", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "Failed to save profile picture", Toast.LENGTH_LONG).show()
            }
                
        } catch (e: Exception) {
            android.util.Log.e("ProfileUpload", "Failed to save profile picture locally", e)
            Toast.makeText(this, "Failed to save profile picture: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun performLogout() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupBottomNavigation() {
        // Home button → switch tab in StudentMainActivity
        findViewById<LinearLayout>(R.id.nav_home_btn)?.setOnClickListener { 
            val intent = Intent(this, StudentMainActivity::class.java)
            intent.putExtra("selected_tab", R.id.nav_home)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
        
        // Scan button
        findViewById<LinearLayout>(R.id.nav_scan_btn)?.setOnClickListener { 
            val intent = Intent(this, StudentMainActivity::class.java)
            intent.putExtra("selected_tab", R.id.nav_scan)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
        
        // History button
        findViewById<LinearLayout>(R.id.nav_history_btn)?.setOnClickListener { 
            val intent = Intent(this, StudentMainActivity::class.java)
            intent.putExtra("selected_tab", R.id.nav_history)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
        
        // Profile button (current page - already selected)
        findViewById<LinearLayout>(R.id.nav_profile_btn)?.setOnClickListener { 
            Toast.makeText(this, "Already on Profile", Toast.LENGTH_SHORT).show()
        }
    }
}