package com.example.attendancesystem

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RoleSelectionActivity : AppCompatActivity() {
    private lateinit var teacherBtn: Button
    private lateinit var studentBtn: Button
    private lateinit var welcomeText: TextView
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_selection)

        teacherBtn = findViewById(R.id.teacherContainer)
        studentBtn = findViewById(R.id.studentContainer)
        welcomeText = findViewById(R.id.textWelcome)

        val user = auth.currentUser
        if (user == null) {
            finish()
            return
        }

        db.collection("users").document(user.uid).get().addOnSuccessListener { doc ->
            val name = doc.getString("name") ?: "User"
            welcomeText.text = "Welcome, $name! Choose your role:"
        }

        teacherBtn.setOnClickListener {
            val intent = Intent(this, TeacherMainActivity::class.java)
            startActivity(intent)
            finish()
        }
        studentBtn.setOnClickListener {
            val intent = Intent(this, StudentMainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
