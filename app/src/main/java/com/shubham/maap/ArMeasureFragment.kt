package com.shubham.maap

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.ar.core.Frame
import com.shubham.maap.arcore.ArCoreManager
import com.shubham.maap.databinding.FragmentArMeasureBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.coroutines.resume

/**
 * Fragment responsible for AR-based room measurement.
 * Follows MVVM and uses a custom GL+Canvas renderer for performance.
 */
class ArMeasureFragment : Fragment(), GLSurfaceView.Renderer {

    private var _binding: FragmentArMeasureBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MeasureViewModel by viewModels()
    private lateinit var arCoreManager: ArCoreManager
    private val backgroundRenderer = BackgroundRenderer()
    
    private var hasSetTexture = false
    private var pendingBitmap: Bitmap? = null
    
    @Volatile
    private var addPointRequested = false

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) resumeAR() else {
                Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentArMeasureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arCoreManager = ArCoreManager.getInstance(requireContext())

        setupGlView()
        setupUI()
        observeViewModel()
    }

    private fun setupGlView() {
        binding.surfaceView.apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setRenderer(this@ArMeasureFragment)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    private fun setupUI() {
        binding.fabAddPoint.setOnClickListener {
            addPointRequested = true
        }

        binding.btnReset.apply {
            setText(R.string.btn_cancel)
            setOnClickListener {
                findNavController().popBackStack()
            }
        }

        binding.btnSaveAr.setOnClickListener {
            startSaveWorkflow()
        }

        binding.btnEncloseArea.setOnClickListener {
            startSaveWorkflow()
        }

        binding.btnUndo.setOnClickListener {
            viewModel.removeLastPoint()
        }

        // Preview Actions
        binding.btnPreviewRetake.setOnClickListener {
            binding.layoutSavePreview.isVisible = false
            pendingBitmap = null
        }

        binding.btnPreviewSave.setOnClickListener {
            finalizeSave()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.currentAreaSqFt.collectLatest { area ->
                _binding?.let { b ->
                    b.tvLiveArea.text = String.format(Locale.getDefault(), "%.2f sq ft", area)
                    updateInstructions()
                }
            }
        }
    }

    private fun updateInstructions() {
        val b = _binding ?: return
        val count = viewModel.anchors.value.size
        
        b.btnEncloseArea.isVisible = count >= 3
        b.btnUndo.isVisible = count > 0

        if (count > 0) {
            b.tvInstruction.text = when (count) {
                1 -> "Add next point for distance"
                else -> "Add more corners for area or Save"
            }
        }
    }

    private fun startSaveWorkflow() {
        val anchors = viewModel.anchors.value
        if (anchors.size < 2) {
            Toast.makeText(context, "Need at least 2 points", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            val bitmap = captureScreenshot()
            if (bitmap != null) {
                pendingBitmap = bitmap
                binding.ivSavePreview.setImageBitmap(bitmap)
                
                // Show area in preview
                val area = if (anchors.size >= 3) viewModel.currentAreaSqFt.value else 0.0
                binding.tvPreviewArea.text = String.format(Locale.getDefault(), "%.2f sq ft", area)
                binding.tvPreviewArea.isVisible = anchors.size >= 3

                binding.layoutSavePreview.isVisible = true
            } else {
                Toast.makeText(context, "Failed to capture preview", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun finalizeSave() {
        val bitmap = pendingBitmap ?: return
        val anchors = viewModel.anchors.value
        
        lifecycleScope.launch {
            val imagePath = saveBitmapToInternalStorage(bitmap)
            saveImageToGallery(bitmap)
            
            val db = AppDatabase.getDatabase(requireContext())
            val isPolygon = anchors.size >= 3
            val shape = if (isPolygon) "AR Polygon" else "AR Distance"
            
            val dimensions = if (isPolygon) {
                "${anchors.size} corners"
            } else {
                val dist = com.shubham.maap.measurement.GeometryUtils.calculateDistance(anchors[0].pose, anchors[1].pose)
                String.format(Locale.getDefault(), "%.2f ft", com.shubham.maap.measurement.GeometryUtils.metersToFeet(dist))
            }

            val area = if (isPolygon) viewModel.currentAreaSqFt.value else 0.0

            db.measurementDao().insert(
                Measurement(
                    roomName = "AR ${System.currentTimeMillis() % 10000}",
                    shape = shape,
                    dimensions = dimensions,
                    area = area,
                    imagePath = imagePath
                ),
            )
            Toast.makeText(context, "Saved successfully", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    private suspend fun saveBitmapToInternalStorage(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        val file = File(requireContext().filesDir, "meas_${System.currentTimeMillis()}.jpg")
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun saveImageToGallery(bitmap: Bitmap) {
        val context = requireContext()
        val filename = "MAAP_${System.currentTimeMillis()}.jpg"
        var fos: OutputStream? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver?.also { resolver ->
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/MAAP")
                }
                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                fos = imageUri?.let { resolver.openOutputStream(it) }
            }
        } else {
            val imagesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM).toString() + File.separator + "MAAP"
            val file = File(imagesDir)
            if (!file.exists()) file.mkdir()
            val image = File(imagesDir, filename)
            fos = FileOutputStream(image)
        }

        fos?.use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
            activity?.runOnUiThread {
                Toast.makeText(context, "Saved to Gallery", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun captureScreenshot(): Bitmap? = suspendCancellableCoroutine { continuation ->
        val view = binding.surfaceView
        val bitmap = createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        
        val handlerThread = HandlerThread("PixelCopy")
        handlerThread.start()
        
        PixelCopy.request(view, bitmap, { result ->
            if (result == PixelCopy.SUCCESS) {
                val combinedBitmap = createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(combinedBitmap)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                binding.measurementOverlay.draw(canvas)
                continuation.resume(combinedBitmap)
            } else {
                continuation.resume(null)
            }
            handlerThread.quitSafely()
        }, Handler(handlerThread.looper))
    }

    private fun resumeAR() {
        if (arCoreManager.session == null) {
            if (arCoreManager.setupSession() == null) {
                Toast.makeText(context, "ARCore not supported on this device", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
                return
            }
        }
        if (!arCoreManager.resume()) {
            Toast.makeText(context, "Failed to start camera", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        } else {
            binding.surfaceView.onResume()
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            resumeAR()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onPause() {
        super.onPause()
        binding.surfaceView.onPause()
        arCoreManager.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        arCoreManager.close()
        _binding = null
    }

    // --- GL Renderer implementation (Running on GL Thread) ---

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1.0f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        arCoreManager.session?.setDisplayGeometry(0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val session = arCoreManager.session ?: return

        if (!hasSetTexture) {
            backgroundRenderer.createOnGlThread()
            session.setCameraTextureName(backgroundRenderer.textureId)
            hasSetTexture = true
        }

        try {
            val frame = session.update()
            backgroundRenderer.draw(frame)

            // Handle business logic on GL thread
            processARFrame(frame)
            
        } catch (_: Exception) {
            // Lifecycle issue or tracking loss
        }
    }

    private fun processARFrame(frame: Frame) {
        val binding = _binding ?: return
        
        // 1. Plane detection check
        val hit = arCoreManager.hitTestCenter(frame, binding.surfaceView.width, binding.surfaceView.height)
        val isPlaneFound = hit != null
        
        // Live Instruction & Dramatic Effect Logic
        if (viewModel.anchors.value.isEmpty()) {
            activity?.runOnUiThread {
                val currentText = _binding?.tvInstruction?.text.toString()
                val newText = if (isPlaneFound) "Add corners" else "Detecting floor..."
                
                if (currentText != newText) {
                    _binding?.tvInstruction?.text = newText
                    if (isPlaneFound) {
                        _binding?.tvInstruction?.clearAnimation()
                        _binding?.viewScanningVignette?.animate()?.alpha(0f)?.setDuration(500)?.start()
                    } else {
                        val animation = android.view.animation.AlphaAnimation(0.4f, 1.0f).apply {
                            duration = 800
                            repeatMode = android.view.animation.Animation.REVERSE
                            repeatCount = android.view.animation.Animation.INFINITE
                        }
                        _binding?.tvInstruction?.startAnimation(animation)
                        
                        _binding?.viewScanningVignette?.alpha = 1.0f
                        _binding?.viewScanningVignette?.visibility = View.VISIBLE
                        val vignetteAnim = android.view.animation.AlphaAnimation(0.3f, 1.0f).apply {
                            duration = 1200
                            repeatMode = android.view.animation.Animation.REVERSE
                            repeatCount = android.view.animation.Animation.INFINITE
                        }
                        _binding?.viewScanningVignette?.startAnimation(vignetteAnim)
                    }
                }
            }
        }
        
        // 2. Handle point addition request
        if (addPointRequested && isPlaneFound) {
            viewModel.addPoint(hit.createAnchor())
            addPointRequested = false
            activity?.runOnUiThread {
                _binding?.viewScanningVignette?.clearAnimation()
                _binding?.viewScanningVignette?.visibility = View.GONE
                _binding?.surfaceView?.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            }
        } else if (addPointRequested) {
            addPointRequested = false // Reset even if failed
        }

        // 3. Extract data for 2D overlay (Safe copy)
        val pointCloudArray = try {
            frame.acquirePointCloud().use { pc ->
                val points = pc.points
                val array = FloatArray(points.remaining())
                points[array]
                array
            }
        } catch (_: Exception) { null }

        val camera = frame.camera
        val reticlePose = hit?.hitPose

        // 4. Update UI overlay
        activity?.runOnUiThread {
            _binding?.let { b ->
                b.ivReticle.alpha = if (isPlaneFound) 1.0f else 0.4f
                b.measurementOverlay.update(
                    viewModel.anchors.value,
                    camera,
                    reticlePose,
                    pointCloudArray,
                )
            }
        }
    }
}
