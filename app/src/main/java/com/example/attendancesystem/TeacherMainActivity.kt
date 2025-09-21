package com.example.attendancesystem

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import androidx.core.view.GravityCompat

class TeacherMainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_main)

        val drawerLayout = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawerLayout)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        val handle = findViewById<android.widget.ImageView>(R.id.drawerHandle)
        handle?.setOnClickListener { drawerLayout?.openDrawer(GravityCompat.END) }
        navigationView?.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.drawer_home -> { load(TeacherDashboardFragment()); true }
                R.id.drawer_schedule -> { load(TeacherSchedulesFragment()); true }
                R.id.drawer_attendance -> { load(TeacherAttendanceFragment()); true }
                R.id.drawer_analytics -> { load(TeacherAnalyticsFragment()); true }
                R.id.drawer_settings -> { load(TeacherOptionsFragment()); true }
                else -> false
            }.also { drawerLayout?.closeDrawers() }
        }

        if (savedInstanceState == null) {
            val open = intent?.getStringExtra("open")
            if (open == "analytics") {
                load(TeacherAnalyticsFragment())
            } else {
                load(TeacherDashboardFragment())
            }
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


