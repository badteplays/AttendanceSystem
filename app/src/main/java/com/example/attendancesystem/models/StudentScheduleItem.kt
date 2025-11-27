package com.example.attendancesystem.models

data class StudentScheduleItem(
    val id: String,
    val subject: String,
    val section: String,
    val day: String,
    val time: String,
    val startTime: String,
    val endTime: String,
    val notes: String,
    val teacherId: String
)
