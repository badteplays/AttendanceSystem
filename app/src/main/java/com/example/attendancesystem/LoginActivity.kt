package com.example.attendancesystem

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.attendancesystem.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import androidx.work.WorkManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth

        val prefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val explicitLogout = prefs.getBoolean("explicit_logout", false)

        val currentUser = auth.currentUser
        if (currentUser != null && !explicitLogout) {
            android.util.Log.d("LoginActivity", "User already logged in: ${currentUser.uid}")

            binding.btnLogin.isEnabled = false
            binding.btnLogin.text = "Checking session..."

            db.collection("users")
                .document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (!document.exists()) {
                        android.util.Log.e("LoginActivity", "User document not found, signing out")
                        auth.signOut()
                        binding.btnLogin.isEnabled = true
                        binding.btnLogin.text = "Login"
                        Toast.makeText(this, "Account not found. Please sign up.", Toast.LENGTH_SHORT).show()

                        setupClickListeners()
                        return@addOnSuccessListener
                    }

                    val isTeacher = document.getBoolean("isTeacher") ?: false
                    val isStudent = document.getBoolean("isStudent") ?: true

                    android.util.Log.d("LoginActivity", "User role - Teacher: $isTeacher, Student: $isStudent")

                    if (isTeacher && isStudent) {

                        android.util.Log.d("LoginActivity", "User has both roles, showing role selection")
                        startActivity(Intent(this, RoleSelectionActivity::class.java))
                        finish()
                    } else {

                        android.util.Log.d("LoginActivity", "Auto-navigating to dashboard")
                        navigateToAppropriateScreen()
                    }
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("LoginActivity", "Error loading user data: ${e.message}", e)
                    binding.btnLogin.isEnabled = true
                    binding.btnLogin.text = "Login"
                    Toast.makeText(this, "Error loading user data", Toast.LENGTH_SHORT).show()

                    setupClickListeners()
                }
            return
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.editEmail.text.toString().trim()
            val password = binding.editPassword.text.toString().trim()
            val isTeacher = binding.teacherRadio.isChecked

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showLoading(true)

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    db.collection("users")
                        .document(result.user!!.uid)
                        .get()
                        .addOnSuccessListener { doc ->
                            showLoading(false)
                            val userIsTeacher = doc.getBoolean("isTeacher") ?: false
                            if (userIsTeacher == isTeacher) {

                                ensureUserProfile(if (isTeacher) "teacher" else "student")
                                navigateToAppropriateScreen()
                            } else {
                                auth.signOut()
                                showError("Incorrect role selected for this account")
                            }
                        }
                        .addOnFailureListener { e ->
                            showLoading(false)
                            showError("Failed to fetch user info: ${e.localizedMessage}")
                        }
                }
                .addOnFailureListener { e ->
                    showLoading(false)
                    showError("Login failed: ${e.localizedMessage}")
                }
        }

        binding.txtSignup.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    private fun showLoading(show: Boolean) {
        binding.btnLogin.isEnabled = !show
        binding.txtSignup.isEnabled = !show
        binding.editEmail.isEnabled = !show
        binding.editPassword.isEnabled = !show
        binding.teacherRadio.isEnabled = !show
        binding.studentRadio.isEnabled = !show

        if (show) {
            binding.btnLogin.text = "Logging in..."
        } else {
            binding.btnLogin.text = "Login"
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun ensureUserProfile(role: String) {
        val user = auth.currentUser ?: return
        val userData = hashMapOf(
            "email" to user.email,
            "displayName" to user.displayName,
            "isTeacher" to (role == "teacher"),
            "isStudent" to (role == "student")
        )
        db.collection("users").document(user.uid)
            .set(userData, com.google.firebase.firestore.SetOptions.merge())
    }

    private fun navigateToAppropriateScreen() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            auth.signOut()
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("explicit_logout", false).apply()

        db.collection("users")
            .document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {

                    auth.signOut()
                    Toast.makeText(this, "Account not found. Please sign up.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val isTeacher = document.getBoolean("isTeacher") ?: false

                if (!isTeacher) {
                    scheduleStudentReminders()
                }

                requestNecessaryPermissions(isTeacher)

                val intent = if (isTeacher) {
                    Intent(this, TeacherMainActivity::class.java)
                } else {
                    Intent(this, StudentMainActivity::class.java)
                }
                startActivity(intent)
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading user data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun scheduleStudentReminders() {
        try {
            val prefs = getSharedPreferences("student_prefs", Context.MODE_PRIVATE)
            val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)

            if (notificationsEnabled) {


                val workRequest = PeriodicWorkRequestBuilder<StudentReminderWorker>(
                    15, TimeUnit.MINUTES
                ).build()

                WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    "student_reminder_work",
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )

                android.util.Log.d("LoginActivity", "Background class reminders scheduled (runs even when app is closed)")
            }
        } catch (e: Exception) {
            android.util.Log.e("LoginActivity", "Error scheduling reminders: ${e.message}", e)
        }
    }

    private fun requestNecessaryPermissions(isTeacher: Boolean) {
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
            android.util.Log.d("LoginActivity", "Requesting permissions: ${permissionsToRequest.joinToString()}")
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
                        "Camera and notification permissions are needed for full app functionality"
                    }
                    deniedPermissions.contains(Manifest.permission.CAMERA) -> {
                        "Camera permission is needed to scan QR codes for attendance"
                    }
                    deniedPermissions.contains(Manifest.permission.POST_NOTIFICATIONS) -> {
                        "Notification permission is needed for class reminders"
                    }
                    else -> "Some permissions were denied"
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                android.util.Log.w("LoginActivity", "Denied permissions: ${deniedPermissions.joinToString()}")
            } else {
                android.util.Log.d("LoginActivity", "All permissions granted")
                Toast.makeText(this, "Permissions granted successfully", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
