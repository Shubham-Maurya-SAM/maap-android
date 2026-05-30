package com.shubham.maap

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import java.util.Locale
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.shubham.maap.databinding.FragmentMeasureBinding
import kotlinx.coroutines.launch

class MeasureFragment : Fragment() {

    private var _binding: FragmentMeasureBinding? = null
    private val binding get() = _binding!!

    private var currentArea: Double = 0.0
    private var currentShape: String = "Rectangle"

    private val liveTextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            calculateLiveArea()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMeasureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateInputsForShape("Rectangle")

        binding.toggleShape.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentShape = when (checkedId) {
                    binding.btnRect.id -> "Rectangle"
                    binding.btnLshape.id -> "L-Shape"
                    binding.btnTshape.id -> "T-Shape"
                    binding.btnTriangle.id -> "Triangle"
                    binding.btnCustom.id -> "Custom"
                    else -> "Rectangle"
                }
                updateInputsForShape(currentShape)
            }
        }

        binding.etNumSides.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val num = s.toString().toIntOrNull() ?: 0
                    if (num in 3..20) {
                        generateCustomInputs(num)
                    }
                }
            },
        )

        binding.btnCancel.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnCalculate.setOnClickListener {
            calculateLiveArea()
            if (currentArea > 0) {
                binding.cvResult.isVisible = true
                binding.btnSave.isVisible = true
                binding.btnCalculate.isVisible = false
            } else {
                Toast.makeText(requireContext(), R.string.error_invalid_number, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSave.setOnClickListener {
            saveMeasurement()
        }
    }

    private fun updateInputsForShape(shape: String) {
        binding.tilNumSides.isVisible = (shape == "Custom")
        binding.inputsContainer.removeAllViews()
        binding.cvResult.isVisible = false
        binding.btnSave.isVisible = false
        binding.btnCalculate.isVisible = true

        when (shape) {
            "Rectangle" -> {
                addInputField(getString(R.string.length_hint), "length")
                addInputField(getString(R.string.width_hint), "width")
            }
            "L-Shape" -> {
                addInputField("Length 1 (फुट)", "l1")
                addInputField("Width 1 (फुट)", "w1")
                addInputField("Length 2 (फुट)", "l2")
                addInputField("Width 2 (फुट)", "w2")
            }
            "T-Shape" -> {
                for (i in 1..4) addInputField("Dimension $i (फुट)", "d$i")
            }
            "Triangle" -> {
                addInputField("Base (आधार)", "base")
                addInputField("Height (ऊंचाई)", "height")
            }
            "Custom" -> {
                val currentSides = binding.etNumSides.text.toString().toIntOrNull() ?: 0
                if (currentSides >= 3) generateCustomInputs(currentSides)
            }
        }
    }

    private fun generateCustomInputs(count: Int) {
        binding.inputsContainer.removeAllViews()
        for (i in 1..count) {
            addInputField(getString(R.string.side_label, i), "side_$i")
        }
    }

    private fun addInputField(hint: String, tag: String) {
        val til = TextInputLayout(requireContext(), null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16.toPx())
            }
            this.hint = hint
        }

        val et = TextInputEditText(til.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            this.tag = tag
            addTextChangedListener(liveTextWatcher)
        }

        til.addView(et)
        binding.inputsContainer.addView(til)
    }

    private fun calculateLiveArea() {
        val inputs = mutableMapOf<String, Double>()
        binding.inputsContainer.children.forEach { view ->
            if (view is TextInputLayout) {
                val et = view.editText as? TextInputEditText
                val value = et?.text.toString().toDoubleOrNull() ?: 0.0
                val tag = et?.tag?.toString() ?: ""
                inputs[tag] = value
            }
        }

        currentArea = when (currentShape) {
            "Rectangle" -> (inputs["length"] ?: 0.0) * (inputs["width"] ?: 0.0)
            "L-Shape" -> {
                val area1 = (inputs["l1"] ?: 0.0) * (inputs["w1"] ?: 0.0)
                val area2 = (inputs["l2"] ?: 0.0) * (inputs["w2"] ?: 0.0)
                area1 + area2
            }
            "Triangle" -> 0.5 * (inputs["base"] ?: 0.0) * (inputs["height"] ?: 0.0)
            "T-Shape", "Custom" -> {
                // Simplified: sum of all inputs for custom/T (could be more complex, but using sum for placeholder)
                inputs.values.sum()
            }
            else -> 0.0
        }

        binding.tvResultArea.text = String.format(Locale.getDefault(), "%.2f sq ft", currentArea)
        if (currentArea > 0) {
            binding.cvResult.isVisible = true
        }
    }

    private fun saveMeasurement() {
        val roomName = binding.etRoomName.text.toString()
        if (roomName.isBlank()) {
            binding.etRoomName.error = getString(R.string.error_enter_name)
            return
        }

        val measurement = Measurement(
            roomName = roomName,
            shape = currentShape,
            area = currentArea
        )

        lifecycleScope.launch {
            AppDatabase.getDatabase(requireContext()).measurementDao().insert(measurement)
            Toast.makeText(requireContext(), "Saved!", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    private fun Int.toPx(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
