package com.trungkien.fbtp.owner.activity

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import java.text.SimpleDateFormat
import java.util.Locale

data class NotificationItem(
    val username: String,
    val phone: String,
    val courtSize: String,
    val bookingDay: String,
    val bookingTime: String,
    val notificationId: String
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
            markNotificationAsRead(notificationId)
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
                    }
                    return@launch
                }

                // Fetch notifications with pagination
                val notificationsQuery = db.collection("notifications")
                    .whereEqualTo("userID", currentUser.uid)
                    .whereEqualTo("type", com.trungkien.fbtp.model.NotificationType.BOOKING)
                    .orderBy("createdAt", Query.Direction.DESCENDING) // Sắp xếp theo thời gian
                    .limit(10) // Giới hạn 10 thông báo
                val notificationsSnapshot = notificationsQuery.get().await()
                val notificationItems = mutableListOf<NotificationItem>()

                for (doc in notificationsSnapshot.documents) {
                    val notification = doc.toObject(com.trungkien.fbtp.model.Notification::class.java) ?: continue
                    val relatedId = notification.relatedID
                    val bookingSnapshot = db.collection("bookings").document(relatedId).get().await()
                    val booking = bookingSnapshot.toObject(Booking::class.java) ?: continue
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
                            notificationId = notification.notificationId
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    notificationAdapter.updateData(notificationItems)
                    if (notificationItems.isEmpty()) {
                        Toast.makeText(this@NotificationActivity, "Không có thông báo nào", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading notifications: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@NotificationActivity, "Lỗi khi tải thông báo: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                }
            }
        }
    }

    private fun markNotificationAsRead(notificationId: String) {
        lifecycleScope.launch {
            try {
                db.collection("notifications")
                    .document(notificationId)
                    .update("isRead", true)
                    .await()
                Log.d(TAG, "Marked notification $notificationId as read")
                loadNotifications()
            } catch (e: Exception) {
                Log.e(TAG, "Error marking notification as read: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@NotificationActivity, "Lỗi khi đánh dấu thông báo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressContainer.visibility = if (show) View.VISIBLE else View.GONE
    }
}