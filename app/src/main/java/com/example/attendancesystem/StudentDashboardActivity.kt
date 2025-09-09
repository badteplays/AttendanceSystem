package com.example.attendancesystem

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.widget.Button
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.example.attendancesystem.QRScannerActivity
import com.example.attendancesystem.StudentOptionsActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.attendancesystem.utils.ProfilePictureManager
import com.google.firebase.firestore.Query
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class StudentDashboardActivity : AppCompatActivity() {
    // UI Components
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var textWelcomeStudent: TextView
    private lateinit var textName: TextView
    private lateinit var textCourse: TextView
    private lateinit var imageProfilePic: ImageView
    private lateinit var textInitials: TextView
    private lateinit var buttonScanQR: Button
    private lateinit var buttonViewHistory: Button
    private lateinit var fabScanQR: FloatingActionButton
    private lateinit var textTodayStatus: TextView
    private lateinit var textStatusTime: TextView
    private lateinit var statusIndicator: View
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var userId: String = ""
    private var studentName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_dashboard_new)
        
        initializeViews()

        loadUserData()
        setupClickListeners()
        setupSwipeRefresh()
        setupBottomNavigation()
        
        // Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(false) // Dashboard is home
    }
    
    override fun onBackPressed() {
        // Exit app with confirmation dialog
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Exit App")
            .setMessage("Are you sure you want to exit?")
            .setPositiveButton("Yes") { _, _ ->
                super.onBackPressed()
            }
            .setNegativeButton("No", null)
            .show()
    }
    
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    private fun initializeViews() {
        try {
            swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
            textWelcomeStudent = findViewById(R.id.textWelcomeStudent)
            textName = findViewById(R.id.textName)
            textCourse = findViewById(R.id.textCourse)
            imageProfilePic = findViewById(R.id.imageProfilePic)
            textInitials = findViewById(R.id.textInitials)
            buttonScanQR = findViewById(R.id.buttonScanQR)
            buttonViewHistory = findViewById(R.id.buttonViewHistory)
            fabScanQR = findViewById(R.id.fabScanQR)
            textTodayStatus = findViewById(R.id.textTodayStatus)
            textStatusTime = findViewById(R.id.textStatusTime)
            statusIndicator = findViewById(R.id.statusIndicator)
        } catch (e: Exception) {
            android.util.Log.e("StudentDashboard", "Error initializing views", e)
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            loadUserData()
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Listen for changes to the user's Firestore document for live profile pic updates
        db.collection("users").document(currentUser.uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    studentName = snapshot.getString("name") ?: "Student"
                    val welcomeText = "Welcome, $studentName!"
                    textWelcomeStudent.text = welcomeText
                    val studentSection = snapshot.getString("section") ?: "Section"
                    val profilePicUrl = snapshot.getString("profilePicUrl")

                    textName.text = studentName
                    textCourse.text = studentSection

                    // Use ProfilePictureManager to load profile picture globally
                    val profileManager = ProfilePictureManager.getInstance()
                    profileManager.loadProfilePicture(this@StudentDashboardActivity, imageProfilePic, textInitials, studentName, "ST")
                }
            }
    }

    private fun setupClickListeners() {
        // Set up quick action buttons
        buttonScanQR.setOnClickListener {
            try {
            startActivity(Intent(this, QRScannerActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            } catch (e: Exception) {
                Toast.makeText(this, "Error opening scanner: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        buttonViewHistory.setOnClickListener {
            val intent = Intent(this, StudentMainActivity::class.java)
            intent.putExtra("selected_tab", R.id.nav_history)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
        
        // Set up floating action button
        fabScanQR.setOnClickListener {
            try {
                startActivity(Intent(this, QRScannerActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            } catch (e: Exception) {
                Toast.makeText(this, "Error opening scanner: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupBottomNavigation() {
        // Home button (already selected)
        findViewById<LinearLayout>(R.id.nav_home_btn)?.setOnClickListener { 
            // Already on home, just refresh or do nothing
            Toast.makeText(this, "Already on Dashboard", Toast.LENGTH_SHORT).show()
        }
        
        // Scan button
        findViewById<LinearLayout>(R.id.nav_scan_btn)?.setOnClickListener { 
            try {
            startActivity(Intent(this, QRScannerActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            } catch (e: Exception) {
                Toast.makeText(this, "Error opening scanner: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // History button
        findViewById<LinearLayout>(R.id.nav_history_btn)?.setOnClickListener { 
            val intent = Intent(this, StudentMainActivity::class.java)
            intent.putExtra("selected_tab", R.id.nav_history)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
        
        // Profile button
        findViewById<LinearLayout>(R.id.nav_profile_btn)?.setOnClickListener { 
            val intent = Intent(this, StudentMainActivity::class.java)
            intent.putExtra("selected_tab", R.id.nav_profile)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
    }

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
    }
}