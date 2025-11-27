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

class SignupActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignupBinding
    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth

        // Show/hide Section or Department field based on radio button
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
            val department = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editDepartment).text.toString().trim()
            val password = binding.editPassword.text.toString().trim()
            val selectedRole = if (findViewById<android.widget.RadioButton>(R.id.teacherRadio).isChecked) "teacher" else "student"

            if (email.isEmpty() || name.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedRole == "student" && section.isEmpty()) {
                Toast.makeText(this, "Please enter your section", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedRole == "teacher" && department.isEmpty()) {
                Toast.makeText(this, "Please enter your department/role", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show loading
            binding.btnSignup.isEnabled = false
            binding.btnSignup.text = "Creating Account..."
            
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val user = hashMapOf(
                        "email" to email,
                        "name" to name,
                        "role" to selectedRole,
                        // Add explicit role booleans for Firestore rules
                        "isTeacher" to (selectedRole == "teacher"),
                        "isStudent" to (selectedRole == "student"),
                        "createdAt" to System.currentTimeMillis()
                    )
                    if (selectedRole == "student") {
                        user["section"] = section.uppercase() // Normalize to uppercase
                    } else if (selectedRole == "teacher") {
                        user["department"] = department
                    }
                    db.collection("users")
                        .document(result.user!!.uid)
                        .set(user)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show()
                            if (selectedRole == "teacher") {
                                startActivity(Intent(this, TeacherMainActivity::class.java))
                            } else {
                                startActivity(Intent(this, StudentMainActivity::class.java))
                            }
                            finish()
                        }
                        .addOnFailureListener { e ->
                            binding.btnSignup.isEnabled = true
                            binding.btnSignup.text = "Sign Up"
                            Toast.makeText(this, "Failed to save user info: ${e.message}", Toast.LENGTH_LONG).show()
                            // Delete the auth user since Firestore save failed
                            result.user?.delete()
                        }
                }
                .addOnFailureListener { e ->
                    binding.btnSignup.isEnabled = true
                    binding.btnSignup.text = "Sign Up"
                    
                    // Better error messages
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
}
