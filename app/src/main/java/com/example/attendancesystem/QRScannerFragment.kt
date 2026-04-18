package com.example.attendancesystem

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
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
import com.example.attendancesystem.models.QRCodeData
import com.example.attendancesystem.utils.LocationManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
        // #region agent log
        private const val DEBUG_SESSION = "f32d87"
        private const val DEBUG_INGEST = "http://10.0.2.2:7552/ingest/00253193-e183-41ea-802a-ffd1a46a6236"
        // #endregion
    }

    // #region agent log
    private fun qrScanDebugLog(hypothesisId: String, location: String, message: String, data: Map<String, Any?>) {
        try {
            val dataObj = JSONObject()
            for ((k, v) in data) {
                when (v) {
                    null -> dataObj.put(k, JSONObject.NULL)
                    is Number -> dataObj.put(k, v)
                    is Boolean -> dataObj.put(k, v)
                    else -> dataObj.put(k, v.toString())
                }
            }
            val line = JSONObject().apply {
                put("sessionId", DEBUG_SESSION)
                put("runId", "pre-fix")
                put("hypothesisId", hypothesisId)
                put("location", location)
                put("message", message)
                put("data", dataObj)
                put("timestamp", System.currentTimeMillis())
            }.toString()
            Thread {
                try {
                    val conn = URL(DEBUG_INGEST).openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("X-Debug-Session-Id", DEBUG_SESSION)
                    conn.doOutput = true
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    conn.outputStream.use { it.write(line.toByteArray(Charsets.UTF_8)) }
                    conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                } catch (_: Exception) {
                }
            }.start()
        } catch (_: Exception) {
        }
    }
    // #endregion

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
            // #region agent log
            AgentDebugLog.log(
                "QRScannerFragment.kt:startScanning",
                "camera_start_failed",
                "H4",
                mapOf("type" to e.javaClass.simpleName, "msg" to (e.message ?: ""))
            )
            // #endregion
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
            // #region agent log
            var debugStep = "INIT"
            // #endregion
            try {
                val qrData = QRCodeData.fromJson(qrContent)
                currentQRData = qrData
                // #region agent log
                qrScanDebugLog(
                    "H4",
                    "QRScannerFragment.kt:handleQRCodeResult",
                    "qr_parsed",
                    mapOf(
                        "scheduleId" to qrData.scheduleId,
                        "qrSection" to qrData.section,
                        "teacherIdLen" to qrData.teacherId.length
                    )
                )
                // #endregion

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

                // #region agent log
                debugStep = "STEP1_READ_SESSION"
                // #endregion
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

                // #region agent log
                debugStep = "STEP2_READ_USER"
                // #endregion
                val currentUser = auth.currentUser ?: throw Exception("Not logged in")
                val userDoc = db.collection("users").document(currentUser.uid).get().await()
                val studentName = userDoc.getString("name") ?: "Unknown Student"
                val studentSectionRaw = userDoc.getString("section") ?: ""
                // #region agent log
                val userRole = userDoc.getString("role") ?: "NULL"
                val userIsStudent = userDoc.getBoolean("isStudent")?.toString() ?: "NULL"
                val userIsTeacher = userDoc.getBoolean("isTeacher")?.toString() ?: "NULL"
                android.util.Log.e("QR_DEBUG", "USER_DOC: role=$userRole isStudent=$userIsStudent isTeacher=$userIsTeacher uid=${currentUser.uid}")
                // #endregion

                // #region agent log
                val scheduleDoc = db.collection("schedules").document(qrData.scheduleId).get().await()
                val scheduleExists = scheduleDoc.exists()
                val scheduleSectionRaw = if (scheduleExists) (scheduleDoc.getString("section") ?: "") else ""
                fun normSec(s: String) = s.trim().lowercase()
                val userVsQrSection = normSec(studentSectionRaw) == normSec(qrData.section)
                val userVsScheduleSection = scheduleExists && normSec(studentSectionRaw) == normSec(scheduleSectionRaw)
                val qrVsScheduleSection = scheduleExists && normSec(qrData.section) == normSec(scheduleSectionRaw)
                qrScanDebugLog(
                    "H1",
                    "QRScannerFragment.kt:handleQRCodeResult",
                    "eligibility_snapshot",
                    mapOf(
                        "studentSectionRaw" to studentSectionRaw,
                        "qrSection" to qrData.section,
                        "scheduleExists" to scheduleExists,
                        "scheduleSectionRaw" to scheduleSectionRaw,
                        "userVsQrSection" to userVsQrSection,
                        "userVsScheduleSection" to userVsScheduleSection,
                        "qrVsScheduleSection" to qrVsScheduleSection,
                        "uidSuffix" to currentUser.uid.takeLast(6)
                    )
                )
                qrScanDebugLog(
                    "H3",
                    "QRScannerFragment.kt:handleQRCodeResult",
                    "schedule_doc_for_qr",
                    mapOf(
                        "scheduleExists" to scheduleExists,
                        "scheduleId" to qrData.scheduleId
                    )
                )
                android.util.Log.e(
                    "QR_DEBUG",
                    "ELIGIBILITY studentSection=[$studentSectionRaw] qrSection=[${qrData.section}] scheduleExists=$scheduleExists scheduleSection=[$scheduleSectionRaw] userVsQr=$userVsQrSection userVsSchedule=$userVsScheduleSection"
                )
                // #endregion

                if (studentSectionRaw.isBlank()) {
                    showToast("Your account has no section assigned. Ask your teacher to update your profile.")
                    progressBar.visibility = View.GONE
                    isProcessing = false
                    stopTimer()
                    barcodeView.postDelayed({
                        if (isAdded && !isDetached) startScanning()
                    }, 2000)
                    return@launch
                }
                if (!scheduleExists) {
                    showToast("This class is not on your schedule.")
                    progressBar.visibility = View.GONE
                    isProcessing = false
                    stopTimer()
                    barcodeView.postDelayed({
                        if (isAdded && !isDetached) startScanning()
                    }, 2000)
                    return@launch
                }
                if (!normSec(studentSectionRaw).equals(normSec(scheduleSectionRaw))) {
                    showToast("This QR code is not for your section.")
                    progressBar.visibility = View.GONE
                    isProcessing = false
                    stopTimer()
                    barcodeView.postDelayed({
                        if (isAdded && !isDetached) startScanning()
                    }, 2000)
                    return@launch
                }

                if (scannedSessions.contains(qrData.scheduleId)) {
                    showToast("You've already marked attendance for this class!")
                    progressBar.visibility = View.GONE
                    isProcessing = false
                    stopTimer()
                    return@launch
                }

                // #region agent log
                debugStep = "STEP3_QUERY_ATTENDANCE"
                android.util.Log.e("QR_DEBUG", "STEP3: querying attendance sessionId=${qrData.sessionId} userId=${currentUser.uid}")
                // #endregion
                val sessionSnap = db.collection("attendance")
                    .whereEqualTo("sessionId", qrData.sessionId)
                    .whereEqualTo("userId", currentUser.uid)
                    .get().await()
                // #region agent log
                android.util.Log.e("QR_DEBUG", "STEP3 OK: found ${sessionSnap.size()} docs")
                // #endregion
                val duplicateThisSession = sessionSnap.documents.isNotEmpty()

                if (duplicateThisSession) {
                    scannedSessions.add(qrData.scheduleId)
                    showToast("You already scanned this QR code.")
                    progressBar.visibility = View.GONE
                    isProcessing = false
                    stopTimer()
                    return@launch
                }

                val sessionStartedAt = sessionDoc.getLong("firstGeneratedAt") ?: sessionDoc.getLong("createdAt") ?: 0L
                val attendanceStatus = if (sessionStartedAt > 0 && System.currentTimeMillis() > sessionStartedAt + 15 * 60 * 1000L) "LATE" else "PRESENT"

                val attendanceData = hashMapOf(
                    "studentId" to currentUser.uid,
                    "userId" to currentUser.uid,
                    "studentName" to studentName,
                    "sessionId" to qrData.sessionId,
                    "teacherId" to qrData.teacherId,
                    "scheduleId" to qrData.scheduleId,
                    "subject" to qrData.subject,
                    "section" to qrData.section,
                    "timestamp" to com.google.firebase.Timestamp.now(),
                    "status" to attendanceStatus,
                    "location" to "",
                    "notes" to ""
                )
                // #region agent log
                qrScanDebugLog(
                    "H5",
                    "QRScannerFragment.kt:handleQRCodeResult",
                    "about_to_write_attendance",
                    mapOf(
                        "storedSectionFromQr" to qrData.section,
                        "studentSectionRaw" to studentSectionRaw,
                        "scheduleId" to qrData.scheduleId
                    )
                )
                // #endregion
                // #region agent log
                debugStep = "STEP4_CREATE_ATTENDANCE"
                android.util.Log.e("QR_DEBUG", "STEP4: creating attendance doc. expiresAt=$expiresAt serverDelta=${expiresAt - System.currentTimeMillis()}ms")
                // #endregion
                val docRef = db.collection("attendance").add(attendanceData).await()
                // #region agent log
                android.util.Log.e("QR_DEBUG", "STEP4 OK: docId=${docRef.id}")
                // #endregion

                scannedSessions.add(qrData.scheduleId)

                progressBar.visibility = View.GONE
                stopTimer()
                isProcessing = false

                val confirmDialog = AttendanceConfirmationDialog.newInstance(
                    studentName = studentName,
                    section = qrData.section,
                    subject = qrData.subject,
                    status = attendanceStatus,
                    timestampMillis = System.currentTimeMillis()
                )
                confirmDialog.onDoneListener = {
                    // Reset bottom nav to Home tab
                    (activity as? StudentMainActivity)?.navigateToDashboard()
                }
                confirmDialog.show(parentFragmentManager, "attendance_confirmation")

            } catch (e: Exception) {
                // #region agent log
                val fs = e as? FirebaseFirestoreException
                val cause = e.cause as? FirebaseFirestoreException
                val code = fs?.code ?: cause?.code
                android.util.Log.e("QR_DEBUG", "FAILED at $debugStep: ${code?.name ?: "no-code"} ${e.javaClass.simpleName}: ${e.message}")
                android.util.Log.e("QR_DEBUG", "Full stack:", e)
                // #endregion
                val raw = e.message.orEmpty()
                val toastMsg = if (raw.contains("index", ignoreCase = true) || raw.contains("FAILED_PRECONDITION", ignoreCase = true)) {
                    "Not a camera issue: Firestore needs an index. Use the link in Logcat or deploy firestore.indexes.json."
                } else {
                    "[$debugStep] ${e.message ?: "Error processing QR code"}"
                }
                showToast(toastMsg)
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
