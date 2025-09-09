package com.example.attendancesystem.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationManager(private val context: Context) {
    
    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val CLASSROOM_RADIUS_METERS = 100.0 // 100 meters radius
        private const val LOCATION_UPDATE_INTERVAL = 10000L // 10 seconds
        private const val LOCATION_FASTEST_INTERVAL = 5000L // 5 seconds
    }
    
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)
    
    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        LOCATION_UPDATE_INTERVAL
    ).apply {
        setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
        setMaxUpdateDelayMillis(LOCATION_UPDATE_INTERVAL)
    }.build()
    
    data class LocationData(
        val latitude: Double,
        val longitude: Double,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    
    suspend fun getCurrentLocation(): LocationData {
        return suspendCancellableCoroutine { continuation ->
            if (!hasLocationPermission()) {
                continuation.resumeWithException(SecurityException("Location permission not granted"))
                return@suspendCancellableCoroutine
            }
            
            if (!isLocationEnabled()) {
                continuation.resumeWithException(Exception("Location services are disabled"))
                return@suspendCancellableCoroutine
            }
            
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        val locationData = LocationData(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            timestamp = System.currentTimeMillis()
                        )
                        continuation.resume(locationData)
                    } else {
                        // If last location is null, request fresh location
                        requestFreshLocation(continuation)
                    }
                }.addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
            } catch (e: SecurityException) {
                continuation.resumeWithException(e)
            }
        }
    }
    
    private fun requestFreshLocation(continuation: kotlinx.coroutines.CancellableContinuation<LocationData>) {
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                val location = locationResult.lastLocation
                if (location != null) {
                    val locationData = LocationData(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        timestamp = System.currentTimeMillis()
                    )
                    continuation.resume(locationData)
                    fusedLocationClient.removeLocationUpdates(this)
                } else {
                    continuation.resumeWithException(Exception("Unable to get location"))
                }
            }
        }
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            continuation.resumeWithException(e)
        }
    }
    
    fun calculateDistance(loc1: LocationData, loc2: LocationData): Double {
        val results = FloatArray(1)
        Location.distanceBetween(
            loc1.latitude, loc1.longitude,
            loc2.latitude, loc2.longitude,
            results
        )
        return results[0].toDouble()
    }
    
    fun isWithinClassroomRadius(
        studentLocation: LocationData,
        teacherLocation: LocationData,
        radiusMeters: Double = CLASSROOM_RADIUS_METERS
    ): Boolean {
        val distance = calculateDistance(studentLocation, teacherLocation)
        return distance <= radiusMeters
    }
    
    fun validateLocationForQRScan(
        studentLocation: LocationData,
        qrCodeLocation: LocationData
    ): LocationValidationResult {
        return try {
            val distance = calculateDistance(studentLocation, qrCodeLocation)
            
            when {
                distance <= CLASSROOM_RADIUS_METERS -> {
                    LocationValidationResult.Valid("Location verified successfully - you are within ${distance.toInt()}m of the teacher")
                }
                else -> {
                    LocationValidationResult.Invalid("You must be within 100m of the teacher to mark attendance. You are ${distance.toInt()}m away.")
                }
            }
        } catch (e: Exception) {
            LocationValidationResult.Error("Error validating location: ${e.message}")
        }
    }
    
    sealed class LocationValidationResult {
        data class Valid(val message: String) : LocationValidationResult()
        data class Warning(val message: String) : LocationValidationResult()
        data class Invalid(val message: String) : LocationValidationResult()
        data class Error(val message: String) : LocationValidationResult()
    }
} 