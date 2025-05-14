package com.trungkien.fbtp.Adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.trungkien.fbtp.R
import com.trungkien.fbtp.model.SportFacility

class UploadAdapter(
    private var facilities: MutableList<SportFacility>,
    private val onItemClick: (SportFacility) -> Unit
) : RecyclerView.Adapter<UploadAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgSan: ImageView = itemView.findViewById(R.id.img_san)
        val txtTenSan: TextView = itemView.findViewById(R.id.txt_tenSan)
        val txtDiaChi: TextView = itemView.findViewById(R.id.txt_diaChi)
        val txtGioItem: TextView = itemView.findViewById(R.id.txt_gio_item)
        val txtSdtItem: TextView = itemView.findViewById(R.id.txt_sdt_item)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_san, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val facility = facilities[position]
        holder.txtTenSan.text = facility.name
        holder.txtDiaChi.text = facility.diaChi
        holder.txtGioItem.text = facility.openingHours ?: "Chưa cài đặt"
        holder.txtSdtItem.text = facility.phoneContact

        if (facility.images.isNotEmpty()) {
            if (facility.images[0].startsWith("http")) {
                Glide.with(holder.itemView.context)
                    .load(facility.images[0])
                    .placeholder(R.drawable.sanbong)
                    .error(R.drawable.sanbong)
                    .into(holder.imgSan)
            } else {
                try {
                    val decodedBytes = android.util.Base64.decode(facility.images[0], android.util.Base64.DEFAULT)
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    holder.imgSan.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    holder.imgSan.setImageResource(R.drawable.sanbong)
                }
            }
        } else {
            holder.imgSan.setImageResource(R.drawable.sanbong)
        }

        holder.itemView.setOnClickListener { onItemClick(facility) }
    }

    override fun getItemCount(): Int = facilities.size

    fun updateData(newFacilities: List<SportFacility>) {
        facilities.clear() // Clear old data to prevent duplicates
        facilities.addAll(newFacilities)
        notifyDataSetChanged()
    }
}