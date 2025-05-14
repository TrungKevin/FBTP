// SportFacilityAdapter.kt
package com.trungkien.fbtp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.trungkien.fbtp.R
import com.trungkien.fbtp.databinding.ItemSanBinding
import com.trungkien.fbtp.model.SportFacility

class SportFacilityAdapter(
    private val facilities: List<SportFacility>,
    private val onItemClick: (SportFacility) -> Unit
) : RecyclerView.Adapter<SportFacilityAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemSanBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(facility: SportFacility) {
            binding.apply {
                txtTenSan.text = facility.name
                txtDiaChi.text = facility.diaChi
                txtGioItem.text = "Giờ mở cửa: ${facility.openingHours}"
                txtSdtItem.text = facility.phoneContact

                // Load image with error handling
                if (facility.images.isNotEmpty()) {
                    Glide.with(imgSan.context)
                        .load(facility.images.first())
                        .placeholder(R.drawable.sanbong)
                        .error(R.drawable.sanbong)
                        .into(imgSan)
                } else {
                    imgSan.setImageResource(R.drawable.sanbong)
                }

                imgLogo.setImageResource(R.drawable.ic_club_logo)
                btnBook.setOnClickListener { onItemClick(facility) }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSanBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(facilities[position])
    }

    override fun getItemCount(): Int = facilities.size
}

