package com.trungkien.fbtp.model

import com.google.firebase.firestore.FirebaseFirestore

fun saveNotifiToFireSt(
    userID: String,
    title: String,
    message: String,
    type: NotificationType,
    relatedID: String
) {
    val db = FirebaseFirestore.getInstance()
    val notificationID = db.collection("notifications").document().id
    val notification = Notification(
        notificationId = notificationID,
        userID = userID,
        title = title,
        message = message,
        isRead = false,
        type = type,
        relatedID = relatedID
    )

    db.collection("notifications")
        .document(notificationID)
        .set(notification)
        .addOnSuccessListener {
            println("Lưu thông báo thành công!")
        }
        .addOnFailureListener { e ->
            println("Lỗi khi lưu thông báo: $e")
        }
}
