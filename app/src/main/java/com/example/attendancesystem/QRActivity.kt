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

        setupViews()
        generateQRCode()
    }

    private fun setupViews() {
        binding.btnRenewQR.setOnClickListener { generateQRCode() }
        binding.btnSetExpiration.setOnClickListener { showExpirationDialog() }

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
                binding.progressBar.visibility = View.VISIBLE
                binding.qrCodeImage.visibility = View.GONE

                // Always create a NEW QRCodeData with fresh timestamp
                val sessionId = intent.getStringExtra("sessionId") ?: UUID.randomUUID().toString()
                val qrData = com.example.attendancesystem.models.QRCodeData.createWithExpiration(
                    teacherId = userId,
                    sessionId = sessionId,
                    userId = userId,
                    scheduleId = scheduleId,
                    subject = subject,
                    section = section,
                    expirationMinutes = currentExpirationMinutes
                )

                val qrCodeBitmap = generateQRCodeBitmap(qrData.toJson())
                binding.qrCodeImage.setImageBitmap(qrCodeBitmap)
                // Fade in animation for QR code
                binding.qrCodeImage.alpha = 0f
                binding.qrCodeImage.visibility = View.VISIBLE
                binding.qrCodeImage.animate().alpha(1f).setDuration(500).start()
                
                Log.d("QRActivity", "Calling startExpirationTimer")
                Toast.makeText(this@QRActivity, "Calling startExpirationTimer", Toast.LENGTH_SHORT).show()
                startExpirationTimer(qrData)
            } catch (e: Exception) {
                Toast.makeText(this@QRActivity, getString(R.string.qr_code_generation_failed), Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun generateQRCodeBitmap(jsonData: String): android.graphics.Bitmap {
        val barcodeEncoder = BarcodeEncoder()
        return barcodeEncoder.encodeBitmap(
            jsonData,
            BarcodeFormat.QR_CODE,
            400,
            400
        )
    }

    private fun startExpirationTimer(qrData: QRCodeData) {
        Log.d("QRActivity", "startExpirationTimer CALLED")
        Toast.makeText(this, "startExpirationTimer CALLED", Toast.LENGTH_SHORT).show()
        countDownTimer?.cancel()
        val expirationTime = qrData.timestamp + qrData.expirationMinutes * 60 * 1000
        val now = System.currentTimeMillis()
        val millisUntilExpiration = (expirationTime - now).coerceAtLeast(0)

        countDownTimer = object : CountDownTimer(millisUntilExpiration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 1000 / 60
                val seconds = (millisUntilFinished / 1000) % 60
                Log.d("QRActivity", "TICK: $minutes:$seconds")
                Toast.makeText(this@QRActivity, "TICK: $minutes:$seconds", Toast.LENGTH_SHORT).show()
                binding.txtExpiration.text = getString(R.string.expires_in, minutes, seconds)
            }

            override fun onFinish() {
                binding.txtExpiration.text = getString(R.string.qr_code_expired)
                showExpirationDialog()
            }
        }.start()
    }

    private fun showExpirationDialog() {
        // This will be implemented in QRExpirationDialog
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
