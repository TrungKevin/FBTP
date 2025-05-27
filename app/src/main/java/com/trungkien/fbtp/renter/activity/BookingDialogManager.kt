package com.trungkien.fbtp.renter.activity

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.trungkien.fbtp.R
import com.trungkien.fbtp.model.TimeSlot

class BookingDialogManager(private val context: Context) {

    fun showBookingConfirmationDialog(
        timeSlot: TimeSlot,
        position: Int,
        onConfirm: (Int, TimeSlot) -> Unit
    ) {
        // Show Toast based on booking status
        val toastMessage = if (timeSlot.isBooked == true) {
            "Khung giờ ${timeSlot.period} đã được đặt"
        } else {
            "Chọn khung giờ ${timeSlot.period}"
        }
        Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()

        // Create and show confirmation dialog
        val dialogMessage = if (timeSlot.isBooked == true) {
            "Khung giờ ${timeSlot.period} đã được đặt. Bạn có muốn tiếp tục đặt khung giờ này không?"
        } else {
            "Bạn muốn đặt khung giờ ${timeSlot.period}?"
        }
        AlertDialog.Builder(context)
            .setTitle("Xác nhận đặt lịch")
            .setMessage(dialogMessage)
            .setPositiveButton("Xác nhận") { _, _ ->
                // Update TimeSlot to booked status locally
                val updatedTimeSlot = timeSlot.copy(isBooked = true)
                onConfirm(position, updatedTimeSlot)
                Toast.makeText(
                    context,
                    "Xác nhận đặt khung giờ ${timeSlot.period}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Hủy") { dialog, _ ->
                dialog.dismiss()
                // No action needed on cancel
            }
            .setCancelable(true)
            .create()
            .show()
    }
}