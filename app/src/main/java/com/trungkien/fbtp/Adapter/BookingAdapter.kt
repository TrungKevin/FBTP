package com.trungkien.fbtp.Adapter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.trungkien.fbtp.R
import com.trungkien.fbtp.databinding.ItemOrderBinding
import com.trungkien.fbtp.model.Booking
import com.trungkien.fbtp.model.Court
import com.trungkien.fbtp.model.SportFacility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class BookingAdapter(
    private var bookings: List<Booking>,
    private val coroutineScope: CoroutineScope,
    private val onChipStatusClick: (Booking) -> Unit = {} // Added click listener for chipStatus
) : RecyclerView.Adapter<BookingAdapter.BookingViewHolder>() {

    inner class BookingViewHolder(val binding: ItemOrderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val binding = ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        val booking = bookings[position]
        with(holder.binding) {
            txtOrderGio.text = booking.period
            txtOrderNgay.text = booking.bookingDate
            chipStatus.text = when (booking.status) {
                "confirmed" -> "Đã đặt"
                "pending" -> "Đang chờ"
                "cancelled" -> "Đã hủy"
                "completed" -> "Hoàn thành"
                else -> booking.status
            }
            chipStatus.chipBackgroundColor = when (booking.status) {
                "confirmed" -> holder.itemView.context.getColorStateList(R.color.colorPrimary)
                "pending" -> holder.itemView.context.getColorStateList(R.color.colorBadge)
                "cancelled" -> holder.itemView.context.getColorStateList(R.color.color_red)
                "completed" -> holder.itemView.context.getColorStateList(R.color.colorPrimaryDark)
                else -> holder.itemView.context.getColorStateList(R.color.colorPrimary)
            }

            // Enable click only for "pending" or "confirmed" bookings
            chipStatus.isClickable = booking.status in listOf("pending", "confirmed")
            chipStatus.setOnClickListener {
                if (booking.status in listOf("pending", "confirmed")) {
                    onChipStatusClick(booking)
                }
            }

            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val db = FirebaseFirestore.getInstance()
                    val facilityDoc = db.collection("sport_facilities")
                        .document(booking.facilityID)
                        .get()
                        .await()
                    val facility = facilityDoc.toObject(SportFacility::class.java)
                    withContext(Dispatchers.Main) {
                        txtOrderTen.text = facility?.name ?: "Unknown Facility"
                        txtAddress.text = facility?.diaChi ?: "Unknown Address"
                        facility?.images?.firstOrNull()?.let { image ->
                            if (image.startsWith("http")) {
                                Glide.with(imgSoccerField.context)
                                    .load(image)
                                    .placeholder(R.drawable.fbtp)
                                    .error(R.drawable.fbtp)
                                    .into(imgSoccerField)
                            } else {
                                try {
                                    val decodedBytes = Base64.decode(image, Base64.DEFAULT)
                                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                                    imgSoccerField.setImageBitmap(bitmap)
                                } catch (e: Exception) {
                                    imgSoccerField.setImageResource(R.drawable.fbtp)
                                }
                            }
                        } ?: imgSoccerField.setImageResource(R.drawable.fbtp)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        txtOrderTen.text = "Error Loading Facility"
                        txtAddress.text = "Error Loading Address"
                        imgSoccerField.setImageResource(R.drawable.fbtp)
                    }
                }
            }

            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val db = FirebaseFirestore.getInstance()
                    val courtDoc = db.collection("courts")
                        .whereEqualTo("courtID", booking.courtID)
                        .whereEqualTo("coSoID", booking.facilityID)
                        .get()
                        .await()
                    val court = courtDoc.documents.firstOrNull()?.toObject(Court::class.java)
                    withContext(Dispatchers.Main) {
                        txtOrderVs.text = court?.size?.takeIf { it.isNotEmpty() }?.let { "Loại sân: $it" } ?: "Unknown Size"
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        txtOrderVs.text = "Error Loading Court"
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = bookings.size

    fun updateData(newBookings: List<Booking>) {
        bookings = newBookings
        notifyDataSetChanged()
    }
}