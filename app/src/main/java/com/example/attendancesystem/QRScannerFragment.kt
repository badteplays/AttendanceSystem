package com.example.attendancesystem

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.example.attendancesystem.utils.LocationManager
import com.example.attendancesystem.models.AttendanceStatus
import com.example.attendancesystem.models.QRCodeData
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class QRScannerFragment : Fragment() {
    private lateinit var barcodeView: DecoratedBarcodeView
    private lateinit var progressBar: ProgressBar
    private lateinit var timerText: TextView
    private lateinit var locationManager: LocationManager
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var isProcessing = false
    private var currentQRData: QRCodeData? = null
    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_qr_scanner, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeViews(view)
        locationManager = LocationManager(requireContext())
        
        // Initialize barcode view
        barcodeView.barcodeView.decoderFactory = com.journeyapps.barcodescanner.DefaultDecoderFactory()
        
        if (checkPermissions()) {
            startScanning()
        } else {
            requestPermissions()
        }
    }

    private fun initializeViews(view: View) {
        barcodeView = view.findViewById(R.id.barcodeScannerView)
        progressBar = view.findViewById(R.id.progressBar)
        timerText = view.findViewById(R.id.timerText)
    }

    private fun checkPermissions(): Boolean {
        val cameraPermission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
        // Camera is the main requirement, location is checked during QR processing
        return cameraPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        
        if (!locationManager.hasLocationPermission()) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun startScanning() {
        try {
            // Initialize camera
            barcodeView.initializeFromIntent(requireActivity().intent)
            barcodeView.decodeContinuous(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult) {
                    if (!isProcessing) {
                        isProcessing = true
                        handleQRCodeResult(result.text)
                    }
                }
            })
            barcodeView.resume()
        } catch (e: Exception) {
            android.util.Log.e("QRScannerFragment", "Error starting camera", e)
            showToast("Error starting camera: ${e.message}")
        }
    }

    private fun stopScanning() {
        barcodeView.pause()
    }

    private fun handleQRCodeResult(qrContent: String) {
        stopScanning()
        progressBar.visibility = View.VISIBLE
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val qrData = QRCodeData.fromJson(qrContent)
                currentQRData = qrData
                
                // Start timer if QR code has expiration
                if (!qrData.isExpired()) {
                    startTimer(qrData)
                }

                // 1) Expiration
                if (qrData.isExpired()) {
                    showToast("This QR code has expired")
                    return@launch
                }

                // 2) Location validation (required)
                if (qrData.hasLocation()) {
                    when (val locationResult = validateLocation(qrData)) {
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
                        }
                        is LocationManager.LocationValidationResult.Warning -> {
                            showToast(locationResult.message)
                            return@launch
                        }
                    }
                } else {
                    showToast("This QR code was generated without location data. Attendance requires location verification.")
                    return@launch
                }

                // 3) Validate session
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

                // 4) Current user
                val currentUser = auth.currentUser ?: throw Exception("Not logged in")
                val userDoc = db.collection("users").document(currentUser.uid).get().await()
                val studentName = userDoc.getString("name") ?: "Unknown Student"

                // 5) Prevent duplicates
                val existing = db.collection("attendance")
                    .whereEqualTo("userId", currentUser.uid)
                    .whereEqualTo("sessionId", qrData.sessionId)
                    .get().await()
                if (!existing.isEmpty) {
                    showToast("Attendance already marked for this session")
                    return@launch
                }

                // 6) Save attendance
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
                db.collection("attendance").add(attendanceData)
                    .addOnSuccessListener {
                        showToast("Attendance marked successfully!")
                        // Navigate back to dashboard
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, StudentDashboardFragment())
                            .commit()
                    }
                    .addOnFailureListener { e ->
                        showToast("Failed to mark attendance: ${e.message}")
                    }
            } catch (e: Exception) {
                showToast(e.message ?: "Error processing QR code")
            } finally {
                progressBar.visibility = View.GONE
                isProcessing = false
                stopTimer()
                // Restart scanning after a short delay
                barcodeView.postDelayed({
                    if (isAdded && !isDetached) {
                        startScanning()
                    }
                }, 2000)
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

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        if (checkPermissions()) {
            startScanning()
        }
    }

    override fun onPause() {
        super.onPause()
        stopScanning()
        stopTimer()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        stopTimer()
    }
    
    fun refreshScanner() {
        if (checkPermissions()) {
            stopScanning()
            startScanning()
        } else {
            requestPermissions()
        }
    }
    
    private fun startTimer(qrData: QRCodeData) {
        stopTimer() // Stop any existing timer
        
        timerRunnable = object : Runnable {
            override fun run() {
                if (!isAdded || isDetached) return
                
                val remainingTime = qrData.getRemainingTimeInMillis()
                if (remainingTime > 0) {
                    val seconds = remainingTime / 1000
                    val minutes = seconds / 60
                    val remainingSeconds = seconds % 60
                    
                    timerText.text = "Expires in: ${minutes}:${remainingSeconds.toString().padStart(2, '0')}"
                    timerText.visibility = View.VISIBLE
                    
                    timerHandler.postDelayed(this, 1000) // Update every second
                } else {
                    timerText.visibility = View.GONE
                    showToast("QR code has expired")
                }
            }
        }
        
        timerHandler.post(timerRunnable!!)
    }
    
    private fun stopTimer() {
        timerRunnable?.let { runnable ->
            timerHandler.removeCallbacks(runnable)
        }
        timerRunnable = null
        timerText.visibility = View.GONE
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
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
                    Toast.makeText(requireContext(), "Location permission is required for attendance validation", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(requireContext(), "Camera permission is required for QR scanning", Toast.LENGTH_LONG).show()
                
                // Switch back to dashboard if camera permission denied
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, StudentDashboardFragment())
                    .commit()
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
}
