package com.example.attendancesystem.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.attendancesystem.models.QRCodeData
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class QRViewModel : ViewModel() {
    private val _qrCodeData = MutableLiveData<QRCodeData>()
    val qrCodeData: LiveData<QRCodeData> = _qrCodeData

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val auth = FirebaseAuth.getInstance()
    private val userId = auth.currentUser?.uid ?: ""

    fun generateQRCode(teacherId: String, sessionId: String, scheduleId: String, subject: String, section: String, expirationMinutes: Int = QRCodeData.DEFAULT_EXPIRATION_MINUTES) {
        viewModelScope.launch {
            try {
                val qrData = QRCodeData.createWithExpiration(
                    teacherId = teacherId,
                    sessionId = sessionId,
                    userId = userId,
                    scheduleId = scheduleId,
                    subject = subject,
                    section = section,
                    expirationMinutes = expirationMinutes
                )
                _qrCodeData.value = qrData
            } catch (e: Exception) {
                _error.value = e.message ?: "Error generating QR code"
            }
        }
    }

    fun updateExpirationTime(teacherId: String, sessionId: String, minutes: Int) {
        val currentData = _qrCodeData.value ?: return
        generateQRCode(
            teacherId = teacherId,
            sessionId = sessionId,
            scheduleId = currentData.scheduleId,
            subject = currentData.subject,
            section = currentData.section,
            expirationMinutes = minutes
        )
    }
}