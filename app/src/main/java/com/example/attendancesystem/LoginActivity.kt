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

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth

        // Check if user is already logged in
        if (auth.currentUser != null) {
            // If user is both teacher and student, prompt for role selection
            db.collection("users")
                .document(auth.currentUser!!.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (!document.exists()) {
                        auth.signOut()
                        Toast.makeText(this, "Account not found. Please sign up.", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }
                    val isTeacher = document.getBoolean("isTeacher") ?: false
                    val isStudent = document.getBoolean("isStudent") ?: true // Default to student if not set
                    if (isTeacher && isStudent) {
                        // User has both roles, prompt selection
                        startActivity(Intent(this, RoleSelectionActivity::class.java))
                        finish()
                    } else {
                        navigateToAppropriateScreen()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error loading user data", Toast.LENGTH_SHORT).show()
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

            // Show loading state
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
                                // Ensure user profile has correct role fields for Firestore rules
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
        db.collection("users")
            .document(auth.currentUser!!.uid)
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    // User does not exist in Firestore, sign out and return to login
                    auth.signOut()
                    Toast.makeText(this, "Account not found. Please sign up.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val isTeacher = document.getBoolean("isTeacher") ?: false
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
}