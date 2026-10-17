package com.devqissy.dsky.data

import android.os.Parcel
import android.os.Parcelable

class AppInfo(
    val label: String?,
    val packageName: String
) : Comparable<AppInfo>, Parcelable {

    override fun compareTo(other: AppInfo): Int {
        if (label == null) {
            return if (other.label == null) packageName.compareTo(other.packageName) else 1
        } else if (other.label == null) {
            return -1
        } else if (label != other.label) {
            return label.compareTo(other.label)
        }
        return packageName.compareTo(other.packageName)
    }

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeStringArray(arrayOf(label, packageName))
    }

    companion object CREATOR : Parcelable.Creator<AppInfo> {
        override fun createFromParcel(parcel: Parcel): AppInfo? {
            val array = parcel.createStringArray() ?: return null
            if (array.size < 2) return null
            return AppInfo(array[0], array[1] ?: return null)
        }
        override fun newArray(size: Int): Array<AppInfo?> = arrayOfNulls(size)
    }
}