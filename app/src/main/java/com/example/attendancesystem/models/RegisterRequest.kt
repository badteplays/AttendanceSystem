package com.example.attendancesystem.models

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String,
    val userType: String,
    val studentId: String? = null,
    val department: String? = null,
    val course: String? = null
)