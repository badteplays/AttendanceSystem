package com.example.attendancesystem

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.attendancesystem.databinding.ActivityTeacherScheduleBinding

class TeacherScheduleActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTeacherScheduleBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeacherScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupBottomNavigation()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Schedule"
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // Navigate to home
                    true
                }
                R.id.nav_schedule -> {
                    // Already on schedule
                    true
                }
                R.id.nav_attendance -> {
                    // Navigate to attendance
                    true
                }
                R.id.nav_analytics -> {
                    // Navigate to analytics
                    true
                }
                R.id.nav_settings -> {
                    // Navigate to settings
                    true
                }
                else -> false
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}