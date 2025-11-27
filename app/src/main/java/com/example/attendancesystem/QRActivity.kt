package com.example.attendancesystem

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.attendancesystem.databinding.ActivityQrBinding
import com.example.attendancesystem.models.QRCodeData
import com.google.firebase.auth.FirebaseAuth
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class QRActivity : AppCompatActivity() {
    private lateinit var binding: ActivityQrBinding
    private var countDownTimer: CountDownTimer? = null
    private val auth = FirebaseAuth.getInstance()
    private val userId = auth.currentUser?.uid ?: ""
    private var currentExpirationMinutes = 30
    private lateinit var scheduleId: String
    private lateinit var subject: String
    private lateinit var section: String
    
    // Location removed

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get schedule details from intent
        scheduleId = intent.getStringExtra("scheduleId") ?: throw IllegalArgumentException("Schedule ID required")
        subject = intent.getStringExtra("subject") ?: throw IllegalArgumentException("Subject required")
        section = intent.getStringExtra("section") ?: throw IllegalArgumentException("Section required")
        val forceNew = intent.getBooleanExtra("forceNew", false)

        setupViews()
        
        if (forceNew) {
            // Delete old sessions and generate fresh QR
            deleteOldSessionsAndGenerate()
        } else {
            // Try to load existing active session
            loadExistingSession()
        }
    }

    private fun setupViews() {
        binding.btnRenewQR.setOnClickListener { 
            Log.d("QRActivity", "Renew QR button clicked")
            Toast.makeText(this, "Generating new QR code...", Toast.LENGTH_SHORT).show()
            generateQRCode() 
        }
        binding.btnSetExpiration.setOnClickListener { 
            Log.d("QRActivity", "Set Expiration button clicked")
            showExpirationDialog() 
        }

        // Drawer handle wiring
        try {
            val drawerLayout = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawerLayout)
            val handle = findViewById<android.widget.ImageView>(R.id.drawerHandle)
            val nav = findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
            handle?.setOnClickListener { androidx.core.view.GravityCompat.END.let { drawerLayout?.openDrawer(it) } }
            nav?.setNavigationItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.drawer_home -> { finish(); true }
                    R.id.drawer_schedule -> { startActivity(android.content.Intent(this, TeacherMainActivity::class.java).putExtra("open","schedule")); true }
                    R.id.drawer_analytics -> { startActivity(android.content.Intent(this, TeacherMainActivity::class.java).putExtra("open","analytics")); true }
                    R.id.drawer_settings -> { startActivity(android.content.Intent(this, TeacherMainActivity::class.java).putExtra("open","settings")); true }
                    else -> false
                }.also { drawerLayout?.closeDrawers() }
            }
        } catch (_: Exception) { }
    }
    
    // Location-based generation removed; always generate without location

    private fun generateQRCode() {
        lifecycleScope.launch {
            try {
                // Cancel existing timer first
                countDownTimer?.cancel()
                
                binding.progressBar.visibility = View.VISIBLE
                binding.qrCodeImage.visibility = View.GONE

                // Always create a NEW QRCodeData with fresh timestamp
                val sessionId = UUID.randomUUID().toString()
                val qrData = com.example.attendancesystem.models.QRCodeData.createWithExpiration(
                    teacherId = userId,
                    sessionId = sessionId,
                    userId = userId,
                    scheduleId = scheduleId,
                    subject = subject,
                    section = section,
                    expirationMinutes = currentExpirationMinutes
                )

                // Create attendance session document in Firestore (required by security rules)
                val sessionData = hashMapOf(
                    "sessionId" to sessionId,
                    "teacherId" to userId,
                    "scheduleId" to scheduleId,
                    "subject" to subject,
                    "section" to section,
                    "createdAt" to System.currentTimeMillis(),
                    "expiresAt" to (System.currentTimeMillis() + currentExpirationMinutes * 60 * 1000L)
                )
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("attendance_sessions")
                    .document(sessionId)
                    .set(sessionData)
                    .await()

                val qrCodeBitmap = generateQRCodeBitmap(qrData.toJson())
                binding.qrCodeImage.setImageBitmap(qrCodeBitmap)
                // Fade in animation for QR code
                binding.qrCodeImage.alpha = 0f
                binding.qrCodeImage.visibility = View.VISIBLE
                binding.qrCodeImage.animate().alpha(1f).setDuration(500).start()
                
                // Start fresh timer
                startExpirationTimer(qrData)
                Toast.makeText(this@QRActivity, "QR Code renewed! Expires in $currentExpirationMinutes minutes", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@QRActivity, "Failed to generate QR code: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("QRActivity", "Error generating QR code", e)
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun generateQRCodeBitmap(jsonData: String): android.graphics.Bitmap {
        val barcodeEncoder = BarcodeEncoder()
        // Increased size to 600x600 for better scanning on all devices including Xiaomi
        // Higher error correction (H = 30%) makes it more reliable
        val hints = hashMapOf<com.google.zxing.EncodeHintType, Any>(
            com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H,
            com.google.zxing.EncodeHintType.MARGIN to 1 // Smaller margin for larger QR code
        )
        return barcodeEncoder.encodeBitmap(
            jsonData,
            BarcodeFormat.QR_CODE,
            600,  // Increased from 400
            600,  // Increased from 400
            hints
        )
    }

    private fun startExpirationTimer(qrData: QRCodeData) {
        // Cancel any existing timer
        countDownTimer?.cancel()
        
        // Calculate expiration time
        val expirationTime = qrData.timestamp + qrData.expirationMinutes * 60 * 1000L
        val now = System.currentTimeMillis()
        val millisUntilExpiration = (expirationTime - now).coerceAtLeast(0)

        // Start new countdown timer
        countDownTimer = object : CountDownTimer(millisUntilExpiration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 1000 / 60
                val seconds = (millisUntilFinished / 1000) % 60
                binding.txtExpiration.text = String.format("Expires in: %02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                binding.txtExpiration.text = "QR Code Expired - Regenerating..."
                Toast.makeText(this@QRActivity, "QR Code expired. Generating new one...", Toast.LENGTH_SHORT).show()
                
                // Auto-regenerate QR code when timer expires
                deleteOldSessionsAndGenerate()
            }
        }.start()
    }

    private fun loadExistingSession() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                
                // Query for active session for this schedule (simplified - no composite index needed)
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val currentTime = System.currentTimeMillis()
                
                val sessionsSnapshot = db.collection("attendance_sessions")
                    .whereEqualTo("scheduleId", scheduleId)
                    .whereEqualTo("teacherId", userId)
                    .get()
                    .await()
                
                // Filter active sessions in memory (avoid needing index)
                val activeSession = sessionsSnapshot.documents
                    .filter { doc ->
                        val expiresAt = doc.getLong("expiresAt") ?: 0L
                        expiresAt > currentTime
                    }
                    .maxByOrNull { doc ->
                        doc.getLong("expiresAt") ?: 0L
                    }
                
                if (activeSession == null) {
                    // No active session found, generate a new one automatically
                    generateQRCode()
                } else {
                    // Load existing session
                    val sessionId = activeSession.getString("sessionId") ?: ""
                    val expiresAt = activeSession.getLong("expiresAt") ?: 0L
                    val createdAt = activeSession.getLong("createdAt") ?: System.currentTimeMillis()
                    val expirationMinutes = ((expiresAt - createdAt) / (60 * 1000)).toInt()
                    
                    currentExpirationMinutes = expirationMinutes
                    
                    val qrData = com.example.attendancesystem.models.QRCodeData(
                        teacherId = userId,
                        sessionId = sessionId,
                        userId = userId,
                        scheduleId = scheduleId,
                        subject = subject,
                        section = section,
                        timestamp = createdAt,
                        expirationMinutes = expirationMinutes
                    )
                    
                    // Generate QR code image
                    val qrCodeBitmap = generateQRCodeBitmap(qrData.toJson())
                    binding.qrCodeImage.setImageBitmap(qrCodeBitmap)
                    binding.qrCodeImage.alpha = 0f
                    binding.qrCodeImage.visibility = View.VISIBLE
                    binding.qrCodeImage.animate().alpha(1f).setDuration(500).start()
                    
                    // Start timer with remaining time
                    startExpirationTimer(qrData)
                    
                    val remainingMinutes = ((expiresAt - currentTime) / (60 * 1000)).toInt()
                    Toast.makeText(this@QRActivity, "Showing active QR code ($remainingMinutes min remaining)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("QRActivity", "Error loading session: ${e.message}", e)
                Toast.makeText(this@QRActivity, "Error loading session: ${e.message}", Toast.LENGTH_LONG).show()
                // Fallback to generating new QR
                generateQRCode()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun deleteOldSessionsAndGenerate() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                
                // Delete all old sessions for this schedule
                val oldSessions = db.collection("attendance_sessions")
                    .whereEqualTo("scheduleId", scheduleId)
                    .whereEqualTo("teacherId", userId)
                    .get()
                    .await()
                
                // Delete each old session
                for (doc in oldSessions.documents) {
                    doc.reference.delete().await()
                }
                
                Log.d("QRActivity", "Deleted ${oldSessions.size()} old sessions")
                
                // Now generate fresh QR
                generateQRCode()
            } catch (e: Exception) {
                Log.e("QRActivity", "Error deleting old sessions: ${e.message}", e)
                // Still try to generate new QR even if delete fails
                generateQRCode()
            }
        }
    }

    private fun showExpirationDialog() {
        val options = arrayOf("5 minutes", "10 minutes", "15 minutes", "30 minutes", "45 minutes", "60 minutes")
        val values = arrayOf(5, 10, 15, 30, 45, 60)
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Set QR Code Expiration Time")
            .setItems(options) { dialog, which ->
                val newExpiration = values[which]
                currentExpirationMinutes = newExpiration
                Toast.makeText(this, "Timer extended to ${options[which]}", Toast.LENGTH_SHORT).show()
                
                // Update the existing session's expiration time without changing QR code
                updateSessionExpiration(newExpiration)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun updateSessionExpiration(newExpirationMinutes: Int) {
        lifecycleScope.launch {
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                
                // Find the current active session for this schedule
                val sessions = db.collection("attendance_sessions")
                    .whereEqualTo("scheduleId", scheduleId)
                    .whereEqualTo("teacherId", userId)
                    .get()
                    .await()
                
                if (!sessions.isEmpty) {
                    // Update the most recent session
                    val latestSession = sessions.documents.maxByOrNull { 
                        it.getLong("createdAt") ?: 0L 
                    }
                    
                    if (latestSession != null) {
                        val createdAt = latestSession.getLong("createdAt") ?: System.currentTimeMillis()
                        val newExpiresAt = createdAt + (newExpirationMinutes * 60 * 1000L)
                        
                        // Update session expiration
                        latestSession.reference.update("expiresAt", newExpiresAt).await()
                        
                        // Restart timer with new duration
                        val qrData = com.example.attendancesystem.models.QRCodeData(
                            teacherId = userId,
                            sessionId = latestSession.getString("sessionId") ?: "",
                            userId = userId,
                            scheduleId = scheduleId,
                            subject = subject,
                            section = section,
                            timestamp = createdAt,
                            expirationMinutes = newExpirationMinutes
                        )
                        startExpirationTimer(qrData)
                        
                        Log.d("QRActivity", "Updated session expiration to $newExpirationMinutes minutes")
                    }
                }
            } catch (e: Exception) {
                Log.e("QRActivity", "Error updating session expiration: ${e.message}", e)
                Toast.makeText(this@QRActivity, "Error updating timer: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
