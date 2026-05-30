package com.shubham.maap

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.ar.core.Frame
import com.shubham.maap.arcore.ArCoreManager
import com.shubham.maap.databinding.FragmentArMeasureBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

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
        arCoreManager = ArCoreManager(requireContext())

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

        binding.btnReset.setOnClickListener {
            viewModel.reset()
        }

        binding.btnSaveAr.setOnClickListener {
            saveAndExit()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.currentAreaSqFt.collectLatest { area ->
                binding.tvLiveArea.text = String.format(Locale.getDefault(), "%.2f sq ft", area)
                updateInstructions()
            }
        }
    }

    private fun updateInstructions() {
        val count = viewModel.anchors.value.size
        binding.tvInstruction.text = when (count) {
            0 -> "Find floor and add first corner"
            1 -> "Add next corner"
            2 -> "Add one more corner to see area"
            else -> "Add more corners or Save"
        }
    }

    private fun saveAndExit() {
        if (viewModel.anchors.value.size < 3) {
            Toast.makeText(context, "Need at least 3 corners", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val count = viewModel.anchors.value.size
            db.measurementDao().insert(
                Measurement(
                    roomName = "AR ${System.currentTimeMillis() % 10000}",
                    shape = "AR Polygon",
                    dimensions = "$count corners",
                    area = viewModel.currentAreaSqFt.value,
                ),
            )
            findNavController().popBackStack()
        }
    }

    private fun resumeAR() {
        if (arCoreManager.session == null) {
            arCoreManager.setupSession()
        }
        arCoreManager.resume()
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
        // 1. Plane detection check
        val hit = arCoreManager.hitTestCenter(frame, binding.surfaceView.width, binding.surfaceView.height)
        val isPlaneFound = hit != null
        
        // 2. Handle point addition request
        if (addPointRequested && isPlaneFound) {
            viewModel.addPoint(hit.createAnchor())
            addPointRequested = false
            activity?.runOnUiThread {
                binding.surfaceView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
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
            binding.ivReticle.alpha = if (isPlaneFound) 1.0f else 0.4f
            binding.measurementOverlay.updateData(
                viewModel.anchors.value,
                camera,
                reticlePose,
                pointCloudArray
            )
        }
    }
}
