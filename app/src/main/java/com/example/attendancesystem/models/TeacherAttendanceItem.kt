package com.example.attendancesystem.models

data class TeacherAttendanceItem(
    val documentId: String,
    val studentName: String,
    val timeTaken: String,
    val section: String,
    val status: String = "PRESENT"
)
