package com.trungkien.fbtp.Adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TableRow
import androidx.recyclerview.widget.RecyclerView
import com.trungkien.fbtp.R
import com.trungkien.fbtp.databinding.ItemPriceBoardRenterBinding
import com.trungkien.fbtp.model.TimeSlot

class PriceBoardOfDetailAdapter(
    private val context: Context,
    private val timeSlots: List<TimeSlot>,
    private val onItemClick: (Int, TimeSlot) -> Unit,
    private var isEditing: Boolean = false
) : RecyclerView.Adapter<PriceBoardOfDetailAdapter.ViewHolder>() {

    private var isLoading = false

    inner class ViewHolder(val binding: ItemPriceBoardRenterBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(timeSlot: TimeSlot) {
            binding.txtSession.text = timeSlot.session
            binding.txtCourtSize.text = timeSlot.courtSize
            binding.txtPeriod.text = timeSlot.period
            binding.txtPrice.text = "${String.format("%,d", timeSlot.price.toLong())} VNĐ"

            // Xử lý click vào hàng dữ liệu
            binding.root.findViewById<TableRow>(R.id.data_row)?.setOnClickListener {
                if (!isLoading && isEditing && adapterPosition != RecyclerView.NO_POSITION) {
                    onItemClick(adapterPosition, timeSlot)
                }
            }
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

    fun setLoading(loading: Boolean) {
        isLoading = loading
        notifyDataSetChanged()
    }

    fun setEditing(editing: Boolean) {
        isEditing = editing
        notifyDataSetChanged()
    }
}