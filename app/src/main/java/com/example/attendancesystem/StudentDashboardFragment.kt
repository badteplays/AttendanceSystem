package com.example.attendancesystem

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.widget.Button
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.attendancesystem.utils.ProfilePictureManager
import com.google.firebase.firestore.Query
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class StudentDashboardFragment : Fragment() {
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

    override fun onCreateView(
        inflater: LayoutInflater, 
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_student_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeViews(view)
        loadUserData()
        setupClickListeners()
        setupSwipeRefresh()
    }

    private fun initializeViews(view: View) {
        try {
            swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
            textWelcomeStudent = view.findViewById(R.id.textWelcomeStudent)
            textName = view.findViewById(R.id.textName)
            textCourse = view.findViewById(R.id.textCourse)
            imageProfilePic = view.findViewById(R.id.imageProfilePic)
            textInitials = view.findViewById(R.id.textInitials)
            buttonScanQR = view.findViewById(R.id.buttonScanQR)
            buttonViewHistory = view.findViewById(R.id.buttonViewHistory)
            fabScanQR = view.findViewById(R.id.fabScanQR)
            textTodayStatus = view.findViewById(R.id.textTodayStatus)
            textStatusTime = view.findViewById(R.id.textStatusTime)
            statusIndicator = view.findViewById(R.id.statusIndicator)
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
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
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
                    profileManager.loadProfilePicture(requireContext(), imageProfilePic, textInitials, studentName, "ST")
                }
            }
    }

    private fun setupClickListeners() {
        // Set up quick action buttons
        buttonScanQR.setOnClickListener {
            try {
                // Switch to QR Scanner fragment
                switchToFragment(QRScannerFragment())
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error opening scanner: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        buttonViewHistory.setOnClickListener {
            try {
                // Switch to History fragment
                switchToFragment(StudentAttendanceHistoryFragment())
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error opening history: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Set up floating action button
        fabScanQR.setOnClickListener {
            try {
                // Switch to QR Scanner fragment
                switchToFragment(QRScannerFragment())
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error opening scanner: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun switchToFragment(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        
        // Update bottom navigation selection
        val bottomNav = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigationView)
        when (fragment) {
            is QRScannerFragment -> bottomNav.selectedItemId = R.id.nav_scan
            is StudentScheduleFragment -> bottomNav.selectedItemId = R.id.nav_schedule
            is StudentAttendanceHistoryFragment -> bottomNav.selectedItemId = R.id.nav_history
            is StudentOptionsFragment -> bottomNav.selectedItemId = R.id.nav_profile
        }
    }

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(requireContext(), message, duration).show()
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
    }
}
