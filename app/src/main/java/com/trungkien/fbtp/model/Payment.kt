package com.trungkien.fbtp.model

data class Payment(
    val paymentID: String = "",
    val bookingID: String = "",
    val soTien: Double = 0.0,
    val payDay: String = "", // "dd/MM/yyyy HH:mm"
    val payMethod: String = "", // "cash", "momo", "banking"
    val transactionCode: String = "",
    val status: String = "pending" // "pending", "success", "failed"
) {
    constructor() : this("", "", 0.0, "", "", "", "pending")
}