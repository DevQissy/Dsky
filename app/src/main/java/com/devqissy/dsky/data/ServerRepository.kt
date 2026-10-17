package com.devqissy.dsky.data

import android.content.Context
import org.json.JSONArray


data class Server(
    val url: String,
    val name: String,
    val ips_v4: String,
    val ips_v6: String,
    val description: String,
    val description_fa: String,
    val website: String,
    val tag: String
) {
    fun hasIPv6(): Boolean = ips_v6.isNotEmpty()
    fun hasIPv4(): Boolean = ips_v4.isNotEmpty()

    fun localizedDescription(locale: java.util.Locale = java.util.Locale.getDefault()): String {
        val isFarsi = locale.language == "fa"
        return if (isFarsi && description_fa.isNotEmpty()) description_fa else description
    }
    fun ipsForMode(mode: String): String = when (mode) {
        "ipv4" -> ips_v4
        "ipv6" -> ips_v6
        else   -> listOf(ips_v4, ips_v6).filter { it.isNotEmpty() }.joinToString(",")
    }
}

object ServerRepository {

    private var cached: List<Server>? = null

    fun getServers(context: Context): List<Server> {
        cached?.let { return it }
        val servers = loadFromAssets(context)
        cached = servers
        return servers
    }

    private fun loadFromAssets(context: Context): List<Server> {
        return try {
            val json = context.assets.open("servers.json").bufferedReader().readText()
            parseJson(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseJson(json: String): List<Server> {
        val list = mutableListOf<Server>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                Server(
                    url            = obj.getString("url"),
                    name           = obj.getString("name"),
                    ips_v4         = obj.optString("ips_v4", ""),
                    ips_v6         = obj.optString("ips_v6", ""),
                    description    = obj.getString("description"),
                    description_fa = obj.optString("description_fa", ""),
                    website        = obj.optString("website", ""),
                    tag            = obj.optString("tag", "U")
                )
            )
        }
        return list
    }

    // suspend fun fetchFromRemote(url: String): List<Server> {
    //     val json = URL(url).readText()
    //     return parseJson(json)
    // }
}