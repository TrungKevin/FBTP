package com.trungkien.fbtp.model

import com.google.firebase.firestore.PropertyName

/**
 * Represents a specific court within a sports facility.
 * Links to SportFacility via coSoID, User via ownerID, and TimeFrame via timeFrameID.
 */
data class Court(
    @PropertyName("courtID") val courtID: String = "",
    @PropertyName("coSoID") val coSoID: String = "",
    @PropertyName("ownerID") val ownerID: String = "",
    @PropertyName("timeFrameID") val timeFrameID: String = "",
    @PropertyName("courtName") val courtName: String = "",
    @PropertyName("sportType") val sportType: String = "", // e.g., "Football", "Badminton"
    @PropertyName("status") val status: String = "available",
    @PropertyName("size") val size: String = "", // e.g., "5vs5"
    @PropertyName("period") val period: String = "", // e.g., "08:00-09:00"
    @PropertyName("pricePerHour") val pricePerHour: Double = 0.0,
    @PropertyName("pricingID") val pricingID: String? = null, // Thêm trường pricingID
    @PropertyName("session") val session: String? = null // e.g., "Sáng", "Chiều", "Tối"
) {
    // Default constructor for Firestore
    constructor() : this("", "", "", "", "", "", "available", "", "", 0.0, null,null)
}