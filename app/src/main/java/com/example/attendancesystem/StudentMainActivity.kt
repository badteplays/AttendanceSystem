package com.example.attendancesystem

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import android.widget.ImageView

class StudentMainActivity : AppCompatActivity() {
    
    private lateinit var drawerLayout: DrawerLayout
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_main)
        
        setupDrawerNavigation()
        
        // Load initial fragment (supports deep-linking via intent extra)
        if (savedInstanceState == null) {
            loadFragment(StudentDashboardFragment())
        }
    }
    
    private fun setupDrawerNavigation() {
        drawerLayout = findViewById(R.id.drawerLayout)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        val drawerHandle = findViewById<ImageView>(R.id.drawerHandle)
        
        // Handle drawer toggle
        drawerHandle.setOnClickListener {
            if (drawerLayout.isDrawerOpen(navigationView)) {
                drawerLayout.closeDrawer(navigationView)
            } else {
                drawerLayout.openDrawer(navigationView)
            }
        }
        
        // Handle navigation item selection
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.drawer_home -> {
                    loadFragment(StudentDashboardFragment())
                    true
                }
                R.id.drawer_scan -> {
                    loadFragment(QRScannerFragment())
                    true
                }
                R.id.drawer_schedule -> {
                    android.util.Log.d("StudentMainActivity", "Loading schedule fragment")
                    loadFragment(StudentScheduleFragment())
                    true
                }
                R.id.drawer_routines -> {
                    loadFragment(StudentRoutinesFragment())
                    true
                }
                R.id.drawer_history -> {
                    loadFragment(StudentAttendanceHistoryFragment())
                    true
                }
                R.id.drawer_profile -> {
                    loadFragment(StudentOptionsFragment())
                    true
                }
                else -> false
            }.also {
                if (it) drawerLayout.closeDrawer(navigationView)
            }
        }
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
    
    override fun onBackPressed() {
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        if (drawerLayout.isDrawerOpen(navigationView)) {
            drawerLayout.closeDrawer(navigationView)
        } else {
            super.onBackPressed()
        }
    }
}
