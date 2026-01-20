package com.example.attendancesystem

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.widget.Toast
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.attendancesystem.notifications.LocalNotificationManager
import com.example.attendancesystem.utils.ProfilePictureManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StudentMainActivity : AppCompatActivity() {

    private var drawerLayout: DrawerLayout? = null
    private var navigationView: NavigationView? = null
    private var notificationManager: LocalNotificationManager? = null
    private var headerListener: ListenerRegistration? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_main)

        try {
        notificationManager = LocalNotificationManager.getInstance(this)
        setupDrawerNavigation()
        requestNecessaryPermissions()
        bindNavHeader()
        } catch (e: Exception) {
            android.util.Log.e("StudentMainActivity", "Error in onCreate: ${e.message}", e)
        }

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
        
        scheduleClassNotifications()
    }

    private fun setupDrawerNavigation() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        val drawerHandle = findViewById<View>(R.id.drawerHandle)

        drawerHandle?.setOnClickListener {
            val nav = navigationView ?: return@setOnClickListener
            val drawer = drawerLayout ?: return@setOnClickListener
            if (drawer.isDrawerOpen(nav)) {
                drawer.closeDrawer(nav)
            } else {
                drawer.openDrawer(nav)
            }
        }

        navigationView?.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.drawer_home -> { loadFragment(StudentDashboardFragment()); true }
                R.id.drawer_scan -> { loadFragment(QRScannerFragment()); true }
                R.id.drawer_schedule -> { loadFragment(StudentScheduleFragment()); true }
                R.id.drawer_routines -> { loadFragment(StudentRoutinesFragment()); true }
                R.id.drawer_history -> { loadFragment(StudentAttendanceHistoryFragment()); true }
                R.id.drawer_profile -> { loadFragment(StudentOptionsFragment()); true }
                else -> false
            }.also { if (it) drawerLayout?.closeDrawer(navigationView!!) }
        }
    }

    private fun bindNavHeader() {
        val nav = navigationView ?: return
        val headerView = nav.getHeaderView(0) ?: return
        val title = headerView.findViewById<TextView>(R.id.navHeaderTitle) ?: return
        val subtitle = headerView.findViewById<TextView>(R.id.navHeaderSubtitle) ?: return
        val profilePic = headerView.findViewById<ImageView>(R.id.navHeaderProfilePic)
        val initials = headerView.findViewById<TextView>(R.id.navHeaderInitials)

        val user = FirebaseAuth.getInstance().currentUser ?: return
        subtitle.text = user.email ?: ""

        headerListener?.remove()
        headerListener = FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val name = snapshot.getString("name") ?: "Student"
                val section = snapshot.getString("section") ?: ""
                title.text = name
                subtitle.text = if (section.isNotBlank()) "Section ${section.uppercase()}" else (user.email ?: "")
                if (profilePic != null && initials != null) {
                    ProfilePictureManager.getInstance().loadProfilePicture(this, profilePic, initials, name, "ST")
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
        navigationView?.setCheckedItem(R.id.drawer_home)
        loadFragment(StudentDashboardFragment())
    }

    override fun onBackPressed() {
        val nav = navigationView
        val drawer = drawerLayout
        if (nav != null && drawer != null && drawer.isDrawerOpen(nav)) {
            drawer.closeDrawer(nav)
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
            } else {
                // All permissions granted, reschedule notifications
                scheduleClassNotifications()
            }
        }
    }

    override fun onDestroy() {
        headerListener?.remove()
        headerListener = null
        super.onDestroy()
    }
    
    private fun scheduleClassNotifications() {
        val prefs = getSharedPreferences("student_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications_enabled", true)) return
        
        val mgr = notificationManager ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                mgr.scheduleAllClassNotifications()
            } catch (e: Exception) {
                android.util.Log.e("StudentMainActivity", "Error scheduling notifications: ${e.message}", e)
            }
        }
    }
}
