package com.example.attendancesystem.security

import android.util.Base64
import android.util.Patterns
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Security utilities for input validation, sanitization, and cryptographic operations.
 */
object SecurityUtils {

    // ═══════════════════════════════════════════════════════════════
    // INPUT VALIDATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Validates email format
     */
    fun isValidEmail(email: String?): Boolean {
        return !email.isNullOrBlank() && 
               Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
               email.length <= 254
    }

    /**
     * Validates password strength
     * - Minimum 6 characters
     * - No whitespace only
     */
    fun isValidPassword(password: String?): Boolean {
        return !password.isNullOrBlank() && 
               password.length >= 6 &&
               password.length <= 128 &&
               password.trim() == password
    }

    /**
     * Validates name (no special characters that could be used for injection)
     */
    fun isValidName(name: String?): Boolean {
        if (name.isNullOrBlank() || name.length > 100) return false
        // Allow letters, spaces, hyphens, apostrophes, periods
        val namePattern = Regex("^[\\p{L}\\s.'-]+$")
        return namePattern.matches(name.trim())
    }

    /**
     * Validates section format (alphanumeric with hyphen)
     */
    fun isValidSection(section: String?): Boolean {
        if (section.isNullOrBlank() || section.length > 20) return false
        val sectionPattern = Regex("^[A-Za-z0-9-]+$")
        return sectionPattern.matches(section.trim())
    }

    /**
     * Validates subject name
     */
    fun isValidSubject(subject: String?): Boolean {
        if (subject.isNullOrBlank() || subject.length > 100) return false
        // Allow letters, numbers, spaces, and common punctuation
        val subjectPattern = Regex("^[\\p{L}\\p{N}\\s.,&()-]+$")
        return subjectPattern.matches(subject.trim())
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT SANITIZATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Sanitizes string input by removing potentially dangerous characters
     */
    fun sanitizeString(input: String?): String {
        if (input.isNullOrBlank()) return ""
        return input.trim()
            .replace(Regex("[<>\"'&]"), "") // Remove HTML/script characters
            .replace(Regex("[\\x00-\\x1F\\x7F]"), "") // Remove control characters
            .take(500) // Limit length
    }

    /**
     * Sanitizes email input
     */
    fun sanitizeEmail(email: String?): String {
        if (email.isNullOrBlank()) return ""
        return email.trim().lowercase().take(254)
    }

    /**
     * Sanitizes section input (uppercase, alphanumeric only)
     */
    fun sanitizeSection(section: String?): String {
        if (section.isNullOrBlank()) return ""
        return section.trim()
            .uppercase()
            .replace(Regex("[^A-Z0-9-]"), "")
            .take(20)
    }

    // ═══════════════════════════════════════════════════════════════
    // QR CODE SECURITY
    // ═══════════════════════════════════════════════════════════════

    private const val QR_SECRET_KEY = "your-secret-key-change-in-production"

    /**
     * Generates HMAC signature for QR code data integrity
     */
    fun generateQRSignature(data: String, timestamp: Long): String {
        val message = "$data|$timestamp"
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(QR_SECRET_KEY.toByteArray(), "HmacSHA256")
        mac.init(secretKey)
        val hash = mac.doFinal(message.toByteArray())
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    /**
     * Verifies QR code signature
     */
    fun verifyQRSignature(data: String, timestamp: Long, signature: String): Boolean {
        val expectedSignature = generateQRSignature(data, timestamp)
        return MessageDigest.isEqual(
            expectedSignature.toByteArray(),
            signature.toByteArray()
        )
    }

    /**
     * Generates a secure random session ID
     */
    fun generateSecureSessionId(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    // ═══════════════════════════════════════════════════════════════
    // ANTI-TAMPERING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generates a hash of device info for session binding
     */
    fun generateDeviceFingerprint(deviceId: String, userId: String): String {
        val message = "$deviceId|$userId|${System.currentTimeMillis() / 86400000}" // Daily rotation
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(message.toByteArray())
        return Base64.encodeToString(hash, Base64.NO_WRAP).take(32)
    }

    // ═══════════════════════════════════════════════════════════════
    // RATE LIMITING
    // ═══════════════════════════════════════════════════════════════

    private val requestTimestamps = mutableMapOf<String, MutableList<Long>>()
    private const val MAX_REQUESTS_PER_MINUTE = 30
    private const val RATE_LIMIT_WINDOW_MS = 60_000L

    /**
     * Checks if a request should be rate limited
     * @param key Unique key for the request type (e.g., "login_attempt_user@email.com")
     * @return true if request should be blocked
     */
    @Synchronized
    fun isRateLimited(key: String): Boolean {
        val now = System.currentTimeMillis()
        val timestamps = requestTimestamps.getOrPut(key) { mutableListOf() }
        
        // Remove old timestamps
        timestamps.removeAll { now - it > RATE_LIMIT_WINDOW_MS }
        
        // Check if over limit
        if (timestamps.size >= MAX_REQUESTS_PER_MINUTE) {
            return true
        }
        
        // Add current timestamp
        timestamps.add(now)
        return false
    }

    /**
     * Clears rate limit for a key (e.g., after successful login)
     */
    @Synchronized
    fun clearRateLimit(key: String) {
        requestTimestamps.remove(key)
    }

    // ═══════════════════════════════════════════════════════════════
    // SQL INJECTION PREVENTION (for any SQL operations)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Escapes SQL special characters (for any raw SQL usage)
     */
    fun escapeSql(input: String?): String {
        if (input.isNullOrBlank()) return ""
        return input
            .replace("'", "''")
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }
}


