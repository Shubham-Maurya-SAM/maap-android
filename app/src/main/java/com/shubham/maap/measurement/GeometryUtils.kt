package com.shubham.maap.measurement

import com.google.ar.core.Pose
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Core geometry logic for AR measurements.
 */
object GeometryUtils {

    /**
     * Calculates the Euclidean distance between two AR points in meters.
     */
    fun calculateDistance(p1: Pose, p2: Pose): Float {
        val dx = p1.tx() - p2.tx()
        val dy = p1.ty() - p2.ty()
        val dz = p1.tz() - p2.tz()
        return sqrt(((dx * dx) + (dy * dy) + (dz * dz)).toDouble()).toFloat()
    }

    /**
     * Calculates the area of a horizontal polygon using the Shoelace formula (Surveyor's formula).
     * @param poses List of points in 3D space, assumed to be on a flat floor.
     * @return Area in square meters.
     */
    fun calculateArea(poses: List<Pose>): Double {
        if (poses.size < 3) return 0.0
        
        var area = 0.0
        for (i in poses.indices) {
            val j = (i + 1) % poses.size
            // Use X and Z coordinates for horizontal plane area
            area += (poses[i].tx() * poses[j].tz()) - (poses[j].tx() * poses[i].tz())
        }
        
        return abs(area) / 2.0
    }

    /**
     * Converts square meters to square feet.
     */
    fun sqMetersToSqFt(area: Double): Double = area * 10.7639
    
    /**
     * Converts meters to feet.
     */
    fun metersToFeet(meters: Float): Float = meters * 3.28084f
}
