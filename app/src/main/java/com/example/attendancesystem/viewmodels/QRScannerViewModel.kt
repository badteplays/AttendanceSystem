package com.example.attendancesystem.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.attendancesystem.models.QRCodeData
import com.example.attendancesystem.models.AttendanceStatus
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.*

class QRScannerViewModel(application: Application) : AndroidViewModel(application) {
    private val _scanResult = MutableLiveData<ScanResult>()
    val scanResult: LiveData<ScanResult> = _scanResult

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val firestore = FirebaseFirestore.getInstance()

    fun processQRCode(qrCodeData: QRCodeData, userId: String) {
        viewModelScope.launch {
            try {
                _loading.value = true

                // Validate QR code expiration
                if (qrCodeData.isExpired()) {
                    _scanResult.value = ScanResult.Error("QR code has expired")
                    return@launch
                }

                // Check if this student already has an attendance record for this session
                firestore.collection("attendance")
                    .whereEqualTo("studentId", userId)
                    .whereEqualTo("scheduleId", qrCodeData.scheduleId)
                    .get()
                    .addOnSuccessListener { docs ->
                        if (docs.isEmpty) {
                            // No attendance yet for this session, allow scan
                            firestore.collection("users").document(userId).get()
                                .addOnSuccessListener { userDoc ->
                                    val studentName = userDoc.getString("name") ?: "Student"
                                    val attendanceData = hashMapOf(
                                        "studentId" to userId,
                                        "userId" to userId,
                                        "studentName" to studentName,
                                        "timestamp" to com.google.firebase.Timestamp.now(),
                                        "status" to AttendanceStatus.PRESENT.name,
                                        "sessionId" to qrCodeData.sessionId,
                                        "scheduleId" to qrCodeData.scheduleId,
                                        "subject" to qrCodeData.subject,
                                        "section" to qrCodeData.section
                                    )
                                    firestore.collection("attendance")
                                        .add(attendanceData)
                                        .addOnSuccessListener { docRef ->
                                            android.widget.Toast.makeText(
                                                getApplication<Application>(),
                                                "Attendance SAVED for ${studentName}\nSessionId: ${qrCodeData.sessionId}",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                            android.util.Log.d(
                                                "QRScannerViewModel",
                                                "Attendance SAVED for ${studentName}, sessionId=${qrCodeData.sessionId}, docId=${docRef.id}"
                                            )
                                            _scanResult.postValue(ScanResult.Success)
                                        }
                                        .addOnFailureListener { e ->
                                            android.widget.Toast.makeText(
                                                getApplication<Application>(),
                                                "ERROR saving attendance: ${e.message}",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                            android.util.Log.e(
                                                "QRScannerViewModel",
                                                "ERROR saving attendance: ${e.message}"
                                            )
                                            _scanResult.postValue(ScanResult.Error(e.message ?: "Failed to record attendance"))
                                        }
                                }
                                .addOnFailureListener { e ->
                                    _scanResult.postValue(ScanResult.Error("Could not fetch student name: ${e.message}"))
                                }
                        } else {
                            // Already scanned for this session
                            _scanResult.postValue(ScanResult.Error("You have already scanned for this session. Wait for the next QR code."))
                        }
                    }
                    .addOnFailureListener { e ->
                        _scanResult.postValue(ScanResult.Error("Error checking previous attendance: ${e.message}"))
                    }
                return@launch

            } catch (e: Exception) {
                _scanResult.value = ScanResult.Error(e.message ?: "An error occurred")
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearScanResult() {
        _scanResult.value = null
    }

    sealed class ScanResult {
        object Success : ScanResult()
        data class Error(val message: String) : ScanResult()
        object InvalidCode : ScanResult()
    }
}
