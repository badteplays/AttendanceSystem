package com.example.attendancesystem

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import android.widget.ImageView
import android.content.Context
import androidx.work.WorkManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class StudentMainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    companion object {
        private const val PERMISSION_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_main)

        setupDrawerNavigation()

        requestNecessaryPermissions()

        if (savedInstanceState == null) {
            val fragmentToLoad = when (intent.getStringExtra("fragment")) {
                "schedule" -> StudentScheduleFragment()
                "routines" -> StudentRoutinesFragment()
                "history" -> StudentAttendanceHistoryFragment()
                "profile" -> StudentOptionsFragment()
                else -> StudentDashboardFragment()
            }
            loadFragment(fragmentToLoad)
        }
    }

    private fun setupDrawerNavigation() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById<NavigationView>(R.id.navigationView)
        val drawerHandle = findViewById<ImageView>(R.id.drawerHandle)

        drawerHandle.setOnClickListener {
            if (drawerLayout.isDrawerOpen(navigationView)) {
                drawerLayout.closeDrawer(navigationView)
            } else {
                drawerLayout.openDrawer(navigationView)
            }
        }

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

            if (fragment !is StudentDashboardFragment) {
                loadFragment(StudentDashboardFragment())
            }
        }
    }

    fun navigateToDashboard() {
        navigationView.setCheckedItem(R.id.drawer_home)
        loadFragment(StudentDashboardFragment())
    }

    override fun onBackPressed() {
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        if (drawerLayout.isDrawerOpen(navigationView)) {
            drawerLayout.closeDrawer(navigationView)
        } else {
            super.onBackPressed()
        }
    }

    private fun requestNecessaryPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            android.util.Log.d("StudentMainActivity", "Requesting permissions: ${permissionsToRequest.joinToString()}")
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
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
            val deniedPermissions = mutableListOf<String>()

            permissions.forEachIndexed { index, permission ->
                if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permission)
                }
            }

            if (deniedPermissions.isNotEmpty()) {
                val message = when {
                    deniedPermissions.contains(Manifest.permission.CAMERA) &&
                    deniedPermissions.contains(Manifest.permission.POST_NOTIFICATIONS) -> {
                        "Camera and notification permissions are recommended for full functionality"
                    }
                    deniedPermissions.contains(Manifest.permission.CAMERA) -> {
                        "Camera permission is needed to scan QR codes for attendance"
                    }
                    deniedPermissions.contains(Manifest.permission.POST_NOTIFICATIONS) -> {
                        "Notification permission is recommended for class reminders"
                    }
                    else -> "Some permissions were denied"
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                android.util.Log.w("StudentMainActivity", "Denied permissions: ${deniedPermissions.joinToString()}")
            }
        }
    }
}
