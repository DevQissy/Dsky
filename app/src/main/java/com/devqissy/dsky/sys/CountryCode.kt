package com.devqissy.dsky.sys

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Static class for getting the system's country code.
 */
class CountryCode(context: Context) {
    // The SIM or CDMA country.
    val deviceCountry: String
    // The country claimed by the attached cell network.
    val networkCountry: String

    init {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        deviceCountry = countryFromSim(telephonyManager)
        networkCountry = telephonyManager.networkCountryIso
    }

    companion object {
        private const val TAG = "CountryCode"

        @SuppressLint("PrivateApi")
        private fun countryFromSim(telephonyManager: TelephonyManager): String {
            val simCountry = telephonyManager.simCountryIso
            if (simCountry.isNotEmpty()) return simCountry
            // User appears to be non-GSM. Try CDMA.
            return try {
                val systemPropertiesClass = Class.forName("android.os.SystemProperties")
                val get = systemPropertiesClass.getMethod("get", String::class.java)
                val cdmaOperator = get.invoke(systemPropertiesClass, "ro.cdma.home.operator.numeric") as? String
                if (cdmaOperator.isNullOrEmpty()) return ""
                val mcc = cdmaOperator.substring(0, 3)
                @SuppressLint("PrivateApi")
                val mccTableClass = Class.forName("com.android.internal.telephony.MccTable")
                val countryCodeForMcc = mccTableClass.getMethod("countryCodeForMcc", String::class.java)
                countryCodeForMcc.invoke(mccTableClass, mcc) as? String ?: ""
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get country from CDMA", e)
                ""
            }
        }
    }
}