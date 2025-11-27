package com.example.attendancesystem.models

data class ScheduleItem(
    val id: String = "",
    val name: String = "",
    val time: String = "",
    val location: String = "",
    val day: String = "",
    val subject: String = "",
    val teacher: String = "",
    val isActive: Boolean = true
)
