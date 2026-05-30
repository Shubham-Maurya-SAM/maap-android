package com.shubham.maap

import android.view.LayoutInflater
import java.util.Locale
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shubham.maap.databinding.ItemMeasurementBinding

class MeasurementAdapter(
    private val onDeleteClick: (Measurement) -> Unit,
    private val onEditClick: (Measurement) -> Unit,
) : ListAdapter<Measurement, MeasurementAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMeasurementBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onDeleteClick, onEditClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemMeasurementBinding,
        private val onDeleteClick: (Measurement) -> Unit,
        private val onEditClick: (Measurement) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(measurement: Measurement) {
            binding.tvItemRoomName.text = measurement.roomName
            binding.tvItemDims.text = measurement.dimensions.ifBlank { measurement.shape }
            val areaText = String.format(Locale.getDefault(), "%.2f sq ft", measurement.area)
            binding.tvItemArea.text = areaText
            
            // Group information for TalkBack
            binding.measurementInfoContainer.contentDescription = 
                "${measurement.roomName}, ${binding.tvItemDims.text}, area $areaText"
            
            binding.ivEdit.setOnClickListener { onEditClick(measurement) }
            
            // Set dynamic delete description
            binding.ivDelete.contentDescription = 
                binding.root.context.getString(R.string.desc_delete_measurement, measurement.roomName)

            binding.ivDelete.setOnClickListener { onDeleteClick(measurement) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Measurement>() {
        override fun areItemsTheSame(oldItem: Measurement, newItem: Measurement): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Measurement, newItem: Measurement): Boolean = oldItem == newItem
    }
}
