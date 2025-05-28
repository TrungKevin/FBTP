package com.trungkien.fbtp.Adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.trungkien.fbtp.R
import com.trungkien.fbtp.databinding.ItemNotificationBinding
import com.trungkien.fbtp.owner.activity.NotificationItem

class NotificationAdapter(
    private var notifications: MutableList<NotificationItem>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    private val TAG = "NotificationAdapter"

    inner class NotificationViewHolder(val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications.getOrNull(position) ?: run {
            Log.w(TAG, "Null notification at position: $position")
            return
        }
        with(holder.binding) {
            tvUsername.text = "Người đặt: ${notification.username.takeIf { it.isNotBlank() } ?: "Không xác định"}"
            tvPhone.text = "Số điện thoại: ${notification.phone.takeIf { it.isNotBlank() } ?: "Không có"}"
            tvCourtSize.text = "Loại sân: ${notification.courtSize.takeIf { it.isNotBlank() } ?: "Không xác định"}"
            tvBookingDay.text = "Ngày: ${notification.bookingDay.takeIf { it.isNotBlank() } ?: "Không xác định"}"
            tvBookingTime.text = "Giờ: ${notification.bookingTime.takeIf { it.isNotBlank() } ?: "Không xác định"}"
            tvBookingStatus.text = when (notification.status) {
                "confirmed" -> "Đã xác nhận"
                "pending" -> "Đang chờ"
                "cancelled" -> "Đã hủy"
                "completed" -> "Đã hoàn thành"
                else -> "Không xác định"
            }
            tvBookingStatus.visibility = View.VISIBLE
            tvBookingStatus.background = ContextCompat.getDrawable(
                holder.itemView.context,
                when (notification.status) {
                    "confirmed" -> R.drawable.status_background
                    "pending" -> R.drawable.status_empty_box
                    "cancelled" -> R.drawable.status_booked_box
                    else -> R.drawable.status_booked_box
                }
            )
            root.setOnClickListener {
                if (notification.notificationId.isNotEmpty()) {
                    Log.d(TAG, "Clicked booking: ${notification.notificationId}")
                    onItemClick(notification.notificationId)
                } else {
                    Log.w(TAG, "Empty notificationId for notification at position: $position")
                }
            }
        }
    }

    override fun onViewRecycled(holder: NotificationViewHolder) {
        super.onViewRecycled(holder)
        holder.binding.root.setOnClickListener(null)
        Log.d(TAG, "Recycled view holder for position: ${holder.adapterPosition}")
    }

    override fun getItemCount(): Int = notifications.size

    fun updateData(newNotifications: List<NotificationItem>) {
        val diffCallback = NotificationDiffCallback(notifications, newNotifications)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        notifications.clear()
        notifications.addAll(newNotifications)
        diffResult.dispatchUpdatesTo(this)
        Log.d(TAG, "Updated notifications: ${newNotifications.size} items")
    }
}

class NotificationDiffCallback(
    private val oldList: List<NotificationItem>,
    private val newList: List<NotificationItem>
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldList.size
    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].notificationId == newList[newItemPosition].notificationId
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}