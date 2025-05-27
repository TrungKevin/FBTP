package com.trungkien.fbtp.Adapter

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.trungkien.fbtp.R
import com.trungkien.fbtp.model.Court
import com.trungkien.fbtp.model.SportFacility
import com.trungkien.fbtp.owner.activity.NotificationActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class UploadAdapter(
    private var facilities: MutableList<SportFacility>,
    private val userRole: String,
    private val onItemClick: (SportFacility) -> Unit,
    private val onBookClick: (SportFacility) -> Unit,
    private val onNotificationClick: (SportFacility) -> Unit
) : RecyclerView.Adapter<UploadAdapter.ViewHolder>() {

    private val firestore = FirebaseFirestore.getInstance()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val courtSizeCache = mutableMapOf<String, String>()
    private val TAG = "UploadAdapter"



    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgSan: ImageView = itemView.findViewById(R.id.img_san)
        val txtTenSan: TextView = itemView.findViewById(R.id.txt_tenSan)
        val txtDiaChi: TextView = itemView.findViewById(R.id.txt_diaChi)
        val txtGioItem: TextView = itemView.findViewById(R.id.txt_gio_item)
        val txtSdtItem: TextView = itemView.findViewById(R.id.txt_sdt_item)
        val txtVsItem: TextView = itemView.findViewById(R.id.txt_vs_item)
        val imgLogo: ImageView = itemView.findViewById(R.id.img_logo)
        val btnBook: MaterialButton = itemView.findViewById(R.id.btn_book)
        val btnNotification: ImageButton = itemView.findViewById(R.id.btn_notification)
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

        // Kiểm soát visibility dựa trên userRole
        when (userRole) {
            "renter" -> {
                holder.btnBook.visibility = View.VISIBLE
                holder.btnNotification.visibility = View.GONE
            }
            "owner" -> {
                holder.btnBook.visibility = View.GONE
                holder.btnNotification.visibility = View.VISIBLE
            }
            else -> {
                holder.btnBook.visibility = View.GONE
                holder.btnNotification.visibility = View.GONE
            }
        }

        Log.d(TAG, "Binding facility: coSoID=${facility.coSoID}, name=${facility.name}, ownerID=${facility.ownerID}")

        val cacheKey = facility.coSoID
        if (courtSizeCache.containsKey(cacheKey)) {
            holder.txtVsItem.text = courtSizeCache[cacheKey]
        } else {
            coroutineScope.launch {
                try {
                    Log.d(TAG, "Querying courts for coSoID: ${facility.coSoID}")
                    val snapshot = withContext(Dispatchers.IO) {
                        firestore.collection("courts")
                            .whereEqualTo("coSoID", facility.coSoID)
                            .get()
                            .await()
                    }

                    val courtSizes = snapshot.documents
                        .mapNotNull { it.toObject(Court::class.java)?.size }
                        .filter { it.isNotBlank() }
                        .distinct()

                    val courtSizeText = if (courtSizes.isNotEmpty()) {
                        courtSizes.joinToString(", ")
                    } else {
                        "N/A"
                    }

                    Log.d(TAG, "Found court sizes: $courtSizeText for coSoID: ${facility.coSoID}")
                    courtSizeCache[cacheKey] = courtSizeText
                    holder.txtVsItem.text = courtSizeText
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching court size for coSoID: ${facility.coSoID}, error: ${e.message}", e)
                    courtSizeCache[cacheKey] = "N/A"
                    holder.txtVsItem.text = "N/A"
                }
            }
        }

        val ownerID = facility.ownerID
        if (ownerID.isEmpty()) {
            Log.w(TAG, "Empty ownerID for facility: ${facility.coSoID}")
            loadProfileImage(holder.imgLogo, null)
        } else {
            Log.d(TAG, "Querying profile image for ownerID: $ownerID")
            coroutineScope.launch {
                try {
                    val snapshot = withContext(Dispatchers.IO) {
                        firestore.collection("users")
                            .document(ownerID)
                            .get()
                            .await()
                    }

                    val profileImageUrl = snapshot.getString("profileImageUrl")
                    Log.d(TAG, "Found profileImageUrl: $profileImageUrl for ownerID: $ownerID")
                    loadProfileImage(holder.imgLogo, profileImageUrl)
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching profile image for ownerID: $ownerID, error: ${e.message}", e)
                    loadProfileImage(holder.imgLogo, null)
                }
            }
        }

        if (facility.images.isNotEmpty()) {
            if (facility.images[0].startsWith("http")) {
                Glide.with(holder.itemView.context)
                    .load(facility.images[0])
                    .thumbnail(0.25f)
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
        holder.btnBook.setOnClickListener { onBookClick(facility) }
        holder.btnNotification.setOnClickListener{onNotificationClick(facility)}

    }

    private fun loadProfileImage(imageView: ImageView, profileImageUrl: String?) {
        Log.d(TAG, "Loading profile image: $profileImageUrl")
        when {
            profileImageUrl.isNullOrEmpty() || profileImageUrl == "N/A" -> {
                Log.d(TAG, "Setting default logo due to null or empty URL")
                imageView.setImageResource(R.drawable.ic_club_logo)
            }
            profileImageUrl.startsWith("http") -> {
                Log.d(TAG, "Using Glide to load HTTP URL: $profileImageUrl")
                Glide.with(imageView.context)
                    .load(profileImageUrl)
                    .thumbnail(0.25f)
                    .placeholder(R.drawable.ic_club_logo)
                    .error(R.drawable.ic_club_logo)
                    .into(imageView)
            }
            else -> {
                Log.d(TAG, "Decoding Base64 profile image")
                try {
                    val decodedBytes = android.util.Base64.decode(profileImageUrl, android.util.Base64.DEFAULT)
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    imageView.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    Log.e(TAG, "Error decoding Base64 profile image: ${e.message}", e)
                    imageView.setImageResource(R.drawable.ic_club_logo)
                }
            }
        }
    }



    override fun getItemCount(): Int = facilities.size

    fun updateData(newFacilities: List<SportFacility>) {
        facilities.clear()
        facilities.addAll(newFacilities)
        preloadData(newFacilities)
        notifyDataSetChanged()
    }

    private fun preloadData(facilities: List<SportFacility>) {
        coroutineScope.launch {
            facilities.forEach { facility ->
                val cacheKey = facility.coSoID
                if (!courtSizeCache.containsKey(cacheKey)) {
                    try {
                        Log.d(TAG, "Preloading courts for coSoID: ${facility.coSoID}")
                        val snapshot = withContext(Dispatchers.IO) {
                            firestore.collection("courts")
                                .whereEqualTo("coSoID", facility.coSoID)
                                .get()
                                .await()
                        }

                        val courtSizes = snapshot.documents
                            .mapNotNull { it.toObject(Court::class.java)?.size }
                            .filter { it.isNotBlank() }
                            .distinct()

                        val courtSizeText = if (courtSizes.isNotEmpty()) {
                            courtSizes.joinToString(", ")
                        } else {
                            "N/A"
                        }

                        courtSizeCache[cacheKey] = courtSizeText
                        Log.d(TAG, "Preloaded court sizes: $courtSizeText for coSoID: ${facility.coSoID}")
                    } catch (e: Exception) {
                        courtSizeCache[cacheKey] = "N/A"
                        Log.e(TAG, "Error preloading court size for coSoID: ${facility.coSoID}, error: ${e.message}", e)
                    }
                }
            }
        }
    }
}