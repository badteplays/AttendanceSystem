package com.example.attendancesystem

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.attendancesystem.databinding.ActivityStudentScannerBinding
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CaptureManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.attendancesystem.models.QRCodeData

class StudentScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentScannerBinding
    private lateinit var captureManager: CaptureManager
    private var isProcessing = false

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (checkCameraPermission()) {
            initializeScanner()
        } else {
            requestCameraPermission()
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeScanner()
            } else {
                Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun initializeScanner() {
        binding.barcodeScannerView.apply {
            decodeContinuous(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult) {
                    if (!isProcessing) {
                        isProcessing = true
                        processQRCode(result.text)
                    }
                }
            })
            resume()
        }
    }

    private fun processQRCode(qrContent: String) {
        try {
            val qrData = QRCodeData.fromJson(qrContent)
            if (qrData != null) {
                // Pass the QR data to QRScannerActivity for processing
                val intent = Intent(this, QRScannerActivity::class.java).apply {
                    putExtra("qr_content", qrContent)
                }
                startActivity(intent)
                finish() // Close this activity after passing data
            } else {
                Toast.makeText(this, getString(R.string.invalid_qr_code), Toast.LENGTH_SHORT).show()
                isProcessing = false
            }
        } catch (e: Exception) {
            Log.e("StudentScannerActivity", "Error processing QR code", e)
            Toast.makeText(this, getString(R.string.error), Toast.LENGTH_SHORT).show()
            isProcessing = false
        }
    }

    override fun onResume() {
        super.onResume()
        binding.barcodeScannerView.resume()
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
