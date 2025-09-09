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
import com.google.android.material.floatingactionbutton.FloatingActionButton
// import com.google.android.material.materialswitch.MaterialSwitch
import androidx.appcompat.widget.SwitchCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.example.attendancesystem.utils.ProfilePictureManager
import com.example.attendancesystem.utils.ThemeManager
import java.util.*

class TeacherOptionsActivity : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()
    
    private lateinit var imageProfilePic: ImageView
    private lateinit var textInitials: TextView
    private lateinit var textUserName: TextView
    private lateinit var textUserEmail: TextView
    private lateinit var fabChangePhoto: FloatingActionButton
    private lateinit var textCurrentTheme: TextView
    private lateinit var themeManager: ThemeManager


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
        setContentView(R.layout.teacher_options)

        themeManager = ThemeManager.getInstance(this)
        initializeViews()
        loadUserProfile()
        setupClickListeners()
        setupBottomNavigation()
        updateThemeDisplay()
    }

    private fun initializeViews() {
        imageProfilePic = findViewById(R.id.imageProfilePic)
        textInitials = findViewById(R.id.textInitials)
        textUserName = findViewById(R.id.textUserName)
        textUserEmail = findViewById(R.id.textUserEmail)
        fabChangePhoto = findViewById(R.id.fabChangePhoto)
        textCurrentTheme = findViewById(R.id.textCurrentTheme)

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
                        val name = document.getString("name") ?: "Teacher"
                        textUserName.text = name
                        
                        // Use ProfilePictureManager to load profile picture
                        val profileManager = ProfilePictureManager.getInstance()
                        profileManager.loadProfilePicture(this, imageProfilePic, textInitials, name, "TC")
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

        // Theme selection
        findViewById<LinearLayout>(R.id.buttonTheme).setOnClickListener {
            showThemeSelectionDialog()
        }

        // Logout
        findViewById<LinearLayout>(R.id.buttonLogout).setOnClickListener {
            performLogout()
        }
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
                profileManager.loadProfilePicture(this, imageProfilePic, textInitials, userName, "TC")
                
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
        // Home button -> open container and select tab
        findViewById<LinearLayout>(R.id.nav_home_btn)?.setOnClickListener {
            val intent = Intent(this, TeacherMainActivity::class.java)
            intent.putExtra("selected_tab", R.id.nav_home)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
        
        // Schedule button -> container
        findViewById<LinearLayout>(R.id.nav_schedule_btn)?.setOnClickListener {
            val intent = Intent(this, TeacherMainActivity::class.java)
            intent.putExtra("selected_tab", R.id.nav_schedule)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
        
        // Attendance button -> container
        findViewById<LinearLayout>(R.id.nav_attendance_btn)?.setOnClickListener {
            val intent = Intent(this, TeacherMainActivity::class.java)
            intent.putExtra("selected_tab", R.id.nav_attendance)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
        
        // Analytics button -> container
        findViewById<LinearLayout>(R.id.nav_analytics_btn)?.setOnClickListener {
            val intent = Intent(this, TeacherMainActivity::class.java)
            intent.putExtra("selected_tab", R.id.nav_analytics)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
        
        // Settings button (current page - already selected)
        findViewById<LinearLayout>(R.id.nav_settings_btn)?.setOnClickListener { 
            Toast.makeText(this, "Already on Settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showThemeSelectionDialog() {
        val themes = arrayOf("Light", "Dark", "System")
        val currentTheme = themeManager.getCurrentTheme()
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Choose Theme")
            .setSingleChoiceItems(themes, currentTheme) { dialog, which ->
                themeManager.setTheme(which)
                updateThemeDisplay()
                dialog.dismiss()
                
                // Recreate activity to apply theme immediately
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun updateThemeDisplay() {
        val currentTheme = themeManager.getCurrentTheme()
        textCurrentTheme.text = themeManager.getThemeName(currentTheme)
    }
}