package com.example.attendancesystem

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.attendancesystem.databinding.ActivityQrBinding
import com.example.attendancesystem.models.QRCodeData
import com.google.android.gms.location.*
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
    private var currentExpirationMinutes = QRCodeData.DEFAULT_EXPIRATION_MINUTES
    private lateinit var scheduleId: String
    private lateinit var subject: String
    private lateinit var section: String
    
    // Location components
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get schedule details from intent
        scheduleId = intent.getStringExtra("scheduleId") ?: throw IllegalArgumentException("Schedule ID required")
        subject = intent.getStringExtra("subject") ?: throw IllegalArgumentException("Subject required")
        section = intent.getStringExtra("section") ?: throw IllegalArgumentException("Section required")

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupViews()
        checkLocationPermissionAndGenerateQR()
    }

    private fun setupViews() {
        binding.btnRenewQR.setOnClickListener {
            checkLocationPermissionAndGenerateQR()
        }

        binding.btnSetExpiration.setOnClickListener {
            showExpirationDialog()
        }
    }
    
    private fun checkLocationPermissionAndGenerateQR() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            == PackageManager.PERMISSION_GRANTED) {
            generateQRCodeWithLocation()
        } else {
            requestLocationPermission()
        }
    }
    
    private fun requestLocationPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            Toast.makeText(this, "Location permission is required to generate QR codes with teacher location for attendance validation", Toast.LENGTH_LONG).show()
        }
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    generateQRCodeWithLocation()
                } else {
                    Toast.makeText(this, "Location permission denied. QR codes will not include location data for attendance validation.", Toast.LENGTH_LONG).show()
                    generateQRCode() // Generate without location as fallback
                }
            }
        }
    }
    
    private fun generateQRCodeWithLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            generateQRCode() // Fallback to non-location QR
            return
        }
        
        binding.progressBar.visibility = View.VISIBLE
        binding.qrCodeImage.visibility = View.GONE
        
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            lifecycleScope.launch {
                try {
                    val sessionId = intent.getStringExtra("sessionId") ?: UUID.randomUUID().toString()
                    
                    val qrData = if (location != null) {
                        Log.d("QRActivity", "Teacher location: ${location.latitude}, ${location.longitude}")
                        Toast.makeText(this@QRActivity, "QR code includes teacher location for attendance validation", Toast.LENGTH_SHORT).show()
                        
                        QRCodeData.createWithExpiration(
                            teacherId = userId,
                            sessionId = sessionId,
                            userId = userId,
                            scheduleId = scheduleId,
                            subject = subject,
                            section = section,
                            expirationMinutes = currentExpirationMinutes,
                            latitude = location.latitude,
                            longitude = location.longitude
                        )
                    } else {
                        Log.w("QRActivity", "Could not get teacher location, generating QR without location")
                        Toast.makeText(this@QRActivity, "Could not get location. QR code generated without location data.", Toast.LENGTH_SHORT).show()
                        
                        QRCodeData.createWithExpiration(
                            teacherId = userId,
                            sessionId = sessionId,
                            userId = userId,
                            scheduleId = scheduleId,
                            subject = subject,
                            section = section,
                            expirationMinutes = currentExpirationMinutes
                        )
                    }

                    val qrCodeBitmap = generateQRCodeBitmap(qrData.toJson())
                    binding.qrCodeImage.setImageBitmap(qrCodeBitmap)
                    
                    // Fade in animation for QR code
                    binding.qrCodeImage.alpha = 0f
                    binding.qrCodeImage.visibility = View.VISIBLE
                    binding.qrCodeImage.animate().alpha(1f).setDuration(500).start()
                    
                    Log.d("QRActivity", "Calling startExpirationTimer")
                    startExpirationTimer(qrData)
                    
                } catch (e: Exception) {
                    Toast.makeText(this@QRActivity, getString(R.string.qr_code_generation_failed), Toast.LENGTH_SHORT).show()
                    Log.e("QRActivity", "Error generating QR code with location", e)
                } finally {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }.addOnFailureListener { e ->
            Log.e("QRActivity", "Failed to get location", e)
            Toast.makeText(this, "Failed to get location. Generating QR code without location data.", Toast.LENGTH_SHORT).show()
            generateQRCode() // Fallback to non-location QR
        }
    }

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
