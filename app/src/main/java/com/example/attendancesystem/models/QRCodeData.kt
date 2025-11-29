package com.example.attendancesystem.models

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.util.*

data class QRCodeData(
    val teacherId: String,
    val sessionId: String,
    val userId: String,
    val timestamp: Long,
    val scheduleId: String,
    val subject: String,
    val section: String,
    val expirationMinutes: Int = DEFAULT_EXPIRATION_MINUTES,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationTimestamp: Long? = null
) {
    companion object {
        const val DEFAULT_EXPIRATION_MINUTES = 5

        fun createWithExpiration(
            teacherId: String,
            sessionId: String,
            userId: String,
            scheduleId: String,
            subject: String,
            section: String,
            expirationMinutes: Int = DEFAULT_EXPIRATION_MINUTES,
            latitude: Double? = null,
            longitude: Double? = null
        ): QRCodeData {
            return QRCodeData(
                teacherId = teacherId,
                sessionId = sessionId,
                userId = userId,
                timestamp = System.currentTimeMillis(),
                scheduleId = scheduleId,
                subject = subject,
                section = section,
                expirationMinutes = expirationMinutes,
                latitude = latitude,
                longitude = longitude,
                locationTimestamp = if (latitude != null && longitude != null) System.currentTimeMillis() else null
            )
        }

        fun fromJson(json: String): QRCodeData {
            return try {
                Gson().fromJson(json, QRCodeData::class.java)
            } catch (e: JsonSyntaxException) {
                throw IllegalArgumentException("Invalid QR code data format")
            }
        }
    }

    fun toJson(): String {
        return Gson().toJson(this)
    }

    fun isExpired(): Boolean {
        val expirationTime = timestamp + (expirationMinutes * 60 * 1000)
        return System.currentTimeMillis() > expirationTime
    }

    fun getRemainingTimeInMillis(): Long {
        val expirationTime = timestamp + (expirationMinutes * 60 * 1000)
        val remaining = expirationTime - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0
    }

    fun hasLocation(): Boolean {
        return latitude != null && longitude != null
    }

    fun getLocationData(): com.example.attendancesystem.utils.LocationManager.LocationData? {
        return if (hasLocation()) {
            com.example.attendancesystem.utils.LocationManager.LocationData(
                latitude = latitude!!,
                longitude = longitude!!,
                timestamp = locationTimestamp ?: timestamp
            )
        } else {
            null
        }
    }
}