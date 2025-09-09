package com.example.attendancesystem

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import androidx.work.ListenableWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class QRCodeWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): ListenableWorker.Result {
        val scheduleId = inputData.getString("scheduleId") ?: return ListenableWorker.Result.failure()
        val db = FirebaseFirestore.getInstance()
        val teacherId = FirebaseAuth.getInstance().currentUser?.uid ?: return ListenableWorker.Result.failure()
        val scheduleDoc = db.collection("schedules").document(scheduleId).get().await()
        val schedule = scheduleDoc.data ?: return ListenableWorker.Result.failure()
        // Generate QR/session for this schedule
        val session = hashMapOf(
            "teacherId" to teacherId,
            "scheduleId" to scheduleId,
            "subject" to schedule["subject"],
            "section" to schedule["section"],
            "day" to schedule["day"],
            "time" to schedule["time"],
            "room" to schedule["room"],
            "createdAt" to System.currentTimeMillis(),
            "expiresAt" to System.currentTimeMillis() + 15 * 60 * 1000 // 15 min expiry
        )
        db.collection("attendance_sessions").add(session).await()
        // TODO: Optionally notify the teacher
        return ListenableWorker.Result.success()
    }
}
