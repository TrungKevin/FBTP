package com.trungkien.fbtp.model

data class MatchFinding(
    val matchID: String = "",
    val userID: String = "",
    val sportType: String = "",
    val muonChoiDate: String = "", // "dd/MM/yyyy HH:mm"
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val skillLevel: String = "medium", // "beginner", "medium", "advanced"
    val status: String = "finding", // "finding", "matched", "cancelled"
    val createdAt: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", "", "", null, null, "medium", "finding")
}