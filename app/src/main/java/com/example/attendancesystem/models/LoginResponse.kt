package com.example.attendancesystem.models

data class LoginResponse(
    val success: Boolean = false,
    val message: String = "",
    val token: String = "",
    val userId: String = "",
    val userType: String = "",
    val userName: String = ""
) 