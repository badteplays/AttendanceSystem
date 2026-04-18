package com.example.attendancesystem.models

enum class AttendanceDocSource {
    ACTIVE,
    ARCHIVED,
    BOTH
}

data class TeacherAttendanceItem(
    val documentId: String,
    val studentName: String,
    val timeTaken: String,
    val section: String,
    val status: String = "PRESENT",
    val source: AttendanceDocSource = AttendanceDocSource.ACTIVE
)
