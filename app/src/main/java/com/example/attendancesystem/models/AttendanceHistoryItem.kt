package com.example.attendancesystem.models

data class AttendanceHistoryItem(
    val id: String = "",
    val studentName: String = "",
    val date: String = "",
    val time: String = "",
    val status: String = "",
    val subject: String = "",
    val location: String = ""
)
