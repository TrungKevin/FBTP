package com.trungkien.fbtp.model

import java.util.Date

data class Booking(
    val bookingID: String = "",
    val userID: String = "",
    val courtID: String = "",
    val facilityID: String = "", // Thêm để tiện truy vấn
    val bookingDate: String = "", // Format "dd/MM/yyyy"
    val startTime: String = "", // Format "HH:mm"
    val endTime: String = "",
    val status: String = "pending", // "pending", "confirmed", "cancelled", "completed"
    val createdAt: Long = System.currentTimeMillis(),
    val totalPrice: Double = 0.0,
    val notes: String = ""
) {
    constructor() : this("", "", "", "", "", "", "", "pending")
}