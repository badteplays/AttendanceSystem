package com.example.attendancesystem

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.attendancesystem.databinding.ActivityQrScannerBinding
import com.example.attendancesystem.models.AttendanceStatus
import com.example.attendancesystem.models.QRCodeData
import com.example.attendancesystem.utils.LocationManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class QRScannerActivity : AppCompatActivity() {
    
    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
        private const val LOCATION_PERMISSION_REQUEST_CODE = 101
    }
    
    private lateinit var binding: ActivityQrScannerBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var locationManager: LocationManager
    private var isProcessing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        locationManager = LocationManager(this)

        // Initialize the barcode scanner view
        binding.barcodeScannerView.barcodeView.decoderFactory = com.journeyapps.barcodescanner.DefaultDecoderFactory()
        
        if (checkPermissions()) {
            startScanning()
        } else {
            requestPermissions()
        }
    }

    private fun checkPermissions(): Boolean {
        val cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        // Camera is the main requirement, location is checked during QR processing
        return cameraPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        
        if (!locationManager.hasLocationPermission()) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun startScanning() {
        try {
            // Initialize and start the camera
            binding.barcodeScannerView.initializeFromIntent(intent)
            binding.barcodeScannerView.decodeContinuous(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult) {
                    if (!isProcessing) {
                        isProcessing = true
                        processQRCode(result.text)
                    }
                }
            })
            binding.barcodeScannerView.resume()
        } catch (e: Exception) {
            android.util.Log.e("QRScanner", "Error starting camera", e)
            showToast("Error starting camera: ${e.message}")
        }
    }

    private fun processQRCode(qrContent: String) {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                
                val qrData = QRCodeData.fromJson(qrContent)
                
                // 1. Check if QR code is expired
                if (qrData.isExpired()) {
                    showToast("This QR code has expired")
                    return@launch
                }

                // 2. Check location if QR code has location data
                if (qrData.hasLocation()) {
                    val locationResult = validateLocation(qrData)
                    when (locationResult) {
                        is LocationManager.LocationValidationResult.Invalid -> {
                            showToast(locationResult.message)
                            return@launch
                        }
                        is LocationManager.LocationValidationResult.Error -> {
                            showToast("Location validation failed: ${locationResult.message}")
                            return@launch
                        }
                        is LocationManager.LocationValidationResult.Valid -> {
                            showToast(locationResult.message)
                            // Continue with normal processing
                        }
                        is LocationManager.LocationValidationResult.Warning -> {
                            // This should not happen with the new strict validation, but handle it just in case
                            showToast(locationResult.message)
                            return@launch
                        }
                    }
                } else {
                    // QR code has no location data - this means it was generated without teacher location
                    showToast("This QR code was generated without location data. Attendance requires location verification.")
                    return@launch
                }

                // 3. Check session uniqueness and teacher binding
                val sessionDoc = db.collection("attendance_sessions")
                    .document(qrData.sessionId)
                    .get().await()
                if (!sessionDoc.exists()) {
                    showToast("Invalid or expired session")
                    return@launch
                }
                val sessionTeacherId = sessionDoc.getString("teacherId")
                if (qrData.teacherId != sessionTeacherId) {
                    showToast("This QR code is not for this class/teacher")
                    return@launch
                }
                val expiresAt = sessionDoc.getLong("expiresAt") ?: 0L
                if (System.currentTimeMillis() > expiresAt) {
                    showToast("This QR code session has expired")
                    return@launch
                }

                // 4. Get current user data
                val currentUser = auth.currentUser ?: throw Exception("Not logged in")
                val userDoc = db.collection("users")
                    .document(currentUser.uid)
                    .get()
                    .await()
                val studentName = userDoc.getString("name") ?: "Unknown Student"

                // 5. Check if already marked attendance for this session
                val existingAttendance = db.collection("attendance")
                    .whereEqualTo("userId", currentUser.uid)
                    .whereEqualTo("sessionId", qrData.sessionId)
                    .get()
                    .await()
                if (!existingAttendance.isEmpty) {
                    showToast("Attendance already marked for this session")
                    return@launch
                }

                // 6. Create attendance record
                val attendanceData = hashMapOf(
                    "userId" to currentUser.uid,
                    "studentName" to studentName,
                    "sessionId" to qrData.sessionId,
                    "teacherId" to qrData.teacherId,
                    "scheduleId" to qrData.scheduleId,
                    "subject" to qrData.subject,
                    "section" to qrData.section,
                    "timestamp" to com.google.firebase.Timestamp.now(),
                    "status" to AttendanceStatus.PRESENT.name,
                    "location" to "",
                    "notes" to ""
                )
                db.collection("attendance")
                    .add(attendanceData)
                    .addOnSuccessListener {
                        showToast("Attendance marked successfully!")
                        finish()
                    }
                    .addOnFailureListener { e ->
                        showToast("Failed to mark attendance: ${e.message}")
                    }
            } catch (e: Exception) {
                showToast(e.message ?: "Error processing QR code")
            } finally {
                binding.progressBar.visibility = View.GONE
                isProcessing = false
            }
        }
    }

    private suspend fun validateLocation(qrData: QRCodeData): LocationManager.LocationValidationResult {
        return try {
            if (!locationManager.isLocationEnabled()) {
                return LocationManager.LocationValidationResult.Error("Location services are disabled")
            }

            val currentLocation = locationManager.getCurrentLocation()
            val qrLocation = qrData.getLocationData()

            if (qrLocation == null) {
                return LocationManager.LocationValidationResult.Error("QR code location data is missing")
            }

            locationManager.validateLocationForQRScan(currentLocation, qrLocation)
        } catch (e: SecurityException) {
            LocationManager.LocationValidationResult.Error("Location permission denied")
        } catch (e: Exception) {
            LocationManager.LocationValidationResult.Error("Location validation failed: ${e.message}")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> {
                // Check if camera permission was granted (minimum requirement)
                val cameraGranted = permissions.indices.any { i ->
                    permissions[i] == Manifest.permission.CAMERA && 
                    grantResults[i] == PackageManager.PERMISSION_GRANTED
                }
                
                if (cameraGranted) {
                    // Camera permission granted, start scanning
                    startScanning()
                    
                    // Check location permission separately
                    if (!locationManager.hasLocationPermission()) {
                        showToast("Location permission is required for attendance validation")
                    }
                } else {
                    showToast("Camera permission is required for QR scanning")
                    finish()
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        if (checkPermissions()) {
            binding.barcodeScannerView.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.barcodeScannerView.pause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        binding.barcodeScannerView.pause()
    }
}