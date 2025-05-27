package com.trungkien.fbtp.model

import com.google.firebase.firestore.PropertyName

data class Booking(
    val bookingID: String = "",
    val userID: String = "",
    val courtID: String = "",
    @PropertyName("facilityID") val facilityID: String = "", // Thay coSoID trong hệ thống
    val bookingDate: String = "", // Format "dd/MM/yyyy"
    val period: String = "", // Format "HH:mm-HH:mm", e.g., "08:00-09:00"
    @PropertyName("status") val status: String = "pending", // "pending", "confirmed", "cancelled", "completed"
    @PropertyName("booked") val booked: Boolean = false, // Đồng bộ với timeframes
    val createdAt: Long = System.currentTimeMillis(),
    val totalPrice: Double = 0.0,
    val notes: String = ""
) {
    constructor() : this(
        bookingID = "",
        userID = "",
        courtID = "",
        facilityID = "",
        bookingDate = "",
        period = "",
        status = "pending",
        booked = false,
        createdAt = System.currentTimeMillis(),
        totalPrice = 0.0,
        notes = ""
    )
}