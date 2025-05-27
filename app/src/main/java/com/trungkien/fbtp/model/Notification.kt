package com.trungkien.fbtp.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

enum class NotificationType {
    BOOKING, MATCH_FINDING, SYSTEM
}

data class Notification(
    @PropertyName("notificationId") val notificationId: String = "",
    @PropertyName("userID") val userID: String = "",
    @PropertyName("title") val title: String = "",
    @PropertyName("message") val message: String = "",
    @PropertyName("createAt") @ServerTimestamp val createAt: Timestamp = Timestamp.now(),
    @PropertyName("isRead") val isRead: Boolean = false,
    @PropertyName("type") val type: NotificationType = NotificationType.SYSTEM,
    @PropertyName("relatedID") val relatedID: String = ""
) {
    constructor() : this("", "", "", "", Timestamp.now(), false, NotificationType.SYSTEM, "")

    fun isValid(): Boolean = userID.isNotEmpty() && relatedID.isNotEmpty() && type != NotificationType.SYSTEM
}