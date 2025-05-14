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
    public var timeSlots: List<TimeSlot>,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<KhungGioAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvPeriod: TextView = itemView.findViewById(R.id.tv_period)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_khung_gio, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val timeSlot = timeSlots[position]
        holder.tvPeriod.text = timeSlot.period

        // Update appearance based on booking status
        if (timeSlot.isBooked == true) {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.colorBooked)) // Define color_booked in res/values/colors.xml
            holder.tvPeriod.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.textSecondary))
            holder.itemView.isEnabled = false // Disable clicking on booked slots
        } else {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.colorAvailable)) // Define color_available
            holder.tvPeriod.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.textPrimary))
            holder.itemView.isEnabled = true
        }

        holder.itemView.setOnClickListener {
            if (timeSlot.isBooked != true) {
                onItemClick(position)
            }
        }
    }

    override fun getItemCount(): Int = timeSlots.size

    fun updateData(newTimeSlots: List<TimeSlot>) {
        timeSlots = newTimeSlots
        notifyDataSetChanged()
    }
}