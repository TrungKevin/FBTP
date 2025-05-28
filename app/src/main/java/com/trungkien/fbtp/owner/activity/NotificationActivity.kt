package com.trungkien.fbtp.owner.activity

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.trungkien.fbtp.Adapter.NotificationAdapter
import com.trungkien.fbtp.R
import com.trungkien.fbtp.databinding.ActivityNotificationBinding
import com.trungkien.fbtp.model.Booking
import com.trungkien.fbtp.model.Court
import com.trungkien.fbtp.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class NotificationItem(
    val username: String,
    val phone: String,
    val courtSize: String,
    val bookingDay: String,
    val bookingTime: String,
    val notificationId: String, // Maps to bookingID
    val status: String = "Không xác định"
)

class NotificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var notificationAdapter: NotificationAdapter
    private val TAG = "NotificationActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadNotifications()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private fun setupRecyclerView() {
        notificationAdapter = NotificationAdapter(mutableListOf()) { notificationId ->
            markBookingAsRead(notificationId)
        }
        binding.rcvNotifications.apply {
            adapter = notificationAdapter
            layoutManager = LinearLayoutManager(this@NotificationActivity)
        }
    }

    private fun loadNotifications() {
        val currentUser = auth.currentUser ?: run {
            Toast.makeText(this, "Vui lòng đăng nhập lại", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Không có kết nối mạng", Toast.LENGTH_LONG).show()
            return
        }

        val coSoID = intent.getStringExtra("coSoID")
        lifecycleScope.launch {
            try {
                showLoading(true)
                // Fetch courts owned by the user
                val courtsQuery = db.collection("courts")
                    .whereEqualTo("ownerID", currentUser.uid)
                    .apply { if (coSoID != null) whereEqualTo("coSoID", coSoID) }
                val courtsSnapshot = courtsQuery.get().await()
                val courtIds = courtsSnapshot.documents.mapNotNull { it.getString("courtID") }
                val coSoIds = courtsSnapshot.documents.mapNotNull { it.getString("coSoID") }.distinct()

                if (courtIds.isEmpty() || coSoIds.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@NotificationActivity, "Không tìm thấy sân nào của bạn", Toast.LENGTH_SHORT).show()
                        showLoading(false)
                    }
                    return@launch
                }

                // Real-time listener for bookings
                db.collection("bookings")
                    .whereIn("courtID", courtIds.take(10)) // Limit to 10 due to Firestore's IN query limit
                    .whereIn("facilityID", coSoIds.take(10)) // Limit to 10 due to Firestore's IN query limit
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(10)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e(TAG, "Error listening for bookings: ${error.message}", error)
                            runOnUiThread {
                                if (error.message?.contains("The query requires an index") == true) {
                                    Toast.makeText(
                                        this@NotificationActivity,
                                        "Yêu cầu tạo chỉ mục Firestore. Vui lòng kiểm tra Firebase Console.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        this@NotificationActivity,
                                        "Lỗi khi tải danh sách đặt sân: ${error.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                showLoading(false)
                            }
                            return@addSnapshotListener
                        }

                        val notificationItems = mutableListOf<NotificationItem>()
                        lifecycleScope.launch {
                            for (doc in snapshot?.documents ?: emptyList()) {
                                val booking = doc.toObject(Booking::class.java) ?: continue
                                if (booking.courtID !in courtIds || booking.facilityID !in coSoIds) continue

                                val userSnapshot = db.collection("users").document(booking.userID).get().await()
                                val user = userSnapshot.toObject(User::class.java)
                                val courtSnapshot = db.collection("courts").document(booking.courtID).get().await()
                                val court = courtSnapshot.toObject(Court::class.java)

                                notificationItems.add(
                                    NotificationItem(
                                        username = user?.username ?: "Không xác định",
                                        phone = user?.phone ?: "Không có số",
                                        courtSize = court?.size ?: "Không xác định",
                                        bookingDay = booking.bookingDate,
                                        bookingTime = booking.period,
                                        notificationId = booking.bookingID, // Use bookingID as notificationId
                                        status = booking.status
                                    )
                                )
                            }

                            withContext(Dispatchers.Main) {
                                notificationAdapter.updateData(notificationItems)
                                if (notificationItems.isEmpty()) {
                                    Toast.makeText(this@NotificationActivity, "Không có đặt sân nào", Toast.LENGTH_SHORT).show()
                                }
                                showLoading(false)
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading bookings: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@NotificationActivity, "Lỗi khi tải danh sách đặt sân: ${e.message}", Toast.LENGTH_LONG).show()
                    showLoading(false)
                }
            }
        }
    }

    private fun markBookingAsRead(notificationId: String) {
        // Optional: If you want to mark a booking as read, update a field in the booking document
        // For now, we'll assume this is not needed unless you have a specific "isRead" field in Booking
        lifecycleScope.launch {
            try {
                // Example: Add an isRead field to the Booking model if needed
                db.collection("bookings")
                    .document(notificationId)
                    .update("isRead", true)
                    .await()
                Log.d(TAG, "Marked booking $notificationId as read")
            } catch (e: Exception) {
                Log.e(TAG, "Error marking booking as read: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@NotificationActivity, "Lỗi khi đánh dấu đã đọc: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressContainer.visibility = if (show) View.VISIBLE else View.GONE
    }
}