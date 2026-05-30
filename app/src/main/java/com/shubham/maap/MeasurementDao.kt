package com.shubham.maap

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM measurements ORDER BY timestamp DESC")
    fun getAllMeasurements(): Flow<List<Measurement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(measurement: Measurement)

    @Delete
    suspend fun delete(measurement: Measurement)

    @Query("SELECT SUM(area) FROM measurements")
    fun getTotalArea(): Flow<Double?>
}
