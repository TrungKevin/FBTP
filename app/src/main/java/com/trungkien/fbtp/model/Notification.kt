package com.trungkien.fbtp.model

enum class NotificationType {
    BOOKING, MATCH_FINDING, SYSTEM
}
data class Notification(
    val notificationID: String = "",
    val userID: String = "",
    val title: String = "",
    val message: String = "",
    val createAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val type: NotificationType = NotificationType.SYSTEM,
    val relatedID: String = "" // ID của đối tượng liên quan
) {
    constructor() : this("", "", "", "", 0L, false, NotificationType.SYSTEM, "")
}

