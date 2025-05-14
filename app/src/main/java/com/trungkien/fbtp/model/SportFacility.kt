package com.trungkien.fbtp.model

import com.google.firebase.firestore.PropertyName
import android.os.Parcelable
import com.google.firebase.firestore.IgnoreExtraProperties
import kotlinx.parcelize.Parcelize

/**
 * Represents a sports facility (e.g., football field, badminton court).
 * Links to Court via coSoID and User (owner) via ownerID.
 */
@IgnoreExtraProperties
@Parcelize
data class SportFacility(
    @PropertyName("coSoID") val coSoID: String = "",
    @PropertyName("name") val name: String = "",
    @PropertyName("diaChi") val diaChi: String = "",
    @PropertyName("ownerID") val ownerID: String = "",
    @PropertyName("phoneContact") val phoneContact: String = "",
    @PropertyName("email") val email: String = "",
    @PropertyName("description") val description: String = "",
    @PropertyName("pricePerHour") val pricePerHour: Double = 0.0,
    @PropertyName("Hour") val Hour: String = "",
    @PropertyName("Buoi") val Buoi: List<String> = emptyList(), // am (buổi sáng) / pm (Buổi tối)
    @PropertyName("sanLoai") val sanLoai: List<String> = emptyList(),
    @PropertyName("openingHours") val openingHours: String = "08:00-22:00",
    @PropertyName("images") val images: List<String> = emptyList()
) : Parcelable {
    // Default constructor for Firestore
    constructor() : this("", "", "", "", "", "", "", 0.0, "", emptyList(), emptyList(), "08:00-22:00", emptyList())
}