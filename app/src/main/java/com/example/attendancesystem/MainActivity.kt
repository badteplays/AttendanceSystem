package com.example.attendancesystem

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import com.example.attendancesystem.network.ServerManager
import com.example.attendancesystem.models.Schedule
import android.widget.NumberPicker
import android.content.Intent

class MainActivity : AppCompatActivity() {
    private lateinit var qrCodeImage: ImageView
    private lateinit var renewQRButton: Button
    private lateinit var studentListView: ListView
    private lateinit var addStudentFab: FloatingActionButton
    private lateinit var serverManager: ServerManager
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var expirationTimeText: TextView
    private lateinit var btnLogout: Button
    private lateinit var btnScheduleSection: Button

    private var currentQRCodeData: String = ""
    private lateinit var prefs: SharedPreferences
    private lateinit var dataFile: File

    private val studentList = ArrayList<String>()
    private lateinit var studentAdapter: ArrayAdapter<String>

    private var currentExpirationMinutes: Int = 5 // Default expiration time

    private val dynamicUpdateInterval = 30000L // Update every 30 seconds
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Prevent screenshots and screen recording
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        initializeViews()
        setupData()
        setupListeners()
        checkServerConnection()
    }

    private fun initializeViews() {
        qrCodeImage = findViewById(R.id.qrCodeImage)
        renewQRButton = findViewById(R.id.renewQRButton)
        studentListView = findViewById(R.id.studentListView)
        addStudentFab = findViewById(R.id.addStudentFab)
        expirationTimeText = findViewById(R.id.expirationTimeText)
        btnLogout = findViewById(R.id.btnLogout)
        btnScheduleSection = findViewById(R.id.btnScheduleSection)
    }

    private fun setupData() {
        prefs = getSharedPreferences("QRPrefs", Context.MODE_PRIVATE)
        dataFile = File(filesDir, "attendance_data.json")
        serverManager = ServerManager.getInstance(this)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        studentAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, studentList)
        studentListView.adapter = studentAdapter

        checkAndGenerateQRCode()
        loadSavedData()
    }

    private fun setupListeners() {
        renewQRButton.setOnClickListener { renewAttendance() }
        addStudentFab.setOnClickListener { showAddStudentDialog() }
        findViewById<Button>(R.id.btnSetExpiration).setOnClickListener {
            showExpirationDialog()
        }
        btnLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
        btnScheduleSection.setOnClickListener {
            val intent = Intent(this, ScheduleActivity::class.java)
            startActivity(intent)
        }
    }

    private fun generateQRCode(schedule: Schedule) {
        val timestamp = System.currentTimeMillis()
        val expirationTime = Timestamp(Date(timestamp + currentExpirationMinutes * 60 * 1000))
        
        val qrCodeData = mapOf(
            "scheduleId" to schedule.id,
            "subject" to schedule.subject,
            "section" to schedule.section,
            "timestamp" to timestamp,
            "dynamicToken" to generateDynamicToken(timestamp),
            "expirationTime" to expirationTime.seconds
        )

        val json = Gson().toJson(qrCodeData)
        val displayWidth = resources.displayMetrics.widthPixels * 0.7f

        val hints = hashMapOf<EncodeHintType, Any>().apply {
            put(EncodeHintType.MARGIN, 0)
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
        }

        try {
            val bitMatrix = MultiFormatWriter().encode(
                json,
                BarcodeFormat.QR_CODE,
                displayWidth.toInt(),
                displayWidth.toInt(),
                hints
            )

            val barcodeEncoder = BarcodeEncoder()
            val bitmap = barcodeEncoder.createBitmap(bitMatrix)
            qrCodeImage.setImageBitmap(bitmap)

            startExpirationCountdown(expirationTime)
        } catch (e: WriterException) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.qr_code_generation_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateDynamicToken(timestamp: Long): String {
        val timeBlock = timestamp / dynamicUpdateInterval
        return timeBlock.hashCode().toString()
    }

    private fun startExpirationCountdown(expirationTime: Timestamp) {
        countDownTimer?.cancel()
        
        val millisUntilExpiration = (expirationTime.seconds - Timestamp.now().seconds) * 1000L
        
        countDownTimer = object : CountDownTimer(millisUntilExpiration, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 60000
                val seconds = (millisUntilFinished % 60000) / 1000
                updateExpirationDisplay(minutes, seconds)
            }

            override fun onFinish() {
                handleExpiredQRCode()
            }
        }.start()
    }

    private fun updateExpirationDisplay(minutes: Long, seconds: Long) {
        val timeText = String.format(
            Locale.getDefault(),
            getString(R.string.expiration_time_format),
            minutes,
            seconds
        )
        expirationTimeText.text = timeText
    }

    private fun handleExpiredQRCode() {
        qrCodeImage.setImageResource(android.R.color.transparent)
        Toast.makeText(this, getString(R.string.qr_code_expired), Toast.LENGTH_LONG).show()
    }

    private fun getCurrentSchedule(): Schedule {
        // Implementation of actual schedule retrieval from database
        return Schedule(
            id = UUID.randomUUID().toString(),
            subject = "Sample Subject",
            section = "A",
            teacherId = auth.currentUser?.uid ?: "",
            startTime = "09:00",
            endTime = "10:30",
            day = "Monday"
        )
    }

    override fun onStart() {
        super.onStart()
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(android.content.Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }

    private fun checkServerConnection() {
        lifecycleScope.launch {
            try {
                if (!serverManager.checkServerConnection()) {
                    Toast.makeText(this@MainActivity, getString(R.string.no_internet_connection), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, getString(R.string.server_connection_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkAndGenerateQRCode() {
        val currentTime = System.currentTimeMillis()
        val lastGeneratedTime = prefs.getLong("lastGeneratedTime", 0)
        val qrCodeDuration = currentExpirationMinutes * 60 * 1000 // Convert minutes to milliseconds

        if (currentTime - lastGeneratedTime >= qrCodeDuration) {
            getCurrentSchedule()?.let { schedule ->
                generateQRCode(schedule)
            }
        } else {
            loadExistingQRCode()
        }
    }

    private fun loadExistingQRCode() {
        val lastQRCode = prefs.getString("lastQRCode", "")
        val lastGeneratedTime = prefs.getLong("lastGeneratedTime", 0)
        if (!lastQRCode.isNullOrEmpty() && lastGeneratedTime > 0) {
            currentQRCodeData = lastQRCode
            try {
                val barcodeEncoder = BarcodeEncoder()
                val bitmap = barcodeEncoder.encodeBitmap(currentQRCodeData, BarcodeFormat.QR_CODE, 400, 400)
                qrCodeImage.setImageBitmap(bitmap)

                // Calculate remaining time and start timer
                val expirationMillis = lastGeneratedTime + currentExpirationMinutes * 60 * 1000
                val millisUntilExpiration = expirationMillis - System.currentTimeMillis()
                if (millisUntilExpiration > 0) {
                    startExpirationCountdown(Timestamp(Date(expirationMillis)))
                } else {
                    handleExpiredQRCode()
                }
            } catch (e: WriterException) {
                e.printStackTrace()
            }
        } else {
            generateNewQRCode()
        }
    }

    private fun generateNewQRCode() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentTime = dateFormat.format(Date())
        // ... existing logic ...
        try {
            val barcodeEncoder = BarcodeEncoder()
            val bitmap = barcodeEncoder.encodeBitmap(currentQRCodeData, BarcodeFormat.QR_CODE, 400, 400)
            qrCodeImage.setImageBitmap(bitmap)

            prefs.edit().apply {
                putLong("lastGeneratedTime", System.currentTimeMillis())
                putString("lastQRCode", currentQRCodeData)
                apply()
            }
            
            val expirationMillis = System.currentTimeMillis() + currentExpirationMinutes * 60 * 1000
            startExpirationCountdown(Timestamp(Date(expirationMillis)))
            sendQRCodeToServer(currentQRCodeData)
        } catch (e: WriterException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to generate QR code", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSavedData() {
        if (dataFile.exists()) {
            try {
                val jsonString = dataFile.readText()
                val jsonObject = JSONObject(jsonString)
                val studentsArray = jsonObject.getJSONArray("students")
                
                studentList.clear()
                for (i in 0 until studentsArray.length()) {
                    studentList.add(studentsArray.getString(i))
                }
                studentAdapter.notifyDataSetChanged()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, getString(R.string.error_loading_data), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renewAttendance() {
        getCurrentSchedule()?.let { schedule ->
            generateQRCode(schedule)
        }
    }

    private fun showAddStudentDialog() {
        val builder = AlertDialog.Builder(this)
        val input = EditText(this).apply {
            hint = getString(R.string.enter_student_name)
        }

        builder.setTitle(getString(R.string.add_student))
            .setView(input)
            .setPositiveButton(getString(R.string.add)) { _, _ ->
                val studentName = input.text.toString()
                if (studentName.isNotEmpty()) {
                    studentList.add(studentName)
                    studentAdapter.notifyDataSetChanged()
                    saveStudentList()
                }
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun saveStudentList() {
        try {
            val jsonObject = JSONObject()
            jsonObject.put("students", JSONObject.wrap(studentList))
            
            FileOutputStream(dataFile).use { output ->
                output.write(jsonObject.toString().toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.error_saving_data), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showExpirationDialog() {
        val builder = AlertDialog.Builder(this)
        val input = NumberPicker(this).apply {
            minValue = 1
            maxValue = 60
            value = currentExpirationMinutes
        }

        builder.setTitle(getString(R.string.set_expiration_time))
            .setMessage(getString(R.string.select_expiration_minutes))
            .setView(input)
            .setPositiveButton(getString(R.string.set)) { _, _ ->
                currentExpirationMinutes = input.value
                getCurrentSchedule()?.let { schedule ->
                    generateQRCode(schedule)
                }
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun sendQRCodeToServer(qrData: String) {
        val db = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val qrInfo = hashMapOf(
            "qrData" to qrData,
            "generatedAt" to System.currentTimeMillis(),
            "teacherId" to (auth.currentUser?.uid ?: ""),
            // Add more fields as needed (e.g., scheduleId, subject, etc.)
        )
        db.collection("qrcodes")
            .add(qrInfo)
            .addOnSuccessListener { /* Optionally show a success message */ }
            .addOnFailureListener { e -> e.printStackTrace() }
    }
}