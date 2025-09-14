package com.example.attendancesystem

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class StudentMainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_main)
        
        setupBottomNavigation()
        
        // Load initial tab (supports deep-linking to a tab via intent extra)
        if (savedInstanceState == null) {
            val bottomNavigationView = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigationView)
            val initialTab = intent?.getIntExtra("selected_tab", R.id.nav_home) ?: R.id.nav_home
            bottomNavigationView.selectedItemId = initialTab
        }
    }
    
    private fun setupBottomNavigation() {
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(StudentDashboardFragment())
                    true
                }
                R.id.nav_scan -> {
                    loadFragment(QRScannerFragment())
                    true
                }
                R.id.nav_schedule -> {
                    android.util.Log.d("StudentMainActivity", "Loading schedule fragment")
                    loadFragment(StudentScheduleFragment())
                    true
                }
                R.id.nav_history -> {
                    loadFragment(StudentAttendanceHistoryFragment())
                    true
                }
                R.id.nav_profile -> {
                    loadFragment(StudentOptionsFragment())
                    true
                }
                else -> false
            }
        }
        
        // Set home as selected by default
        bottomNavigationView.selectedItemId = R.id.nav_home
    }
    
    private fun loadFragment(fragment: Fragment) {
        try {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
        } catch (e: Exception) {
            android.util.Log.e("StudentMainActivity", "Error loading fragment: ${e.message}", e)
            // Fallback to dashboard if fragment loading fails
            if (fragment !is StudentDashboardFragment) {
                loadFragment(StudentDashboardFragment())
            }
        }
    }
}
