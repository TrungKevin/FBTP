package com.trungkien.fbtp.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

enum class NotificationType {
    BOOKING, MATCH_FINDING, SYSTEM
}

data class Notification(
    @PropertyName("notificationId") val notificationId: String = "",
    @PropertyName("userID") val userID: String = "", // ID of the owner to receive the notification
    @PropertyName("title") val title: String = "",
    @PropertyName("message") val message: String = "",
    @PropertyName("createAt") @ServerTimestamp val createAt: Timestamp = Timestamp.now(),
    @PropertyName("isRead") val isRead: Boolean = false,
    @PropertyName("type") val type: NotificationType = NotificationType.BOOKING,
    @PropertyName("relatedID") val relatedID: String = "", // Booking ID
    @PropertyName("courtID") val courtID: String = "", // Court ID for reference
    @PropertyName("facilityID") val facilityID: String = "" // Facility ID for filtering
) {
    constructor() : this("", "", "", "", Timestamp.now(), false, NotificationType.BOOKING, "", "", "")

    fun isValid(): Boolean = userID.isNotEmpty() && relatedID.isNotEmpty() && type == NotificationType.BOOKING && courtID.isNotEmpty() && facilityID.isNotEmpty()
}