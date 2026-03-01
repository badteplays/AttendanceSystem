package com.example.attendancesystem

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.core.view.GravityCompat
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.widget.ImageView
import android.widget.TextView
import com.example.attendancesystem.utils.ProfilePictureManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class TeacherMainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 102
    }

    private var headerListener: ListenerRegistration? = null
    private var drawerLayout: androidx.drawerlayout.widget.DrawerLayout? = null
    private var navigationView: NavigationView? = null
    private var bottomNav: BottomNavigationView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_main)

        enableImmersiveMode()

        try {
            setupBottomNavigation()
            setupDrawerNavigation()
            requestCameraPermission()
            bindNavHeader()
        } catch (e: Exception) {
            android.util.Log.e("TeacherMainActivity", "Error in onCreate: ${e.message}", e)
        }

        if (savedInstanceState == null) {
            when (intent?.getStringExtra("open")) {
                "analytics" -> {
                    bottomNav?.selectedItemId = R.id.nav_analytics
                }
                "schedule" -> {
                    bottomNav?.selectedItemId = R.id.nav_schedule
                }
                "settings" -> {
                    load(TeacherOptionsFragment())
                }
                else -> load(TeacherDashboardFragment())
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
                R.id.nav_home -> { load(TeacherDashboardFragment()); true }
                R.id.nav_schedule -> { load(TeacherSchedulesFragment()); true }
                R.id.nav_analytics -> { load(TeacherAnalyticsFragment()); true }
                else -> false
            }
        }
    }

    private fun setupDrawerNavigation() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        val handle = findViewById<View>(R.id.drawerHandle)
        handle?.setOnClickListener { drawerLayout?.openDrawer(GravityCompat.END) }

        navigationView?.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.drawer_settings -> { load(TeacherOptionsFragment()); true }
                else -> false
            }.also { drawerLayout?.closeDrawers() }
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
                val name = snapshot.getString("name") ?: "Teacher"
                val department = snapshot.getString("department") ?: ""
                title.text = name
                subtitle.text = if (department.isNotBlank()) department else (user.email ?: "")
                if (profilePic != null && initials != null) {
                    ProfilePictureManager.getInstance().loadProfilePicture(this, profilePic, initials, name, "TC")
                }
            }
    }

    fun refreshNavHeader() {
        bindNavHeader()
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
            }
        }
    }

    override fun onDestroy() {
        headerListener?.remove()
        headerListener = null
        super.onDestroy()
    }
}
