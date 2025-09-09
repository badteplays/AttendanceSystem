package com.example.attendancesystem.models

import com.google.firebase.Timestamp

data class Attendance(
    var id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val location: String = "",
    var status: AttendanceStatus = AttendanceStatus.PRESENT,
    var notes: String = "",
    val scheduleId: String = "",
    val subject: String = "",
    val section: String = "",
    val sessionId: String = ""
)