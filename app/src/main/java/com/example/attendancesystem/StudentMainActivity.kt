package com.example.attendancesystem

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.attendancesystem.notifications.LocalNotificationManager
import com.example.attendancesystem.utils.ProfilePictureManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class StudentMainActivity : AppCompatActivity() {

    private var drawerLayout: DrawerLayout? = null
    private var navigationView: NavigationView? = null
    private var bottomNav: BottomNavigationView? = null
    private var notificationManager: LocalNotificationManager? = null
    private var headerListener: ListenerRegistration? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_main)

        enableImmersiveMode()

        try {
            notificationManager = LocalNotificationManager.getInstance(this)
            setupBottomNavigation()
            setupDrawerNavigation()
            requestNecessaryPermissions()
            bindNavHeader()
        } catch (e: Exception) {
            android.util.Log.e("StudentMainActivity", "Error in onCreate: ${e.message}", e)
        }

        if (savedInstanceState == null) {
            val fragmentToLoad = when (intent.getStringExtra("fragment")) {
                "schedule" -> { bottomNav?.selectedItemId = R.id.nav_calendar; StudentScheduleFragment() }
                "routines" -> StudentRoutinesFragment()
                "profile" -> StudentOptionsFragment()
                else -> StudentDashboardFragment()
            }
            loadFragment(fragmentToLoad)
        }

        scheduleClassNotifications()
        requestBatteryOptimizationExemption()
        requestExactAlarmPermission()
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) { }
            }
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) { }
            }
        }
    }

    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        findViewById<View>(R.id.fragmentContainer)?.let { container ->
            ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
                val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                view.setPadding(0, statusBar.top, 0, 0)
                insets
            }
        }

        findViewById<View>(R.id.drawerHandle)?.let { handle ->
            ViewCompat.setOnApplyWindowInsetsListener(handle) { view, insets ->
                val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                val lp = view.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                lp.topMargin = statusBar.top + 16
                view.layoutParams = lp
                insets
            }
        }

        findViewById<BottomNavigationView>(R.id.bottomNav)?.let { nav ->
            ViewCompat.setOnApplyWindowInsetsListener(nav) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(0, 0, 0, systemBars.bottom)
                insets
            }
        }
    }

    private fun setupBottomNavigation() {
        bottomNav = findViewById(R.id.bottomNav)
        bottomNav?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { loadFragment(StudentDashboardFragment()); true }
                R.id.nav_calendar -> { loadFragment(StudentScheduleFragment()); true }
                R.id.nav_routines -> { loadFragment(StudentRoutinesFragment()); true }
                else -> false
            }
        }

        findViewById<View>(R.id.globalScanFab)?.setOnClickListener {
            loadFragment(QRScannerFragment())
        }
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
                R.id.drawer_routines -> { loadFragment(StudentRoutinesFragment()); true }
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

    fun refreshNavHeader() {
        bindNavHeader()
    }

    fun navigateToDashboard() {
        bottomNav?.selectedItemId = R.id.nav_home
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

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
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
                    deniedPermissions.contains(Manifest.permission.CAMERA) -> {
                        "Camera permission is needed to scan QR codes for attendance"
                    }
                    deniedPermissions.contains(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                        "Location permission may be needed for QR validation"
                    }
                    deniedPermissions.contains(Manifest.permission.POST_NOTIFICATIONS) -> {
                        "Notification permission is recommended for class reminders"
                    }
                    else -> "Some permissions were denied"
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            } else {
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

        try {
            val workRequest = PeriodicWorkRequestBuilder<StudentReminderWorker>(
                15, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "student_reminder_work",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        } catch (e: Exception) {
            android.util.Log.e("StudentMainActivity", "Error scheduling worker: ${e.message}", e)
        }
    }
}
