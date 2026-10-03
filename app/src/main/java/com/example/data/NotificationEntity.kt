package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "Pending", // Pending, Completed, Cancelled, Low Battery
    val locationLabel: String = "Auto Stand Module",
    val batteryLevel: Int? = null,
    val requestId: String = "",
    val replyStatus: String? = null, // null, "coming", "busy"
    val replyTimestamp: Long? = null
)
