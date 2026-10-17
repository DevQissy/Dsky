package com.devqissy.dsky.data

import android.content.res.AssetManager
import java.io.IOException
import java.net.InetAddress

/**
 * Map IP addresses to country codes, using a fixed-width sorted database.
 * Lookups are performed using a binary search.
 */
internal class CountryMap @Throws(IOException::class) constructor(assetManager: AssetManager) {

    companion object {
        private const val COUNTRY_SIZE = 2
    }

    private val db: ByteArray = assetManager.open("dbip.v4").readBytes()

    private fun lessEqual(db: ByteArray, position: Int, key: ByteArray): Boolean {
        for (i in key.indices) {
            val ai = db[position + i].toInt() and 0xFF
            val bi = key[i].toInt() and 0xFF
            if (ai < bi) return true
            if (ai > bi) return false
        }
        return true
    }

    fun getCountryCode(address: InetAddress): String {
        val key = address.address
        if (key.size != 4) return "ZZ"  // Only IPv4 supported

        val recordSize = key.size + COUNTRY_SIZE
        var low = 0
        var high = db.size / recordSize

        while (high - low > 1) {
            val mid = (low + high) / 2
            if (lessEqual(db, mid * recordSize, key)) low = mid
            else high = mid
        }

        val position = low * recordSize + key.size
        return String(db.copyOfRange(position, position + COUNTRY_SIZE), Charsets.UTF_8)
    }
}