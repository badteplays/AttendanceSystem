package com.example.attendancesystem

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import androidx.core.view.GravityCompat
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class TeacherMainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 102
    }

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
                R.id.drawer_analytics -> { load(TeacherAnalyticsFragment()); true }
                R.id.drawer_settings -> { load(TeacherOptionsFragment()); true }
                else -> false
            }.also { drawerLayout?.closeDrawers() }
        }

        requestCameraPermission()

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

            if (fragment !is TeacherDashboardFragment) {
                load(TeacherDashboardFragment())
            }
        }
    }

    private fun requestCameraPermission() {

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            android.util.Log.d("TeacherMainActivity", "Requesting camera permission")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                    this,
                    "Camera permission is needed to display QR codes for attendance",
                    Toast.LENGTH_LONG
                ).show()
                android.util.Log.w("TeacherMainActivity", "Camera permission denied")
            }
        }
    }
}


