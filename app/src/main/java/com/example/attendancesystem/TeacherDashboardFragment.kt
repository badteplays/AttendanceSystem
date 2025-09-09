package com.example.attendancesystem

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.attendancesystem.utils.ProfilePictureManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class TeacherDashboardFragment : Fragment() {
    private lateinit var imageProfilePic: ImageView
    private lateinit var textInitials: TextView
    private lateinit var textName: TextView
    private lateinit var textDepartment: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_teacher_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imageProfilePic = view.findViewById(R.id.imageProfilePic)
        textInitials = view.findViewById(R.id.textInitials)
        textName = view.findViewById(R.id.textName)
        textDepartment = view.findViewById(R.id.textDepartment)

        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val currentUser = auth.currentUser
        currentUser?.let {
            db.collection("users").document(it.uid)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null && snapshot.exists()) {
                        val name = snapshot.getString("name") ?: "Teacher"
                        val department = snapshot.getString("department") ?: "Department"
                        textName.text = name
                        textDepartment.text = department
                        ProfilePictureManager.getInstance().loadProfilePicture(requireContext(), imageProfilePic, textInitials, name, "TC")
                    }
                }
        }
        
        view.findViewById<View>(R.id.buttonAnalytics)?.setOnClickListener {
            startActivity(Intent(requireContext(), AnalyticsActivity::class.java))
        }
    }
}


