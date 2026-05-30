package com.shubham.maap.arcore

import android.content.Context
import com.google.ar.core.*
import com.google.ar.core.exceptions.CameraNotAvailableException

/**
 * Handles ARCore session logic, tracking, and hit testing.
 */
class ArCoreManager(private val context: Context) {

    var session: Session? = null
        private set

    fun setupSession(): Session? {
        try {
            session = Session(context)
            val config = Config(session).apply {
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                focusMode = Config.FocusMode.AUTO
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                // Enabled for low-end device optimization if supported
                lightEstimationMode = Config.LightEstimationMode.DISABLED
            }
            session?.configure(config)
        } catch (_: Exception) {
            return null
        }
        return session
    }

    fun resume() {
        try {
            session?.resume()
        } catch (e: CameraNotAvailableException) {
            e.printStackTrace()
        }
    }

    fun pause() {
        session?.pause()
    }

    fun close() {
        session?.close()
        session = null
    }

    /**
     * Performs a hit test in the center of the screen to find a horizontal floor plane.
     */
    fun hitTestCenter(frame: Frame, width: Int, height: Int): HitResult? {
        val hits = frame.hitTest(width / 2f, height / 2f)
        return hits.firstOrNull { hit ->
            val trackable = hit.trackable
            (trackable is Plane) && trackable.isPoseInPolygon(hit.hitPose) && (trackable.type == Plane.Type.HORIZONTAL_UPWARD_FACING)
        }
    }
}
