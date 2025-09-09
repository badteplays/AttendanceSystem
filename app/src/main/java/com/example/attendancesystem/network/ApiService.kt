package com.example.attendancesystem.network

import com.example.attendancesystem.models.LoginResponse
import com.example.attendancesystem.models.RegisterRequest
import com.example.attendancesystem.models.AttendanceRecord
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit
import android.content.Context
import android.content.SharedPreferences

interface ApiService {
    @POST("api/register")
    suspend fun register(@Body request: RegisterRequest): Response<Unit>

    @POST("api/login")
    suspend fun login(@Body credentials: Map<String, String>): Response<LoginResponse>

    @POST("api/attendance")
    suspend fun recordAttendance(@Header("Authorization") token: String): Response<Unit>

    @GET("api/attendance")
    suspend fun getAttendance(@Header("Authorization") token: String): Response<List<AttendanceRecord>>

    companion object {
        private const val PREFS_NAME = "ServerPrefs"
        private const val KEY_SERVER_IP = "server_ip"
        private const val KEY_USE_HTTPS = "use_https"
        private const val DEFAULT_IP = "192.168.1.100" // Replace with your local IP for testing
        private const val DEFAULT_USE_HTTPS = false // Use HTTP for local testing

        fun create(context: Context): ApiService {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val serverIp = prefs.getString(KEY_SERVER_IP, DEFAULT_IP) ?: DEFAULT_IP
            val useHttps = prefs.getBoolean(KEY_USE_HTTPS, DEFAULT_USE_HTTPS)
            
            val protocol = if (useHttps) "https" else "http"
            val port = if (useHttps) "443" else "5000"
            val baseUrl = "$protocol://$serverIp:$port/"

            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val clientBuilder = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)

            // Only add SSL configuration if using HTTPS
            if (useHttps) {
                // For production, use proper certificate validation
                // For now, we'll use HTTP for local testing to avoid certificate issues
                android.util.Log.w("ApiService", "HTTPS is enabled but not recommended for local testing")
            }

            val client = clientBuilder.build()

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }

        fun updateServerIp(context: Context, newIp: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_SERVER_IP, newIp).apply()
        }
        
        fun updateUseHttps(context: Context, useHttps: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_USE_HTTPS, useHttps).apply()
        }
    }
} 