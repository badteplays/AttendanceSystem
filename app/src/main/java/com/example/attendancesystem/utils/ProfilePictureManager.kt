package com.example.attendancesystem.utils

import android.content.Context
import android.net.Uri
import android.widget.ImageView
import android.widget.TextView
import android.view.View
import com.bumptech.glide.Glide
import com.example.attendancesystem.R
import com.google.firebase.auth.FirebaseAuth

class ProfilePictureManager private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: ProfilePictureManager? = null
        
        fun getInstance(): ProfilePictureManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProfilePictureManager().also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Save profile picture URI locally
     */
    fun saveProfilePicture(context: Context, imageUri: Uri): Boolean {
        return try {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                val sharedPrefs = context.getSharedPreferences("profile_pics", Context.MODE_PRIVATE)
                sharedPrefs.edit()
                    .putString("profile_pic_${currentUser.uid}", imageUri.toString())
                    .apply()
                android.util.Log.d("ProfilePictureManager", "Saved profile picture for user: ${currentUser.uid}")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("ProfilePictureManager", "Failed to save profile picture", e)
            false
        }
    }
    
    /**
     * Load profile picture into ImageView with fallback to initials
     */
    fun loadProfilePicture(
        context: Context,
        imageView: ImageView,
        initialsTextView: TextView,
        userName: String,
        defaultInitials: String = "U"
    ) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            showInitials(initialsTextView, imageView, userName, defaultInitials)
            return
        }
        
        // Check for local profile picture first
        val sharedPrefs = context.getSharedPreferences("profile_pics", Context.MODE_PRIVATE)
        val localProfilePicUri = sharedPrefs.getString("profile_pic_${currentUser.uid}", null)
        
        if (!localProfilePicUri.isNullOrEmpty()) {
            try {
                // Load local profile picture
                initialsTextView.visibility = View.GONE
                imageView.visibility = View.VISIBLE
                
                Glide.with(context)
                    .load(Uri.parse(localProfilePicUri))
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(imageView)
                    
                android.util.Log.d("ProfilePictureManager", "Loaded local profile picture for user: ${currentUser.uid}")
            } catch (e: Exception) {
                android.util.Log.e("ProfilePictureManager", "Failed to load local profile picture", e)
                showInitials(initialsTextView, imageView, userName, defaultInitials)
            }
        } else {
            // No local profile picture, show initials
            showInitials(initialsTextView, imageView, userName, defaultInitials)
        }
    }
    
    /**
     * Show user initials instead of profile picture
     */
    private fun showInitials(
        initialsTextView: TextView,
        imageView: ImageView,
        userName: String,
        defaultInitials: String
    ) {
        imageView.visibility = View.GONE
        initialsTextView.visibility = View.VISIBLE
        
        val initials = userName.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .uppercase()
            
        initialsTextView.text = if (initials.isEmpty()) defaultInitials else initials
    }
    
    /**
     * Check if user has a local profile picture
     */
    fun hasLocalProfilePicture(context: Context): Boolean {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return false
        val sharedPrefs = context.getSharedPreferences("profile_pics", Context.MODE_PRIVATE)
        val localProfilePicUri = sharedPrefs.getString("profile_pic_${currentUser.uid}", null)
        return !localProfilePicUri.isNullOrEmpty()
    }
    
    /**
     * Get the local profile picture URI
     */
    fun getLocalProfilePictureUri(context: Context): Uri? {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return null
        val sharedPrefs = context.getSharedPreferences("profile_pics", Context.MODE_PRIVATE)
        val localProfilePicUri = sharedPrefs.getString("profile_pic_${currentUser.uid}", null)
        return if (!localProfilePicUri.isNullOrEmpty()) {
            Uri.parse(localProfilePicUri)
        } else {
            null
        }
    }
}
