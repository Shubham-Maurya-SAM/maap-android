package com.shubham.maap

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "measurements")
data class Measurement(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val roomName: String,
    val shape: String,
    val dimensions: String = "",
    val area: Double,
    val imagePath: String? = null, // Path to the saved screenshot
    val timestamp: Long = System.currentTimeMillis(),
)
