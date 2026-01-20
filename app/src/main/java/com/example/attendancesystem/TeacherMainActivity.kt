package com.example.attendancesystem

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import androidx.core.view.GravityCompat
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_main)

        try {
            drawerLayout = findViewById(R.id.drawerLayout)
            navigationView = findViewById(R.id.navigationView)
            val handle = findViewById<View>(R.id.drawerHandle)
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
            bindNavHeader()
        } catch (e: Exception) {
            android.util.Log.e("TeacherMainActivity", "Error in onCreate: ${e.message}", e)
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

    override fun onDestroy() {
        headerListener?.remove()
        headerListener = null
        super.onDestroy()
    }
}


