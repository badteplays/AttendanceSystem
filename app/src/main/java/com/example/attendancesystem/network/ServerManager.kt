package com.example.attendancesystem.network

import android.content.Context
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import com.example.attendancesystem.models.AttendanceRecord
import com.google.firebase.Timestamp

class ServerManager private constructor(private val context: Context) {
    private val db = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val networkManager = NetworkManager(context)

    companion object {
        @Volatile
        private var INSTANCE: ServerManager? = null

        fun getInstance(context: Context): ServerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ServerManager(context).also { INSTANCE = it }
            }
        }
    }

    suspend fun checkServerConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            try {

                db.collection("health")
                    .document("status")
                    .get()
                    .await()
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun recordAttendance(studentId: String, qrCodeData: String, status: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val qrSnapshot = db.collection("qrcodes")
                .whereEqualTo("data", qrCodeData)
                .whereEqualTo("isValid", true)
                .get()
                .await()

            if (qrSnapshot.isEmpty) {
                return@withContext Result.failure(Exception("Invalid QR code"))
            }

            val qrDoc = qrSnapshot.documents[0]
            val attendanceRecord = hashMapOf(
                "studentId" to studentId,
                "qrCodeId" to qrDoc.id,
                "timestamp" to Timestamp.now(),
                "status" to status
            )

            db.collection("attendance")
                .add(attendanceRecord)
                .await()

            qrDoc.reference.update("isValid", false).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendQRCodeToServer(qrCodeData: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val qrDoc = hashMapOf(
                "data" to qrCodeData,
                "userId" to userId,
                "timestamp" to Timestamp.now(),
                "isValid" to true
            )

            db.collection("qrcodes")
                .add(qrDoc)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyQRCode(qrCodeData: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val snapshot = db.collection("qrcodes")
                .whereEqualTo("data", qrCodeData)
                .whereEqualTo("isValid", true)
                .get()
                .await()

            Result.success(!snapshot.isEmpty)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAttendanceHistory(studentId: String): Result<List<AttendanceRecord>> = withContext(Dispatchers.IO) {
        try {
            val snapshot = db.collection("attendance")
                .whereEqualTo("studentId", studentId)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()

            val records = snapshot.documents.mapNotNull { doc ->
                doc.toObject(AttendanceRecord::class.java)
            }

            Result.success(records)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deactivateQRCode(code: String) {
        withContext(Dispatchers.IO) {
            try {
                val snapshot = db.collection("qr_codes")
                    .whereEqualTo("qrData", code)
                    .whereEqualTo("active", true)
                    .get()
                    .await()

                for (document in snapshot.documents) {
                    document.reference.update("active", false).await()
                }
            } catch (e: Exception) {

            }
        }
    }
}