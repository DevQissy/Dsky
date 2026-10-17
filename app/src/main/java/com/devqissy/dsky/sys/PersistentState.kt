package com.devqissy.dsky.sys

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.preference.PreferenceManager
import android.util.Log
import com.devqissy.dsky.data.ServerRepository
import com.devqissy.dsky.util.Untemplate
import java.net.MalformedURLException
import java.net.URL

/**
 * Static class representing on-disk storage of mutable state. Collecting this all in one class
 * helps to reduce duplication of code using SharedPreferences, allows settings to be read and
 * written by separate components, and also helps to improve preference naming consistency.
 */
object PersistentState {
    private const val LOG_TAG = "PersistentState"

    const val APPS_KEY = "pref_apps"
    const val URL_KEY = "pref_server_url"

    private const val APPROVED_KEY = "approved"
    private const val ENABLED_KEY = "enabled"
    private const val SERVER_KEY = "server"
    private const val INTERNAL_STATE_NAME = "MainActivity"

    // The approval state is currently stored in a separate preferences file.
    // TODO: Unify preferences into a single file.
    private const val APPROVAL_PREFS_NAME = "IntroState"

    private fun getInternalState(context: Context): SharedPreferences =
        context.getSharedPreferences(INTERNAL_STATE_NAME, Context.MODE_PRIVATE)

    private fun getUserPreferences(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    @JvmStatic
    fun getVpnEnabled(context: Context): Boolean =
        getInternalState(context).getBoolean(ENABLED_KEY, false)

    @JvmStatic
    fun setVpnEnabled(context: Context, enabled: Boolean) {
        getInternalState(context).edit().putBoolean(ENABLED_KEY, enabled).apply()
    }

    @JvmStatic
    fun syncLegacyState(context: Context) {
        if (getServerUrl(context) != null) return

        val settings = getInternalState(context)
        val domain = settings.getString(SERVER_KEY, null) ?: return

        val servers = ServerRepository.getServers(context)
        val defaultUrl = servers.firstOrNull()?.url ?: return
        val defaultDomain = Uri.parse(defaultUrl).host ?: ""

        val url = when {
            domain == defaultDomain -> defaultUrl
            else -> servers.firstOrNull { Uri.parse(it.url).host == domain }?.url
        }

        if (url == null) {
            Log.w(LOG_TAG, "Legacy domain is unrecognized")
            return
        }
        setServerUrl(context, url)
    }

    @JvmStatic
    fun setServerUrl(context: Context, url: String) {
        getUserPreferences(context).edit().putString(URL_KEY, url).apply()
    }

    @JvmStatic
    fun getServerUrl(context: Context): String? {
        val urlTemplate = getUserPreferences(context).getString(URL_KEY, null) ?: return null
        return Untemplate.strip(urlTemplate)
    }

    private fun extractHost(url: String): String? {
        return try {
            URL(url).host
        } catch (e: MalformedURLException) {
            Log.w(LOG_TAG, "URL is corrupted: $url")
            null
        }
    }

    @JvmStatic
    fun expandUrl(context: Context, url: String?): String {
        if (url.isNullOrEmpty()) return ServerRepository.getServers(context).firstOrNull()?.url ?: ""
        return url
    }

    @JvmStatic
    fun getServerName(context: Context): String? =
        extractHost(expandUrl(context, getServerUrl(context)))

    @JvmStatic
    fun extractHostForAnalytics(context: Context, url: String): String {
        val expanded = expandUrl(context, url)
        val servers = ServerRepository.getServers(context)
        return if (servers.any { it.url == expanded }) extractHost(expanded) ?: ""
        else InternalNames.CUSTOM_SERVER.name
    }

    private fun getApprovalSettings(context: Context): SharedPreferences =
        context.getSharedPreferences(APPROVAL_PREFS_NAME, Context.MODE_PRIVATE)

    @JvmStatic
    fun getWelcomeApproved(context: Context): Boolean =
        getApprovalSettings(context).getBoolean(APPROVED_KEY, false)

    @JvmStatic
    fun setWelcomeApproved(context: Context, approved: Boolean) {
        getApprovalSettings(context).edit().putBoolean(APPROVED_KEY, approved).apply()
    }

    @JvmStatic
    fun setExcludedPackages(context: Context, packages: Set<String>) {
        getUserPreferences(context).edit().putStringSet(APPS_KEY, packages).apply()
    }

    @JvmStatic
    fun getExcludedPackages(context: Context): Set<String> =
        getUserPreferences(context).getStringSet(APPS_KEY, emptySet()) ?: emptySet()
}