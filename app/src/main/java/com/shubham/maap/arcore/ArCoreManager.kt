package com.shubham.maap.arcore

import android.content.Context
import android.util.Log
import com.google.ar.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles ARCore session logic, tracking, and hit testing.
 * Singleton to allow early initialization (warm-up).
 */
class ArCoreManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    var session: Session? = null
        private set

    fun setupSession(): Session? {
        if (session != null) return session
        
        try {
            session = Session(appContext)
            val config = Config(session).apply {
                // Focus on Horizontal for faster floor detection as requested, 
                // but keep VERTICAL if we want both. 
                // Using HORIZONTAL only is usually faster for floor.
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                focusMode = Config.FocusMode.AUTO
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                // Ensure Depth is used if available for faster detection
                if (session?.isDepthModeSupported(Config.DepthMode.AUTOMATIC) == true) {
                    depthMode = Config.DepthMode.AUTOMATIC
                }
                lightEstimationMode = Config.LightEstimationMode.DISABLED
            }
            session?.configure(config)
            Log.d("ArCoreManager", "ARCore Session initialized with Depth Support")
        } catch (e: Exception) {
            Log.e("ArCoreManager", "Failed to create ARCore session", e)
            return null
        }
        return session
    }

    /**
     * Pre-initialize the session in background to reduce lag when opening AR view.
     */
    fun warmUp() {
        CoroutineScope(Dispatchers.IO).launch {
            if (session == null) {
                setupSession()
            }
        }
    }

    fun resume(): Boolean {
        return try {
            session?.resume()
            true
        } catch (e: Exception) {
            Log.e("ArCoreManager", "Failed to resume ARCore session", e)
            false
        }
    }

    fun pause() {
        try {
            session?.pause()
        } catch (e: Exception) {
            Log.e("ArCoreManager", "Failed to pause ARCore session", e)
        }
    }

    fun close() {
        session?.close()
        session = null
    }

    fun hitTestCenter(frame: Frame, width: Int, height: Int): HitResult? {
        if (width <= 0 || height <= 0) return null
        
        return try {
            val hits = frame.hitTest(width / 2f, height / 2f)
            hits.firstOrNull { hit ->
                val trackable = hit.trackable
                (trackable is Plane) && trackable.isPoseInPolygon(hit.hitPose)
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ArCoreManager? = null

        fun getInstance(context: Context): ArCoreManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ArCoreManager(context).also { INSTANCE = it }
            }
        }
    }
}
