package com.example.attendancesystem

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.attendancesystem.databinding.ActivityLoginBinding
import com.example.attendancesystem.utils.KeyboardUtils
import com.example.attendancesystem.utils.NetworkUtils
import com.google.android.material.snackbar.Snackbar
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
        private const val PREF_LAST_ROLE = "last_selected_role"
        private const val PREF_LAST_EMAIL = "last_email"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth

        restoreLastSelections()
        setupKeyboardHandling()
        
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
                        binding.btnLogin.text = "Sign In"
                        showSnackbar("Account not found. Please sign up.")
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
                    binding.btnLogin.text = "Sign In"
                    showSnackbar("Error loading user data")
                    setupClickListeners()
                }
            return
        }

        setupClickListeners()
    }
    
    private fun restoreLastSelections() {
        val prefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val lastRole = prefs.getString(PREF_LAST_ROLE, "student")
        val lastEmail = prefs.getString(PREF_LAST_EMAIL, "")
        
        if (lastRole == "teacher") {
            binding.teacherRadio.isChecked = true
        } else {
            binding.studentRadio.isChecked = true
        }
        
        if (!lastEmail.isNullOrEmpty()) {
            binding.editEmail.setText(lastEmail)
        }
    }
    
    private fun saveLastSelections(email: String, isTeacher: Boolean) {
        getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_LAST_ROLE, if (isTeacher) "teacher" else "student")
            .putString(PREF_LAST_EMAIL, email)
            .apply()
    }
    
    private fun setupKeyboardHandling() {
        binding.scrollView.setOnTouchListener { _, _ ->
            KeyboardUtils.hideKeyboard(this)
            false
        }
        
        binding.editEmail.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.editPassword.requestFocus()
                true
            } else false
        }
        
        binding.editPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                KeyboardUtils.hideKeyboard(this)
                attemptLogin()
                true
            } else false
        }
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            attemptLogin()
        }

        binding.txtSignup.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }
    
    private fun attemptLogin() {
        KeyboardUtils.hideKeyboard(this)
        
        val email = binding.editEmail.text.toString().trim()
        val password = binding.editPassword.text.toString().trim()
        val isTeacher = binding.teacherRadio.isChecked
        
        binding.textInputEmail.error = null
        binding.textInputPassword.error = null

        var hasError = false
        if (email.isEmpty()) {
            binding.textInputEmail.error = "Email is required"
            hasError = true
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.textInputEmail.error = "Enter a valid email"
            hasError = true
        }
        
        if (password.isEmpty()) {
            binding.textInputPassword.error = "Password is required"
            hasError = true
        } else if (password.length < 6) {
            binding.textInputPassword.error = "Password must be at least 6 characters"
            hasError = true
        }
        
        if (hasError) return
        
        if (!NetworkUtils.isNetworkAvailable(this)) {
            showSnackbar("No internet connection")
            return
        }

        showLoading(true)
        saveLastSelections(email, isTeacher)

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
                            showSnackbar("Incorrect role selected for this account")
                        }
                    }
                    .addOnFailureListener { e ->
                        showLoading(false)
                        showSnackbar("Failed to fetch user info: ${e.localizedMessage}")
                    }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                val message = when {
                    e.message?.contains("no user record", ignoreCase = true) == true -> 
                        "No account found with this email"
                    e.message?.contains("password is invalid", ignoreCase = true) == true -> 
                        "Incorrect password"
                    e.message?.contains("badly formatted", ignoreCase = true) == true -> 
                        "Invalid email format"
                    e.message?.contains("network", ignoreCase = true) == true -> 
                        "Network error. Please try again"
                    else -> "Login failed: ${e.localizedMessage}"
                }
                showSnackbar(message)
            }
    }

    private fun showLoading(show: Boolean) {
        binding.btnLogin.isEnabled = !show
        binding.txtSignup.isEnabled = !show
        binding.editEmail.isEnabled = !show
        binding.editPassword.isEnabled = !show
        binding.teacherRadio.isEnabled = !show
        binding.studentRadio.isEnabled = !show

        binding.btnLogin.text = if (show) "Signing in..." else "Sign In"
    }
    
    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(getColor(R.color.surface_container_high))
            .setTextColor(getColor(R.color.text_primary))
            .show()
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
            showSnackbar("Session expired. Please sign in again.")
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
                    showSnackbar("Account not found. Please sign up.")
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
                showSnackbar("Error loading user data")
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
                android.util.Log.d("LoginActivity", "Background class reminders scheduled")
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
            val deniedPermissions = permissions.filterIndexed { index, _ ->
                grantResults[index] != PackageManager.PERMISSION_GRANTED
            }

            if (deniedPermissions.isNotEmpty()) {
                val message = when {
                    Manifest.permission.CAMERA in deniedPermissions &&
                    Manifest.permission.POST_NOTIFICATIONS in deniedPermissions -> 
                        "Camera and notifications needed for full functionality"
                    Manifest.permission.CAMERA in deniedPermissions -> 
                        "Camera needed to scan QR codes"
                    Manifest.permission.POST_NOTIFICATIONS in deniedPermissions -> 
                        "Notifications needed for class reminders"
                    else -> "Some permissions were denied"
                }
                showSnackbar(message)
            }
        }
    }
}
