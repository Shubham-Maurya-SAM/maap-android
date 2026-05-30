package com.shubham.maap

import androidx.lifecycle.ViewModel
import com.google.ar.core.Anchor
import com.shubham.maap.measurement.GeometryUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel managing the measurement state and data logic.
 */
class MeasureViewModel : ViewModel() {

    private val _anchors = MutableStateFlow<List<Anchor>>(emptyList())
    val anchors: StateFlow<List<Anchor>> = _anchors.asStateFlow()

    private val _currentAreaSqFt = MutableStateFlow(0.0)
    val currentAreaSqFt: StateFlow<Double> = _currentAreaSqFt.asStateFlow()

    /**
     * Adds a new corner point to the measurement list.
     */
    fun addPoint(anchor: Anchor) {
        val currentList = _anchors.value.toMutableList()
        currentList.add(anchor)
        _anchors.value = currentList
        recalculateArea()
    }

    /**
     * Clears all measurements.
     */
    fun reset() {
        _anchors.value.forEach { it.detach() }
        _anchors.value = emptyList()
        _currentAreaSqFt.value = 0.0
    }

    private fun recalculateArea() {
        val poses = _anchors.value.map { it.pose }
        val areaSqM = GeometryUtils.calculateArea(poses)
        _currentAreaSqFt.value = GeometryUtils.sqMetersToSqFt(areaSqM)
    }
}
