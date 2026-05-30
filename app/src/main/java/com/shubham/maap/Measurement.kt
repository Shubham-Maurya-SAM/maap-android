package com.shubham.maap

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "measurements")
data class Measurement(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val roomName: String,
    val shape: String,
    val dimensions: String = "", // Added to store e.g. "10 x 12 ft"
    val area: Double,
    val timestamp: Long = System.currentTimeMillis(),
)
