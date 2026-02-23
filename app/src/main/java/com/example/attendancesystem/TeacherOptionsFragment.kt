package com.example.attendancesystem

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.attendancesystem.utils.ThemeManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth

class TeacherOptionsFragment : Fragment() {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private var currentName: String = "Teacher"

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                try {
                    val profileManager = com.example.attendancesystem.utils.ProfilePictureManager.getInstance()
                    val success = profileManager.saveProfilePicture(requireContext(), uri)
                    if (success) {
                        Toast.makeText(requireContext(), "Profile picture updated", Toast.LENGTH_SHORT).show()

                        val imageView = view?.findViewById<ImageView>(R.id.imageProfilePic)
                        val initialsView = view?.findViewById<TextView>(R.id.textInitials)
                        if (imageView != null && initialsView != null) {
                            profileManager.loadProfilePicture(
                                requireContext(),
                                imageView,
                                initialsView,
                                currentName,
                                "TC"
                            )
                        }
                    } else {
                        Toast.makeText(requireContext(), "Failed to save profile picture", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_teacher_options, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<LinearLayout>(R.id.buttonTheme)?.setOnClickListener {
            showThemePicker()
        }

        view.findViewById<LinearLayout>(R.id.buttonAboutUs)?.setOnClickListener {
            val url = "https://badteplays.github.io/FPL-WEBSITE/website.html"
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Unable to open link", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<LinearLayout>(R.id.buttonLogout)?.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout") { _, _ ->

                    val prefs = requireContext().getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("explicit_logout", true).apply()

                    auth.signOut()
                    val intent = Intent(requireContext(), LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabChangePhoto)?.setOnClickListener {
            openImagePicker()
        }
        view.findViewById<ImageView>(R.id.imageProfilePic)?.setOnClickListener { openImagePicker() }
        view.findViewById<TextView>(R.id.textInitials)?.setOnClickListener { openImagePicker() }

        val themeManager = ThemeManager.getInstance(requireContext())
        view.findViewById<TextView>(R.id.textCurrentTheme)?.text = themeManager.getThemeName(themeManager.getCurrentTheme())

        val textName = view.findViewById<TextView>(R.id.textUserName)
        val textEmail = view.findViewById<TextView>(R.id.textUserEmail)
        val imageProfile = view.findViewById<ImageView>(R.id.imageProfilePic)
        val textInitials = view.findViewById<TextView>(R.id.textInitials)

        val currentUser = auth.currentUser
        if (currentUser != null) {
            textEmail?.text = currentUser.email ?: "No email"

            FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUser.uid)
                .addSnapshotListener { snapshot, _ ->
                    currentName = snapshot?.getString("name") ?: (currentUser.displayName ?: "Teacher")
                    textName?.text = currentName
                    try {
                        val profileManager = com.example.attendancesystem.utils.ProfilePictureManager.getInstance()
                        profileManager.loadProfilePicture(requireContext(), imageProfile ?: return@addSnapshotListener, textInitials ?: return@addSnapshotListener, currentName, "TC")
                    } catch (_: Exception) { }
                }
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        imagePickerLauncher.launch(intent)
    }

    private fun showThemePicker() {
        val themeManager = ThemeManager.getInstance(requireContext())
        val themes = arrayOf("Light", "Dark", "System")
        val current = themeManager.getCurrentTheme()
        AlertDialog.Builder(requireContext())
            .setTitle("Choose theme")
            .setSingleChoiceItems(themes, current) { dialog, which ->
                val mode = when (which) {
                    0 -> ThemeManager.THEME_LIGHT
                    1 -> ThemeManager.THEME_DARK
                    else -> ThemeManager.THEME_SYSTEM
                }
                themeManager.setTheme(mode)
                view?.findViewById<TextView>(R.id.textCurrentTheme)?.text = themeManager.getThemeName(mode)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}


