package com.example.attendancesystem.models

import com.google.firebase.Timestamp

data class AttendanceRecord(
    val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val status: AttendanceStatus = AttendanceStatus.PRESENT,
    val subject: String = "",
    val scheduleId: String = "",
    val location: String = "",
    val notes: String = ""
)