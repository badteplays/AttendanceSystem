package com.example.attendancesystem.models

import com.google.firebase.Timestamp

data class Routine(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val day: String = "", // Monday, Tuesday, etc.
    val startTime: String = "", // HH:mm format
    val endTime: String = "", // HH:mm format
    val color: String = "#4CAF50", // Hex color for UI
    val timestamp: Timestamp = Timestamp.now()
) {
    fun getTimeRange(): String {
        return "$startTime - $endTime"
    }
    
    fun toMap(): HashMap<String, Any> {
        return hashMapOf(
            "userId" to userId,
            "title" to title,
            "description" to description,
            "day" to day,
            "startTime" to startTime,
            "endTime" to endTime,
            "color" to color,
            "timestamp" to timestamp
        )
    }
    
    companion object {
        fun fromMap(id: String, data: Map<String, Any>): Routine {
            return Routine(
                id = id,
                userId = data["userId"] as? String ?: "",
                title = data["title"] as? String ?: "",
                description = data["description"] as? String ?: "",
                day = data["day"] as? String ?: "",
                startTime = data["startTime"] as? String ?: "",
                endTime = data["endTime"] as? String ?: "",
                color = data["color"] as? String ?: "#4CAF50",
                timestamp = data["timestamp"] as? Timestamp ?: Timestamp.now()
            )
        }
    }
}





