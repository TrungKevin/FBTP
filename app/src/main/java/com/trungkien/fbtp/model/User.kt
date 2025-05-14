package com.trungkien.fbtp.model

import com.google.firebase.firestore.PropertyName

/**
 * Represents a user in the system (renter, owner, or admin).
 * Links to SportFacility via ownerID and UserRole via roleID.
 */
data class User(
    @PropertyName("userID") val userID: String = "",
    @PropertyName("username") val username: String = "",
    @PropertyName("email") val email: String = "",
    @PropertyName("phone") val phone: String = "",
    @PropertyName("roleID") val roleID: String = "", // Maps to UserRole.roleName
    @PropertyName("profileImageUrl") val profileImageUrl: String = "",
    @PropertyName("fcmToken") val fcmToken: String = ""
) {
    // Default constructor for Firestore
    constructor() : this("", "", "", "", "", "", "")
}