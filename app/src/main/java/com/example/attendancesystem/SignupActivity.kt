package com.example.attendancesystem

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.attendancesystem.databinding.ActivitySignupBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import androidx.work.WorkManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import android.content.Context
import com.example.attendancesystem.security.SecurityUtils

class SignupActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignupBinding
    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth

        val sectionLayout = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.textInputSection)
        val departmentLayout = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.textInputDepartment)
        val teacherRadio = findViewById<android.widget.RadioButton>(R.id.teacherRadio)
        val studentRadio = findViewById<android.widget.RadioButton>(R.id.studentRadio)
        val radioGroup = findViewById<android.widget.RadioGroup>(R.id.radioGroup1)

        fun updateRoleFields() {
            if (teacherRadio.isChecked) {
                departmentLayout.visibility = android.view.View.VISIBLE
                sectionLayout.visibility = android.view.View.GONE
            } else {
                departmentLayout.visibility = android.view.View.GONE
                sectionLayout.visibility = android.view.View.VISIBLE
            }
        }
        updateRoleFields()
        radioGroup.setOnCheckedChangeListener { _, _ ->
            updateRoleFields()
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnSignup.setOnClickListener {
            val email = binding.editEmail.text.toString().trim()
            val name = binding.editName.text.toString().trim()
            val section = binding.editSection.text.toString().trim()
            val department = binding.editDepartment.text.toString().trim()
            val password = binding.editPassword.text.toString().trim()
            val confirmPassword = binding.editConfirmPassword.text.toString().trim()
            val selectedRole = if (findViewById<android.widget.RadioButton>(R.id.teacherRadio).isChecked) "teacher" else "student"

            // Input validation with security checks
            if (!SecurityUtils.isValidEmail(email)) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!SecurityUtils.isValidName(name)) {
                Toast.makeText(this, "Please enter a valid name (letters only)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!SecurityUtils.isValidPassword(password)) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
<<<<<<< HEAD
            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
=======
>>>>>>> origin/master
            if (selectedRole == "student" && !SecurityUtils.isValidSection(section)) {
                Toast.makeText(this, "Please enter a valid section (e.g., BSIT-3A)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedRole == "teacher" && department.isEmpty()) {
                Toast.makeText(this, "Please enter your department/role", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Sanitize inputs
            val sanitizedEmail = SecurityUtils.sanitizeEmail(email)
            val sanitizedName = SecurityUtils.sanitizeString(name)
            val sanitizedSection = SecurityUtils.sanitizeSection(section)

            binding.btnSignup.isEnabled = false
            binding.btnSignup.text = "Creating Account..."
            
            auth.createUserWithEmailAndPassword(sanitizedEmail, password)
                .addOnSuccessListener { result ->
                    val createdUser = result.user
                    if (createdUser == null) {
                        binding.btnSignup.isEnabled = true
                        binding.btnSignup.text = "Sign Up"
                        Toast.makeText(this, "Signup failed. Please try again.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }
                    val user = hashMapOf(
                        "email" to sanitizedEmail,
                        "name" to sanitizedName,
                        "role" to selectedRole,
                        "isTeacher" to (selectedRole == "teacher"),
                        "isStudent" to (selectedRole == "student"),
                        "createdAt" to System.currentTimeMillis()
                    )
                    if (selectedRole == "student") {
                        user["section"] = sanitizedSection
                    } else if (selectedRole == "teacher") {
                        user["department"] = SecurityUtils.sanitizeString(department)
                    }
                    db.collection("users")
                        .document(createdUser.uid)
                        .set(user)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show()
                            
                            if (selectedRole == "student") {
                                scheduleStudentReminders()
                            }
                            
                            getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean("explicit_logout", false)
                                .apply()
                            
                            val intent = if (selectedRole == "teacher") {
                                Intent(this, TeacherMainActivity::class.java)
                            } else {
                                Intent(this, StudentMainActivity::class.java)
                            }
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            binding.btnSignup.isEnabled = true
                            binding.btnSignup.text = "Sign Up"
                            Toast.makeText(this, "Failed to save user info: ${e.message}", Toast.LENGTH_LONG).show()
                            createdUser.delete()
                        }
                }
                .addOnFailureListener { e ->
                    binding.btnSignup.isEnabled = true
                    binding.btnSignup.text = "Sign Up"
                    
                    val errorMessage = when {
                        e.message?.contains("already in use", ignoreCase = true) == true -> 
                            "This email is already registered. Please login or use a different email."
                        e.message?.contains("badly formatted", ignoreCase = true) == true -> 
                            "Invalid email format. Please check your email address."
                        e.message?.contains("weak password", ignoreCase = true) == true -> 
                            "Password is too weak. Please use at least 6 characters."
                        else -> "Signup failed: ${e.message}"
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
        }

        binding.txtLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
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
                
                android.util.Log.d("SignupActivity", "Background class reminders scheduled (runs even when app is closed)")
            }
        } catch (e: Exception) {
            android.util.Log.e("SignupActivity", "Error scheduling reminders: ${e.message}", e)
        }
    }
}
