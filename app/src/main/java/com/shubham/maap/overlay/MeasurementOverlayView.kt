package com.shubham.maap.overlay

import android.content.Context
import android.graphics.*
import android.opengl.Matrix
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt
import com.google.ar.core.Anchor
import com.google.ar.core.Camera
import com.google.ar.core.Pose
import com.shubham.maap.measurement.GeometryUtils
import java.util.*

/**
 * 2D Canvas overlay for drawing AR measurement lines, markers, and labels.
 */
class MeasurementOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 6f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    private val markerPaint = Paint().apply {
        color = "#2563EB".toColorInt() // Electric Blue
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val labelPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }

    private val polygonFillPaint = Paint().apply {
        color = Color.argb(60, 37, 99, 235) // Translucent Electric Blue
        style = Paint.Style.FILL
    }

    private var anchors: List<Anchor> = emptyList()
    private var arCamera: Camera? = null
    private var reticlePose: Pose? = null
    private var pointCloudArray: FloatArray? = null
    private val polygonPath = Path()

    /**
     * Updates the rendering data. Should be called on UI thread.
     */
    fun updateData(
        anchors: List<Anchor>,
        camera: Camera?,
        reticlePose: Pose? = null,
        pointCloud: FloatArray? = null
    ) {
        this.anchors = anchors
        this.arCamera = camera
        this.reticlePose = reticlePose
        this.pointCloudArray = pointCloud
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val camera = arCamera ?: return

        // 1. Draw Point Cloud
        drawScanningDots(canvas, camera)

        if (anchors.isEmpty()) return

        // 2. Project world anchors to screen coordinates
        val screenPoints = anchors.mapNotNull { projectPoint(it.pose, camera) }
        
        // 3. Draw Polygon Fill (if 3+ points)
        if (screenPoints.size >= 3) {
            polygonPath.reset()
            polygonPath.moveTo(screenPoints[0].x, screenPoints[0].y)
            for (i in 1 until screenPoints.size) {
                polygonPath.lineTo(screenPoints[i].x, screenPoints[i].y)
            }
            polygonPath.close()
            canvas.drawPath(polygonPath, polygonFillPaint)
        }

        // 4. Draw Lines and Markers
        if (screenPoints.isNotEmpty()) {
            for (i in screenPoints.indices) {
                val current = screenPoints[i]
                
                // Marker
                canvas.drawCircle(current.x, current.y, 12f, markerPaint)

                // Line to next point
                if (i < (screenPoints.size - 1)) {
                    val next = screenPoints[i + 1]
                    canvas.drawLine(current.x, current.y, next.x, next.y, linePaint)
                    
                    // Distance label for the segment
                    val dist = GeometryUtils.calculateDistance(anchors[i].pose, anchors[i + 1].pose)
                    drawDistanceLabel(canvas, current, next, dist)
                }
            }
            
            // 5. Live line from last point to current reticle position
            reticlePose?.let { rPose ->
                val lastPoint = screenPoints.last()
                val screenReticle = projectPoint(rPose, camera)
                if (screenReticle != null) {
                    canvas.drawLine(lastPoint.x, lastPoint.y, screenReticle.x, screenReticle.y, linePaint)
                    val dist = GeometryUtils.calculateDistance(anchors.last().pose, rPose)
                    drawDistanceLabel(canvas, lastPoint, screenReticle, dist)
                }
            }
        }
    }

    private fun drawScanningDots(canvas: Canvas, camera: Camera) {
        val points = pointCloudArray ?: return
        val paint = Paint().apply { color = Color.WHITE; alpha = 100 }
        // Step 16 to reduce dots on low-end devices
        for (i in 0 until points.size step 16) {
            val pose = Pose(floatArrayOf(points[i], points[i + 1], points[i + 2]), floatArrayOf(0f, 0f, 0f, 1f))
            projectPoint(pose, camera)?.let {
                canvas.drawPoint(it.x, it.y, paint)
            }
        }
    }

    private fun drawDistanceLabel(canvas: Canvas, p1: PointF, p2: PointF, distMeters: Float) {
        val midX = (p1.x + p2.x) / 2
        val midY = (p1.y + p2.y) / 2
        val distFeet = GeometryUtils.metersToFeet(distMeters)
        val text = String.format(Locale.getDefault(), "%.2f ft", distFeet)
        canvas.drawText(text, midX, midY - 20, labelPaint)
    }

    private fun projectPoint(pose: Pose, camera: Camera): PointF? {
        val viewMatrix = FloatArray(16)
        val projMatrix = FloatArray(16)
        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projMatrix, 0, 0.1f, 100f)

        val worldCoords = floatArrayOf(pose.tx(), pose.ty(), pose.tz(), 1f)
        val viewCoords = FloatArray(4)
        Matrix.multiplyMV(viewCoords, 0, viewMatrix, 0, worldCoords, 0)
        
        val clipCoords = FloatArray(4)
        Matrix.multiplyMV(clipCoords, 0, projMatrix, 0, viewCoords, 0)

        if (clipCoords[3] <= 0f) return null

        val x = (clipCoords[0] / clipCoords[3] + 1) * width / 2f
        val y = (1 - clipCoords[1] / clipCoords[3]) * height / 2f

        return PointF(x, y)
    }
}
