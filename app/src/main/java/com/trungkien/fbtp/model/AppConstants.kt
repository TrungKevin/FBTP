package com.trungkien.fbtp.model

object AppConstants {
    object BookingStatus {
        const val PENDING = "pending"
        const val CONFIRMED = "confirmed"
        const val CANCELLED = "cancelled"
        const val COMPLETED = "completed"
    }

    object SportTypes {
        const val FOOTBALL = "football"
        const val TENNIS = "tennis"
        const val BADMINTON = "badminton"
        const val BASKETBALL = "basketball"
    }

    object PaymentMethods {
        const val CASH = "cash"
        const val MOMO = "momo"
        const val BANKING = "banking"
    }
}