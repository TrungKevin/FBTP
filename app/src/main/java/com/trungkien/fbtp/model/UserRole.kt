package com.trungkien.fbtp.model

enum class UserRole(val roleName: String, val permissions: List<String>) {
    RENTER("Người thuê", listOf("book_field", "view_field")),
    OWNER("Chủ sân", listOf("manage_field", "add_field")),
    ADMIN("Quản trị viên", listOf("manage_users", "manage_all"))
}
