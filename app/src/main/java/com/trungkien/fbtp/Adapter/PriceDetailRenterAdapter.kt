package com.trungkien.fbtp.Adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.trungkien.fbtp.databinding.ItemPriceBoardRenterBinding
import com.trungkien.fbtp.model.TimeSlot
import java.text.NumberFormat
import java.util.Locale

class PriceDetailRenterAdapter(
    private val context: Context,
    private var timeSlots: List<TimeSlot>,
    private val onItemClick: (TimeSlot) -> Unit
) : RecyclerView.Adapter<PriceDetailRenterAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemPriceBoardRenterBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(timeSlot: TimeSlot) {
            binding.txtPeriod.text = timeSlot.period
            binding.txtCourtSize.text = timeSlot.courtSize
            binding.txtSession.text = timeSlot.session
            binding.txtPrice.text = formatPrice(timeSlot.price)
            binding.root.setOnClickListener { onItemClick(timeSlot) }
        }

        private fun formatPrice(price: Double): String {
            val formatter = NumberFormat.getCurrencyInstance(Locale("vi", "VN"))
            return formatter.format(price)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPriceBoardRenterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(timeSlots[position])
    }

    override fun getItemCount(): Int = timeSlots.size

    // Method to update data
    fun updateData(newTimeSlots: List<TimeSlot>) {
        timeSlots = newTimeSlots
        notifyDataSetChanged()
    }
}