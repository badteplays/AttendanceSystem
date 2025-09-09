package com.example.attendancesystem.models

data class Schedule(
    val id: String = "",
    val subject: String = "",
    val section: String = "",
    val teacherId: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val day: String = "",
    val room: String = "",
    val lastGeneratedDate: String = "" // For auto QR generation control
)