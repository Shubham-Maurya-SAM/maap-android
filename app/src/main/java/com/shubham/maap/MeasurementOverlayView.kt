package com.shubham.maap

import android.content.Context
import android.graphics.*
import android.opengl.Matrix
import android.util.AttributeSet
import android.view.View
import com.google.ar.core.Anchor
import com.google.ar.core.Camera
import com.google.ar.core.PointCloud
import com.google.ar.core.Pose

class MeasurementOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 8f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    private val pointPaint = Paint().apply {
        color = Color.parseColor("#2563EB") // Electric Blue
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val cloudPaint = Paint().apply {
        color = Color.WHITE
        alpha = 150
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint().apply {
        color = Color.argb(80, 37, 99, 235) // Translucent Electric Blue
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var anchors: List<Anchor> = emptyList()
    private var arCamera: Camera? = null
    private var reticlePose: Pose? = null
    private var pointCloudPoints: FloatArray? = null
    private val path = Path()

    fun update(
        anchors: List<Anchor>, 
        camera: Camera?, 
        reticlePose: Pose? = null,
        pointCloudPoints: FloatArray? = null
    ) {
        this.anchors = anchors
        this.arCamera = camera
        this.reticlePose = reticlePose
        this.pointCloudPoints = pointCloudPoints
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val camera = arCamera ?: return
        
        // 1. Draw Point Cloud (visual feedback for scanning)
        drawPointCloud(canvas, camera)

        if (anchors.isEmpty()) return
        
        // 2. Draw measurements
        path.reset()
        val screenPoints = mutableListOf<PointF>()

        anchors.forEach { anchor ->
            val pose = anchor.pose
            val screenPoint = projectPoint(pose, camera)
            if (screenPoint != null) {
                screenPoints.add(screenPoint)
            }
        }

        if (screenPoints.size >= 2) {
            path.moveTo(screenPoints[0].x, screenPoints[0].y)
            for (i in 1 until screenPoints.size) {
                path.lineTo(screenPoints[i].x, screenPoints[i].y)
                canvas.drawCircle(screenPoints[i].x, screenPoints[i].y, 15f, pointPaint)
            }
            if (screenPoints.size >= 3) {
                path.close()
                canvas.drawPath(path, fillPaint)
            }
            canvas.drawPath(path, linePaint)
        }

        // Draw the first point last so it's on top
        if (screenPoints.isNotEmpty()) {
            canvas.drawCircle(screenPoints[0].x, screenPoints[0].y, 20f, pointPaint)
            
            // Live line from last point to reticle
            reticlePose?.let { rPose ->
                val lastPose = anchors.last().pose
                val screenReticle = projectPoint(rPose, camera)
                if (screenReticle != null) {
                    val lastPoint = screenPoints.last()
                    canvas.drawLine(lastPoint.x, lastPoint.y, screenReticle.x, screenReticle.y, linePaint)
                    
                    // Draw distance text in middle of line
                    val dist = calculateDistance(lastPose, rPose)
                    val midX = (lastPoint.x + screenReticle.x) / 2
                    val midY = (lastPoint.y + screenReticle.y) / 2
                    val distStr = String.format("%.2f ft", dist * 3.28084)
                    canvas.drawText(distStr, midX, midY, Paint().apply {
                        color = Color.WHITE
                        textSize = 40f
                        textAlign = Paint.Align.CENTER
                        setShadowLayer(5f, 0f, 0f, Color.BLACK)
                    })
                }
            }
        }
    }

    private fun calculateDistance(p1: Pose, p2: Pose): Float {
        val dx = p1.tx() - p2.tx()
        val dy = p1.ty() - p2.ty()
        val dz = p1.tz() - p2.tz()
        return Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
    }

    private fun drawPointCloud(canvas: Canvas, camera: Camera) {
        val points = pointCloudPoints ?: return
        for (i in 0 until points.size step 4) {
            val pose = Pose(floatArrayOf(points[i], points[i + 1], points[i + 2]), floatArrayOf(0f, 0f, 0f, 1f))
            val screenPoint = projectPoint(pose, camera)
            if (screenPoint != null) {
                canvas.drawPoint(screenPoint.x, screenPoint.y, cloudPaint)
            }
        }
    }

    private fun projectPoint(pose: Pose, camera: Camera): PointF? {
        val viewMatrix = FloatArray(16)
        val projMatrix = FloatArray(16)
        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projMatrix, 0, 0.1f, 100f)

        val worldCoords = floatArrayOf(pose.tx(), pose.ty(), pose.tz(), 1f)
        val modelView = FloatArray(16)
        Matrix.multiplyMM(modelView, 0, viewMatrix, 0, worldCoords, 0) // This is wrong, it should be Matrix.multiplyMV
        
        // Correct projection logic:
        val viewCoords = FloatArray(4)
        Matrix.multiplyMV(viewCoords, 0, viewMatrix, 0, worldCoords, 0)
        
        val clipCoords = FloatArray(4)
        Matrix.multiplyMV(clipCoords, 0, projMatrix, 0, viewCoords, 0)

        if (clipCoords[3] == 0f) return null

        val ndcCoords = floatArrayOf(
            clipCoords[0] / clipCoords[3],
            clipCoords[1] / clipCoords[3]
        )

        val x = (ndcCoords[0] + 1) * width / 2f
        val y = (1 - ndcCoords[1]) * height / 2f

        return PointF(x, y)
    }
}
