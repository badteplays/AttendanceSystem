package com.example.attendancesystem

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.attendancesystem.utils.ProfilePictureManager

class StudentOptionsFragment : Fragment() {
    private lateinit var textUserName: TextView
    private lateinit var textUserEmail: TextView
    private lateinit var chipSection: TextView
    private lateinit var imageProfilePic: ImageView
    private lateinit var textInitials: TextView
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_student_options, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeViews(view)
        loadUserProfile()
        setupClickListeners(view)
    }

    private fun initializeViews(view: View) {
        textUserName = view.findViewById(R.id.textUserName)
        textUserEmail = view.findViewById(R.id.textUserEmail)
        chipSection = view.findViewById(R.id.chipSection)
        imageProfilePic = view.findViewById(R.id.imageProfilePic)
        textInitials = view.findViewById(R.id.textInitials)
    }

    private fun loadUserProfile() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Set email
            textUserEmail.text = currentUser.email ?: "No email"
            
            // Load user data from Firestore
            db.collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val name = document.getString("name") ?: "Student"
                        val section = document.getString("section") ?: "N/A"
                        
                        textUserName.text = name
                        chipSection.text = "Section $section"
                        
                        // Use ProfilePictureManager to load profile picture
                        val profileManager = ProfilePictureManager.getInstance()
                        profileManager.loadProfilePicture(requireContext(), imageProfilePic, textInitials, name, "ST")
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun setupClickListeners(view: View) {
        // Logout option
        view.findViewById<LinearLayout>(R.id.buttonLogout).setOnClickListener {
            performLogout()
        }
    }

    private fun performLogout() {
        auth.signOut()
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }
}
