package com.example.attendancesystem.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.mindrot.jbcrypt.BCrypt
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.TimeUnit

class SecurePreferencesManager private constructor(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .setKeyGenParameterSpec(
            KeyGenParameterSpec.Builder(
                MasterKey.DEFAULT_MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        )
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        @Volatile
        private var INSTANCE: SecurePreferencesManager? = null

        fun getInstance(context: Context): SecurePreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecurePreferencesManager(context).also { INSTANCE = it }
            }
        }
    }

    fun saveUserCredentials(username: String, password: String, userType: String) {
        val salt = BCrypt.gensalt()
        val hashedPassword = BCrypt.hashpw(password, salt)

        sharedPreferences.edit().apply {
            putString(username, hashedPassword)
            putString("${username}_type", userType)
            putString("${username}_salt", salt)
            apply()
        }
    }

    fun verifyPassword(username: String, password: String): Boolean {
        val hashedPassword = sharedPreferences.getString(username, null) ?: return false
        return BCrypt.checkpw(password, hashedPassword)
    }

    fun getUserType(username: String): String? {
        return sharedPreferences.getString("${username}_type", null)
    }

    fun saveSession(username: String, userType: String) {
        sharedPreferences.edit().apply {
            putBoolean("isLoggedIn", true)
            putString("currentUser", username)
            putString("userType", userType)
            putLong("lastLoginTime", System.currentTimeMillis())
            putInt("loginAttempts", 0)
            apply()
        }
    }

    fun clearSession() {
        sharedPreferences.edit().clear().apply()
    }

    fun isUserLoggedIn(): Boolean {
        val lastLoginTime = sharedPreferences.getLong("lastLoginTime", 0)
        val sessionTimeout = TimeUnit.HOURS.toMillis(24)
        return sharedPreferences.getBoolean("isLoggedIn", false) &&
               (System.currentTimeMillis() - lastLoginTime) < sessionTimeout
    }

    fun getCurrentUser(): String? {
        return sharedPreferences.getString("currentUser", null)
    }

    fun getCurrentUserType(): String? {
        return sharedPreferences.getString("userType", null)
    }

    fun incrementLoginAttempts() {
        val loginAttempts = sharedPreferences.getInt("loginAttempts", 0) + 1
        sharedPreferences.edit().apply {
            putInt("loginAttempts", loginAttempts)
            putLong("lastAttemptTime", System.currentTimeMillis())
            apply()
        }
    }

    fun isAccountLocked(): Boolean {
        val lastAttemptTime = sharedPreferences.getLong("lastAttemptTime", 0)
        val loginAttempts = sharedPreferences.getInt("loginAttempts", 0)
        val lockoutDuration = TimeUnit.MINUTES.toMillis(15)

        return loginAttempts >= 5 &&
               (System.currentTimeMillis() - lastAttemptTime) < lockoutDuration
    }

    fun getRemainingLockoutTime(): Long {
        val lastAttemptTime = sharedPreferences.getLong("lastAttemptTime", 0)
        val lockoutDuration = TimeUnit.MINUTES.toMillis(15)
        return lockoutDuration - (System.currentTimeMillis() - lastAttemptTime)
    }
}