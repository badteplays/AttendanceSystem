package com.example.attendancesystem

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class TeacherMainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { load(TeacherDashboardFragment()); true }
                R.id.nav_schedule -> { 
                    android.util.Log.d("TeacherMainActivity", "Loading schedule fragment")
                    load(TeacherSchedulesFragment()); true 
                }
                R.id.nav_attendance -> { load(TeacherAttendanceFragment()); true }
                R.id.nav_analytics -> { load(TeacherAnalyticsFragment()); true }
                R.id.nav_settings -> { load(TeacherOptionsFragment()); true }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_home
        }
    }

    private fun load(fragment: Fragment) {
        try {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
        } catch (e: Exception) {
            android.util.Log.e("TeacherMainActivity", "Error loading fragment: ${e.message}", e)
            // Fallback to dashboard if fragment loading fails
            if (fragment !is TeacherDashboardFragment) {
                load(TeacherDashboardFragment())
            }
        }
    }
}


