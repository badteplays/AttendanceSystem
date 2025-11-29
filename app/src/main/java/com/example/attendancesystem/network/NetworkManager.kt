package com.example.attendancesystem.network

import android.content.Context
import com.example.attendancesystem.models.LoginResponse
import com.example.attendancesystem.models.RegisterRequest
import com.example.attendancesystem.models.AttendanceRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

class NetworkManager(context: Context) {
    private val apiService = ApiService.create(context)

    suspend fun register(request: RegisterRequest): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.register(request)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("Registration failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<LoginResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.login(mapOf(
                "email" to email,
                "password" to password
            ))
            if (response.isSuccessful) {
                response.body()?.let {
                    Result.success(it)
                } ?: Result.failure(IOException("Empty response body"))
            } else {
                Result.failure(IOException("Login failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordAttendance(token: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.recordAttendance("Bearer $token")
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("Failed to record attendance: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAttendanceHistory(token: String): Result<List<AttendanceRecord>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getAttendance("Bearer $token")
            if (response.isSuccessful) {
                response.body()?.let {
                    Result.success(it)
                } ?: Result.failure(IOException("Empty response body"))
            } else {
                Result.failure(IOException("Failed to get attendance: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        fun updateServerIp(context: Context, newIp: String) {
            ApiService.updateServerIp(context, newIp)
        }
    }
}