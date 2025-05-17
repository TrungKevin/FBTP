package com.trungkien.fbtp.Adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.trungkien.fbtp.R
import com.trungkien.fbtp.model.Court
import com.trungkien.fbtp.model.SportFacility
import com.trungkien.fbtp.model.TimeSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import android.util.Log

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
        val txtVsItem: TextView = itemView.findViewById(R.id.txt_vs_item)
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

        // Load court size from TimeSlot using coSoID and courtID, fallback to Court
        holder.txtVsItem.text = "..vs.."
        GlobalScope.launch(Dispatchers.IO) {
            var courtSize: String? = null
            try {
                val db = FirebaseFirestore.getInstance()
                // Step 1: Get courtID from courts collection
                var courtID: String? = null
                repeat(2) { attempt ->
                    withTimeoutOrNull(10000) {
                        try {
                            val courtSnapshot = db.collection("courts")
                                .whereEqualTo("coSoID", facility.coSoID)
                                .limit(1)
                                .get()
                                .await()
                            val court = courtSnapshot.documents.firstOrNull()?.toObject(Court::class.java)
                            courtID = court?.courtID
                            courtSize = court?.size?.takeIf { it.isNotEmpty() } // Store for fallback
                            Log.d("UploadAdapter", "Attempt $attempt: CourtID: $courtID, Court size: ${court?.size} for coSoID: ${facility.coSoID}")
                            if (courtID != null) return@withTimeoutOrNull
                        } catch (e: Exception) {
                            Log.w("UploadAdapter", "Attempt $attempt: courts query failed: ${e.message}")
                        }
                    }
                    if (courtID != null) return@repeat
                }

                // Step 2: Try time_slots collection with coSoID and courtID
                if (courtID != null) {
                    repeat(2) { attempt ->
                        withTimeoutOrNull(10000) {
                            try {
                                val timeSlotSnapshot = db.collection("time_slots")
                                    .whereEqualTo("coSoID", facility.coSoID)
                                    .whereEqualTo("courtID", courtID)
                                    .limit(1)
                                    .get()
                                    .await()
                                val timeSlot = timeSlotSnapshot.documents.firstOrNull()?.toObject(TimeSlot::class.java)
                                courtSize = timeSlot?.courtSize?.takeIf { it.isNotEmpty() }
                                Log.d("UploadAdapter", "Attempt $attempt: TimeSlot courtSize: ${timeSlot?.courtSize} for coSoID: ${facility.coSoID}, courtID: $courtID")
                                if (courtSize != null) return@withTimeoutOrNull
                            } catch (e: Exception) {
                                Log.w("UploadAdapter", "Attempt $attempt: time_slots query failed: ${e.message}")
                            }
                        }
                        if (courtSize != null) return@repeat
                    }
                }

                // Step 3: Try time_frames/{timeFrameID}/time_slots subcollection
                if (courtSize == null && courtID != null) {
                    repeat(2) { attempt ->
                        withTimeoutOrNull(10000) {
                            try {
                                val timeFrameSnapshot = db.collection("time_frames")
                                    .whereEqualTo("coSoID", facility.coSoID)
                                    .limit(1)
                                    .get()
                                    .await()
                                val timeFrameID = timeFrameSnapshot.documents.firstOrNull()?.id
                                Log.d("UploadAdapter", "Attempt $attempt: TimeFrame ID: $timeFrameID for coSoID: ${facility.coSoID}")
                                if (timeFrameID != null) {
                                    val subSnapshot = db.collection("time_frames")
                                        .document(timeFrameID)
                                        .collection("time_slots")
                                        .whereEqualTo("courtID", courtID)
                                        .limit(1)
                                        .get()
                                        .await()
                                    val timeSlot = subSnapshot.documents.firstOrNull()?.toObject(TimeSlot::class.java)
                                    courtSize = timeSlot?.courtSize?.takeIf { it.isNotEmpty() }
                                    Log.d("UploadAdapter", "Attempt $attempt: Subcollection TimeSlot courtSize: ${timeSlot?.courtSize} for courtID: $courtID")
                                    if (courtSize != null) return@withTimeoutOrNull
                                }
                            } catch (e: Exception) {
                                Log.w("UploadAdapter", "Attempt $attempt: time_frames/time_slots query failed: ${e.message}")
                            }
                        }
                        if (courtSize != null) return@repeat
                    }
                }
            } catch (e: Exception) {
                Log.e("UploadAdapter", "Error fetching court size for coSoID: ${facility.coSoID}, ${e.message}", e)
            }

            withContext(Dispatchers.Main) {
                holder.txtVsItem.text = courtSize ?: "..vs.."
            }
        }

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