package com.shubham.maap

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.shubham.maap.databinding.FragmentHomeBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Main dashboard screen with Tabs for measuring and history.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: MeasurementAdapter
    
    private var manualShape: String = "Rectangle"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTabs()
        setupManualMeasure()
        setupHistory()
        
        binding.cardArLaunch.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_arMeasureFragment)
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        binding.layoutTabMeasure.isVisible = true
                        binding.layoutTabHistory.isVisible = false
                    }
                    1 -> {
                        binding.layoutTabMeasure.isVisible = false
                        binding.layoutTabHistory.isVisible = true
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupManualMeasure() {
        updateManualInputs("Rectangle")
        
        binding.toggleShape.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                manualShape = if (checkedId == R.id.btn_rect) "Rectangle" else "L-Shape"
                updateManualInputs(manualShape)
            }
        }
        
        binding.btnCalculateManual.setOnClickListener {
            saveManualMeasurement()
        }
    }

    private fun updateManualInputs(shape: String) {
        binding.inputsContainer.removeAllViews()
        if (shape == "Rectangle") {
            addInputField(getString(R.string.length_hint), "length")
            addInputField(getString(R.string.width_hint), "width")
        } else {
            addInputField("Length 1 (ft)", "l1")
            addInputField("Width 1 (ft)", "w1")
            addInputField("Length 2 (ft)", "l2")
            addInputField("Width 2 (ft)", "w2")
        }
    }

    private fun addInputField(hint: String, tag: String) {
        val til = TextInputLayout(requireContext(), null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 12.toPx())
            }
            this.hint = hint
        }
        val et = TextInputEditText(til.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            this.tag = tag
        }
        til.addView(et)
        binding.inputsContainer.addView(til)
    }

    private fun saveManualMeasurement() {
        val name = binding.etRoomName.text.toString()
        if (name.isBlank()) {
            binding.etRoomName.error = "Enter name"
            return
        }

        val inputs = mutableMapOf<String, Double>()
        binding.inputsContainer.children.forEach { view ->
            if (view is TextInputLayout) {
                val et = view.editText as? TextInputEditText
                val value = et?.text.toString().toDoubleOrNull() ?: 0.0
                inputs[et?.tag?.toString() ?: ""] = value
            }
        }

        val dims: String
        val area = if (manualShape == "Rectangle") {
            val l = inputs["length"] ?: 0.0
            val w = inputs["width"] ?: 0.0
            dims = String.format(Locale.getDefault(), "%.1f x %.1f ft", l, w)
            l * w
        } else {
            val l1 = inputs["l1"] ?: 0.0
            val w1 = inputs["w1"] ?: 0.0
            val l2 = inputs["l2"] ?: 0.0
            val w2 = inputs["w2"] ?: 0.0
            dims = String.format(Locale.getDefault(), "L-Shape: (%.1f x %.1f) + (%.1f x %.1f)", l1, w1, l2, w2)
            (l1 * w1) + (l2 * w2)
        }

        if (area <= 0) {
            Toast.makeText(requireContext(), "Invalid area", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val dao = AppDatabase.getDatabase(requireContext()).measurementDao()
            dao.insert(Measurement(roomName = name, shape = manualShape, dimensions = dims, area = area))
            Toast.makeText(requireContext(), "Saved to History", Toast.LENGTH_SHORT).show()
            
            // Switch to history tab
            binding.tabLayout.getTabAt(1)?.select()
            
            // Clear inputs
            binding.etRoomName.text = null
            updateManualInputs(manualShape)
        }
    }

    private fun setupHistory() {
        val dao = AppDatabase.getDatabase(requireContext()).measurementDao()

        adapter = MeasurementAdapter(
            onDeleteClick = { measurement ->
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.delete_confirm)
                    .setPositiveButton(R.string.delete_yes) { _, _ ->
                        lifecycleScope.launch {
                            dao.delete(measurement)
                        }
                    }
                    .setNegativeButton(R.string.delete_no, null)
                    .show()
            },
            onEditClick = { measurement ->
                showEditDialog(measurement)
            }
        )

        binding.rvMeasurements.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMeasurements.adapter = adapter

        lifecycleScope.launch {
            dao.getAllMeasurements().collectLatest { measurements ->
                adapter.submitList(measurements)
                binding.emptyState.isVisible = measurements.isEmpty()
                binding.tvTotalRooms.text = measurements.size.toString()
            }
        }

        lifecycleScope.launch {
            dao.getTotalArea().collectLatest { totalArea ->
                binding.tvTotalArea.text = String.format(Locale.getDefault(), "%.1f sq ft", totalArea ?: 0.0)
            }
        }
    }

    private fun showEditDialog(measurement: Measurement) {
        val editText = TextInputEditText(requireContext()).apply {
            setText(measurement.roomName)
            hint = "New room name"
        }
        val container = LinearLayout(requireContext()).apply {
            setPadding(24.toPx(), 24.toPx(), 24.toPx(), 0)
            addView(editText, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Room Name")
            .setView(container)
            .setPositiveButton("Update") { _, _ ->
                val newName = editText.text.toString()
                if (newName.isNotBlank()) {
                    lifecycleScope.launch {
                        val dao = AppDatabase.getDatabase(requireContext()).measurementDao()
                        dao.insert(measurement.copy(roomName = newName))
                        Toast.makeText(requireContext(), "Updated", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun Int.toPx(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
