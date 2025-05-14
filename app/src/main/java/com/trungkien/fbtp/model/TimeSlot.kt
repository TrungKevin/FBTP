package com.trungkien.fbtp.model

import android.os.Parcel
import android.os.Parcelable

data class TimeSlot(
    val scheduleID: String? = null,
    val price: Double = 0.0,
    val courtSize: String = "",
    val period: String = "",
    val session: String = "",
    val isTimeRange: Boolean = false,
    val courtID: String = "",
    val pricingID: String = "",
    val ownerID: String = "",
    val coSoID: String = "",
    val date: String = "",
    var isBooked: Boolean? = null
) : Parcelable {

    constructor(parcel: Parcel) : this(
        scheduleID = parcel.readString(),
        price = parcel.readDouble(),
        courtSize = parcel.readString() ?: "",
        period = parcel.readString() ?: "",
        session = parcel.readString() ?: "",
        isTimeRange = parcel.readByte() == 1.toByte(),
        courtID = parcel.readString() ?: "",
        pricingID = parcel.readString() ?: "",
        ownerID = parcel.readString() ?: "",
        coSoID = parcel.readString() ?: "",
        date = parcel.readString() ?: "",
        isBooked = parcel.readValue(Boolean::class.java.classLoader) as? Boolean
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(scheduleID)
        parcel.writeDouble(price)
        parcel.writeString(courtSize)
        parcel.writeString(period)
        parcel.writeString(session)
        parcel.writeByte(if (isTimeRange) 1 else 0)
        parcel.writeString(courtID)
        parcel.writeString(pricingID)
        parcel.writeString(ownerID)
        parcel.writeString(coSoID)
        parcel.writeString(date)
        parcel.writeValue(isBooked)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<TimeSlot> {
        override fun createFromParcel(parcel: Parcel): TimeSlot {
            return TimeSlot(parcel)
        }

        override fun newArray(size: Int): Array<TimeSlot?> {
            return arrayOfNulls(size)
        }
    }
}