package com.trungkien.fbtp.model

import android.os.Parcel
import android.os.Parcelable
import com.google.firebase.firestore.PropertyName

data class TimeFrame(
    @PropertyName("timeFrameID") val timeFrameID: String = "",
    @PropertyName("courtID") val courtID: String = "",
    @PropertyName("coSoID") val coSoID: String = "",
    @PropertyName("date") val date: String = "",
    @PropertyName("period") val period: List<String> = emptyList()
) : Parcelable {
    constructor() : this("", "", "", "", emptyList())

    constructor(parcel: Parcel) : this(
        timeFrameID = parcel.readString() ?: "",
        courtID = parcel.readString() ?: "",
        coSoID = parcel.readString() ?: "",
        date = parcel.readString() ?: "",
        period = parcel.createStringArrayList() ?: emptyList()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(timeFrameID)
        parcel.writeString(courtID)
        parcel.writeString(coSoID)
        parcel.writeString(date)
        parcel.writeStringList(period)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<TimeFrame> {
        override fun createFromParcel(parcel: Parcel): TimeFrame {
            return TimeFrame(parcel)
        }

        override fun newArray(size: Int): Array<TimeFrame?> {
            return arrayOfNulls(size)
        }
    }
}