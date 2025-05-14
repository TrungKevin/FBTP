package com.trungkien.fbtp.model

import android.os.Message
import com.google.firebase.firestore.FirebaseFirestore

fun saveNotifiToFireSt (userID: String, message: String) {
    val db = FirebaseFirestore.getInstance()
    val notification = Notification(
        notificationID = db.collection("notifications").document().id,
            userID = userID,
            message = message,
            isRead = false
    )

    db.collection("notifications")
        .document(notification.notificationID)
        .set(notification)
        .addOnSuccessListener {
            println("Lưu thông báo thành công!")
        }
        .addOnFailureListener { e ->
            println("Lỗi khi lưu thông báo: $e")
        }
}