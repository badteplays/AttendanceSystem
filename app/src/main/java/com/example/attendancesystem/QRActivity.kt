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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrBinding.inflate(layoutInflater)
        setContentView(binding.root)

        scheduleId = intent.getStringExtra("scheduleId") ?: throw IllegalArgumentException("Schedule ID required")
        subject = intent.getStringExtra("subject") ?: throw IllegalArgumentException("Subject required")
        section = intent.getStringExtra("section") ?: throw IllegalArgumentException("Section required")
        val forceNew = intent.getBooleanExtra("forceNew", false)

        setupViews()

        if (forceNew) {

            deleteOldSessionsAndGenerate()
        } else {

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


    private fun generateQRCode() {
        lifecycleScope.launch {
            try {

                countDownTimer?.cancel()

                binding.progressBar.visibility = View.VISIBLE
                binding.qrCodeImage.visibility = View.GONE

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

                binding.qrCodeImage.alpha = 0f
                binding.qrCodeImage.visibility = View.VISIBLE
                binding.qrCodeImage.animate().alpha(1f).setDuration(500).start()

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


        val hints = hashMapOf<com.google.zxing.EncodeHintType, Any>(
            com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H,
            com.google.zxing.EncodeHintType.MARGIN to 1
        )
        return barcodeEncoder.encodeBitmap(
            jsonData,
            BarcodeFormat.QR_CODE,
            600,
            600,
            hints
        )
    }

    private fun startExpirationTimer(qrData: QRCodeData) {

        countDownTimer?.cancel()

        val expirationTime = qrData.timestamp + qrData.expirationMinutes * 60 * 1000L
        val now = System.currentTimeMillis()
        val millisUntilExpiration = (expirationTime - now).coerceAtLeast(0)

        countDownTimer = object : CountDownTimer(millisUntilExpiration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 1000 / 60
                val seconds = (millisUntilFinished / 1000) % 60
                binding.txtExpiration.text = String.format("Expires in: %02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                binding.txtExpiration.text = "QR Code Expired - Regenerating..."
                Toast.makeText(this@QRActivity, "QR Code expired. Generating new one...", Toast.LENGTH_SHORT).show()

                deleteOldSessionsAndGenerate()
            }
        }.start()
    }

    private fun loadExistingSession() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val currentTime = System.currentTimeMillis()

                val sessionsSnapshot = db.collection("attendance_sessions")
                    .whereEqualTo("scheduleId", scheduleId)
                    .whereEqualTo("teacherId", userId)
                    .get()
                    .await()

                val activeSession = sessionsSnapshot.documents
                    .filter { doc ->
                        val expiresAt = doc.getLong("expiresAt") ?: 0L
                        expiresAt > currentTime
                    }
                    .maxByOrNull { doc ->
                        doc.getLong("expiresAt") ?: 0L
                    }

                if (activeSession == null) {

                    generateQRCode()
                } else {

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

                    val qrCodeBitmap = generateQRCodeBitmap(qrData.toJson())
                    binding.qrCodeImage.setImageBitmap(qrCodeBitmap)
                    binding.qrCodeImage.alpha = 0f
                    binding.qrCodeImage.visibility = View.VISIBLE
                    binding.qrCodeImage.animate().alpha(1f).setDuration(500).start()

                    startExpirationTimer(qrData)

                    val remainingMinutes = ((expiresAt - currentTime) / (60 * 1000)).toInt()
                    Toast.makeText(this@QRActivity, "Showing active QR code ($remainingMinutes min remaining)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("QRActivity", "Error loading session: ${e.message}", e)
                Toast.makeText(this@QRActivity, "Error loading session: ${e.message}", Toast.LENGTH_LONG).show()

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

                val oldSessions = db.collection("attendance_sessions")
                    .whereEqualTo("scheduleId", scheduleId)
                    .whereEqualTo("teacherId", userId)
                    .get()
                    .await()

                for (doc in oldSessions.documents) {
                    doc.reference.delete().await()
                }

                Log.d("QRActivity", "Deleted ${oldSessions.size()} old sessions")

                generateQRCode()
            } catch (e: Exception) {
                Log.e("QRActivity", "Error deleting old sessions: ${e.message}", e)

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

                val sessions = db.collection("attendance_sessions")
                    .whereEqualTo("scheduleId", scheduleId)
                    .whereEqualTo("teacherId", userId)
                    .get()
                    .await()

                if (!sessions.isEmpty) {

                    val latestSession = sessions.documents.maxByOrNull {
                        it.getLong("createdAt") ?: 0L
                    }

                    if (latestSession != null) {
                        val createdAt = latestSession.getLong("createdAt") ?: System.currentTimeMillis()
                        val newExpiresAt = createdAt + (newExpirationMinutes * 60 * 1000L)

                        latestSession.reference.update("expiresAt", newExpiresAt).await()

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
