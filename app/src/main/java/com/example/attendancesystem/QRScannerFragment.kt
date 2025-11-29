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
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType

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
    private var vibrator: Vibrator? = null
    private var toneGenerator: ToneGenerator? = null
    private val scannedSessions = mutableSetOf<String>()

    companion object {
        private const val TAG = "QRScannerFragment"
        private const val PERMISSION_REQUEST_CODE = 100
    }


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

        vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Could not initialize tone generator", e)
        }

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

        return cameraPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {

        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.CAMERA),
            PERMISSION_REQUEST_CODE
        )
    }

    private fun startScanning() {
        try {

            barcodeView.barcodeView.cameraSettings.apply {
                requestedCameraId = -1
                isAutoFocusEnabled = true
                isContinuousFocusEnabled = true
                isAutoTorchEnabled = false
                isBarcodeSceneModeEnabled = true
                isExposureEnabled = true
                isMeteringEnabled = true
            }

            val hints = mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.CHARACTER_SET to "UTF-8",

                DecodeHintType.ALSO_INVERTED to true
            )

            val formats = listOf(BarcodeFormat.QR_CODE)
            barcodeView.barcodeView.decoderFactory = DefaultDecoderFactory(formats, hints, null, 0)

            barcodeView.initializeFromIntent(requireActivity().intent)
            barcodeView.decodeContinuous(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult) {
                    if (!isProcessing && result.text != null && result.text.isNotEmpty()) {
                        isProcessing = true

                        playSuccessSound()
                        vibratePhone()
                        handleQRCodeResult(result.text)
                    }
                }
            })
            barcodeView.resume()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error starting camera", e)
            showToast("Error starting camera: ${e.message}")
        }
    }

    private fun playSuccessSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Could not play success sound", e)
        }
    }

    private fun vibratePhone() {
        try {
            vibrator?.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(200)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Could not vibrate", e)
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

                if (!qrData.isExpired()) {
                    startTimer(qrData)
                }

                if (qrData.isExpired()) {
                    showToast("This QR code has expired")
                    progressBar.visibility = View.GONE
                    isProcessing = false
                    stopTimer()
                    return@launch
                }


                val sessionDoc = db.collection("attendance_sessions")
                    .document(qrData.sessionId)
                    .get().await()
                if (!sessionDoc.exists()) {
                    showToast("Invalid or expired session")
                    progressBar.visibility = View.GONE
                    isProcessing = false
                    stopTimer()
                    return@launch
                }
                val sessionTeacherId = sessionDoc.getString("teacherId")
                if (qrData.teacherId != sessionTeacherId) {
                    showToast("This QR code is not for this class/teacher")
                    progressBar.visibility = View.GONE
                    isProcessing = false
                    stopTimer()
                    return@launch
                }
                val expiresAt = sessionDoc.getLong("expiresAt") ?: 0L
                if (System.currentTimeMillis() > expiresAt) {
                    showToast("This QR code session has expired")
                    progressBar.visibility = View.GONE
                    isProcessing = false
                    stopTimer()
                    return@launch
                }

                val currentUser = auth.currentUser ?: throw Exception("Not logged in")
                val userDoc = db.collection("users").document(currentUser.uid).get().await()
                val studentName = userDoc.getString("name") ?: "Unknown Student"


                if (scannedSessions.contains(qrData.sessionId)) {
                    android.util.Log.d(TAG, "Duplicate scan detected (app session) - Session: ${qrData.sessionId}")
                    showToast("You've already marked attendance for this class!")
                    progressBar.visibility = View.GONE
                    isProcessing = false
                    stopTimer()
                    return@launch
                }

                val existingBySession = db.collection("attendance")
                    .whereEqualTo("userId", currentUser.uid)
                    .whereEqualTo("sessionId", qrData.sessionId)
                    .get().await()

                if (!existingBySession.isEmpty) {
                    android.util.Log.d(TAG, "Duplicate scan detected - already scanned this QR session")
                    scannedSessions.add(qrData.sessionId)
                    showToast("You've already scanned this QR code!")
                    progressBar.visibility = View.GONE
                    isProcessing = false
                    stopTimer()
                    return@launch
                }

                android.util.Log.d(TAG, "No duplicates found, marking attendance for ${qrData.subject}")

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
                android.util.Log.d(TAG, "=== SAVING ATTENDANCE TO FIRESTORE ===")
                android.util.Log.d(TAG, "userId: ${currentUser.uid}")
                android.util.Log.d(TAG, "studentName: $studentName")
                android.util.Log.d(TAG, "sessionId: ${qrData.sessionId}")
                android.util.Log.d(TAG, "teacherId: ${qrData.teacherId}")
                android.util.Log.d(TAG, "scheduleId: ${qrData.scheduleId}")
                android.util.Log.d(TAG, "subject: ${qrData.subject}")
                android.util.Log.d(TAG, "section: ${qrData.section}")
                android.util.Log.d(TAG, "status: ${AttendanceStatus.PRESENT.name}")
                val docRef = db.collection("attendance").add(attendanceData).await()
                android.util.Log.d(TAG, "✓✓✓ ATTENDANCE SAVED! Document ID: ${docRef.id} ✓✓✓")

                scannedSessions.add(qrData.sessionId)

                android.util.Log.d(TAG, "Attendance marked successfully - Session: ${qrData.sessionId}, Subject: ${qrData.subject}")

                progressBar.visibility = View.GONE
                stopTimer()
                isProcessing = false
                showToast("Attendance marked successfully!")

                val dashboardFragment = StudentDashboardFragment().apply {
                    arguments = Bundle().apply {
                        putString("justMarkedSubject", qrData.subject)
                        putLong("justMarkedTime", System.currentTimeMillis())
                    }
                }
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, dashboardFragment)
                    .commit()

            } catch (e: Exception) {
                showToast(e.message ?: "Error processing QR code")
                progressBar.visibility = View.GONE
                isProcessing = false
                stopTimer()

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

        toneGenerator?.release()
        toneGenerator = null
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
        stopTimer()

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

                    timerHandler.postDelayed(this, 1000)
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

            val cameraGranted = permissions.indices.any { i ->
                permissions[i] == Manifest.permission.CAMERA &&
                grantResults[i] == PackageManager.PERMISSION_GRANTED
            }

            if (cameraGranted) {

                startScanning()
            } else {
                Toast.makeText(requireContext(), "Camera permission is required for QR scanning", Toast.LENGTH_LONG).show()

                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, StudentDashboardFragment())
                    .commit()
            }
        }
    }
}
