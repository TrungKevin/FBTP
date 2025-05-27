package com.trungkien.fbtp.Adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.trungkien.fbtp.R
import com.trungkien.fbtp.model.TimeSlot

class KhungGioAdapter(
    var timeSlots: List<TimeSlot>,
    private val onItemClick: (Int, Boolean, TimeSlot) -> Unit
) : RecyclerView.Adapter<KhungGioAdapter.ViewHolder>() {

    private var selectedPositions: List<Int> = emptyList() // Track multiple selected positions

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvPeriod: TextView = itemView.findViewById(R.id.tv_period)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_khung_gio, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val timeSlot = timeSlots[position]
        holder.tvPeriod.text = timeSlot.period

        val isBooked = timeSlot.isBooked ?: false
        val isSelected = selectedPositions.contains(position) && !isBooked

        // Set background and text color based on state
        when {
            isBooked -> {
                holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.colorBooked))
                holder.tvPeriod.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.textSecondary))
                holder.itemView.isEnabled = false // Disable booked slots
            }
            isSelected -> {
                holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.colorSelected))
                holder.tvPeriod.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.textPrimary))
                holder.itemView.isEnabled = true
            }
            else -> {
                holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.colorAvailable))
                holder.tvPeriod.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.textPrimary))
                holder.itemView.isEnabled = true
            }
        }

        holder.itemView.setOnClickListener {
            val adapterPosition = holder.adapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                val currentIsBooked = timeSlots[adapterPosition].isBooked ?: false
                onItemClick(adapterPosition, currentIsBooked, timeSlots[adapterPosition])
            }
        }
    }

    override fun getItemCount(): Int = timeSlots.size

    fun updateData(newTimeSlots: List<TimeSlot>) {
        timeSlots = newTimeSlots
        notifyDataSetChanged()
    }

    fun updateTimeSlot(position: Int, updatedTimeSlot: TimeSlot) {
        if (position in 0 until timeSlots.size) {
            timeSlots = timeSlots.toMutableList().apply {
                set(position, updatedTimeSlot)
            }
            notifyItemChanged(position)
        }
    }

    fun setSelectedPositions(positions: List<Int>) {
        val oldPositions = selectedPositions
        selectedPositions = positions
        oldPositions.forEach { if (it !in positions) notifyItemChanged(it) }
        positions.forEach { if (it !in oldPositions) notifyItemChanged(it) }
    }

    fun getTimeSlot(position: Int): TimeSlot? {
        return if (position in 0 until timeSlots.size) timeSlots[position] else null
    }

    fun getData(): List<TimeSlot> = timeSlots
}