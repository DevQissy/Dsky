package com.devqissy.dsky.ui

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.widget.AppCompatImageButton
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.devqissy.dsky.R
import com.devqissy.dsky.data.ServerRepository
import com.devqissy.dsky.net.doh.Transaction
import com.devqissy.dsky.sys.IntraVpnService
import com.devqissy.dsky.sys.PersistentState
import com.devqissy.dsky.sys.VpnController
import com.devqissy.dsky.ui.server.ServerListActivity
import java.net.NetworkInterface
import java.net.SocketException
import java.util.LinkedList
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri

class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val LOG_TAG = "MainActivity"
        private const val PREF_RATE_APP = "rate_app"
        private const val PREF_RATE_APP_LAST_SHOWN = "rate_app_last_shown"
        private const val PREF_APP_LAUNCH_COUNT = "app_launch_count"
        private const val PREF_UPDATE_CHECK_TIME = "update_check_time"
    }

    private val viewModel: MainViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) startDnsVpnService()
        else stopDnsVpnService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            window.insetsController?.setSystemBarsAppearance(0, 0)
        } else {
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(0, 0)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        PersistentState.setWelcomeApproved(this, true)
        PersistentState.syncLegacyState(this)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val currentUrl = prefs.getString(PersistentState.URL_KEY, null)
        if (currentUrl == null || "null" == currentUrl || currentUrl.isEmpty()) {
            prefs.edit().putString(PersistentState.URL_KEY, "https://dns.google/dns-query").apply()
        }

        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(this)

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        VpnController.initEventViewModel(this)

        VpnController.getEventViewModel()?.dnsStatus?.observe(this) {
            syncDnsStatus()
        }

        VpnController.getEventViewModel()?.transaction?.observe(this) { transaction ->
            if (transaction != null) {
                VpnController.getEventViewModel()?.clearTransaction()
            }
        }

        incrementAppLaunchCount()
        checkAndShowRateDialog()

        val rootLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor("#0D1117".toColorInt())
        }

        val particleBackground = com.devqissy.dsky.ui.fragment.ParticleBackground(this).apply {
            id = View.generateViewId()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(particleBackground)

        val mainLinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(0, getStatusBarHeight(), 0, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                layoutDirection = View.LAYOUT_DIRECTION_LTR
            }
        }

        val buttonsMainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(24)
                bottomMargin = dpToPx(8)
                marginStart = dpToPx(12)
                marginEnd = dpToPx(12)
            }
            gravity = Gravity.CENTER_VERTICAL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                layoutDirection = View.LAYOUT_DIRECTION_LTR
            }
        }

        val leftGlassCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.START

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(24).toFloat()
                setColor("#33FFFFFF".toColorInt())
                setStroke(1, "#66FFFFFF".toColorInt())
            }
            setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4))
        }

        val messageButton = AppCompatImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(36),
                dpToPx(36)
            ).apply {
                marginEnd = dpToPx(2)
            }
            setImageResource(R.drawable.ic_crown)
            imageTintList = ContextCompat.getColorStateList(this@MainActivity, android.R.color.white)
            background = getSelectableItemBackground()
            setOnClickListener {
                showGitHubRedirectDialog()
            }
        }

        val splitTuningButton = AppCompatImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(36),
                dpToPx(36)
            )
            setImageResource(R.drawable.ic_sliders)
            imageTintList = ContextCompat.getColorStateList(this@MainActivity, android.R.color.white)
            background = getSelectableItemBackground()
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ExcludedAppsActivity::class.java))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.fade_in, R.anim.fade_out)
                } else {
                    @Suppress("DEPRECATION")
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                }
            }
        }

        leftGlassCard.addView(messageButton)
        leftGlassCard.addView(splitTuningButton)

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
        }

        val rightGlassCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.END

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(24).toFloat()
                setColor("#33FFFFFF".toColorInt())
                setStroke(1, "#66FFFFFF".toColorInt())
            }
            setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4))
        }

        val menuComposeView = ComposeView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(36)
            )
            setContent {
                var expanded by remember { mutableStateOf(false) }

                val menuSurface = ComposeColor(0xFF1E2430)
                val menuOnSurface = ComposeColor(0xFFFFFFFF)

                Box(contentAlignment = Alignment.TopEnd) {
                    androidx.compose.material3.IconButton(
                        onClick = { expanded = true },
                        modifier = androidx.compose.ui.Modifier.size(36.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_ellipsis_vertical),
                            contentDescription = getString(R.string.menu),
                            tint = ComposeColor.White,
                            modifier = androidx.compose.ui.Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        offset = DpOffset(0.dp, 0.dp),
                        containerColor = menuSurface,
                    ) {
                        val itemTextStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 15.sp,
                            color = menuOnSurface,
                            textDirection = androidx.compose.ui.text.style.TextDirection.Rtl
                        )

                        DropdownMenuItem(
                            text = { Text(getString(R.string.help), style = itemTextStyle) },
                            onClick = { expanded = false; showGuideDialog() }
                        )
                        DropdownMenuItem(
                            text = { Text(getString(R.string.support), style = itemTextStyle) },
                            onClick = { expanded = false; showSupportDialog() }
                        )
                        DropdownMenuItem(
                            text = { Text(getString(R.string.rate_app), style = itemTextStyle) },
                            onClick = { expanded = false; showRateDialogManually() }
                        )
                    }
                }
            }
        }

        rightGlassCard.addView(menuComposeView)

        buttonsMainContainer.addView(leftGlassCard)
        buttonsMainContainer.addView(spacer)
        buttonsMainContainer.addView(rightGlassCard)
        mainLinearLayout.addView(buttonsMainContainer)

        val contentContainer = FrameLayout(this).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        mainLinearLayout.addView(contentContainer)
        rootLayout.addView(mainLinearLayout)
        setContentView(rootLayout)

        val composeView = ComposeView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setContent {
                val uiState by viewModel.uiState.collectAsState()
                MainControlScreen(
                    uiState = uiState,
                    onMoonClick = { toggleVpnConnection() },
                    onServerClick = {
                        startActivity(
                            Intent(this@MainActivity, ServerListActivity::class.java).apply {
                                putExtra(ServerListActivity.EXTRA_KEY, PersistentState.URL_KEY)
                            }
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.slide_up, R.anim.stay)
                        } else {
                            @Suppress("DEPRECATION")
                            overridePendingTransition(R.anim.slide_up, R.anim.stay)
                        }
                    },
                    onChangeServerClick = {
                        startActivity(
                            Intent(this@MainActivity, ServerListActivity::class.java).apply {
                                putExtra(ServerListActivity.EXTRA_KEY, PersistentState.URL_KEY)
                            }
                        )
                    }
                )
            }
        }
        contentContainer.addView(composeView)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }

        syncDnsStatus()
        maybeAutostart()
        registerBackPressedCallback()

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            autoCheckForMyketUpdate()
        }, 3000)
    }

    private fun shouldCheckForUpdate(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val lastCheckTime = prefs.getLong(PREF_UPDATE_CHECK_TIME, 0)
        val oneDayInMillis = 24 * 60 * 60 * 1000L

        return System.currentTimeMillis() - lastCheckTime > oneDayInMillis
    }

    private fun autoCheckForMyketUpdate() {
        if (!shouldCheckForUpdate()) {
            Log.d(LOG_TAG, "Skipping update check, checked recently")
            return
        }

        try {
            val packageName = this.packageName
            val uri = "myket://check-update?id=$packageName".toUri()
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)

            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            prefs.edit().putLong(PREF_UPDATE_CHECK_TIME, System.currentTimeMillis()).apply()

            Log.d(LOG_TAG, "Myket update check triggered automatically")
        } catch (e: android.content.ActivityNotFoundException) {
            Log.d(LOG_TAG, "Myket app is not installed.")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error checking for Myket update", e)
        }
    }

    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    private fun syncDnsStatus() {
        val status = VpnController.getInstance().getState(this)
        val privateDns = when (getPrivateDnsMode()) {
            PrivateDnsMode.STRICT   -> com.devqissy.dsky.ui.PrivateDnsMode.STRICT
            PrivateDnsMode.UPGRADED -> com.devqissy.dsky.ui.PrivateDnsMode.UPGRADED
            else                    -> com.devqissy.dsky.ui.PrivateDnsMode.NONE
        }
        viewModel.updateState(
            VpnUiState(
                vpnState = status,
                serverName = getCurrentServerName(),
                systemDnsServer = if (!status.on) getSystemDnsServer() ?: "" else "",
                isAnotherVpnActive = isAnotherVpnActive(),
                privateDnsMode = privateDns,
                showChangeServerButton = status.activationRequested &&
                        status.connectionState == IntraVpnService.State.FAILING
            )
        )
    }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String?) {
        if (PersistentState.URL_KEY == key) syncDnsStatus()
    }

    private fun toggleVpnConnection() {
        val state = VpnController.getInstance().getState(this)
        if (state.activationRequested) {
            stopDnsVpnService()
        } else {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val url = prefs.getString(PersistentState.URL_KEY, null)
            if (url == null || "null" == url || url.isEmpty()) {
                prefs.edit().putString(PersistentState.URL_KEY, "https://dns.google/dns-query").commit()
            }
            prepareAndStartDnsVpn()
        }
    }

    override fun onAttachedToWindow() {}

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        syncDnsStatus()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    private fun maybeAutostart() {
        val state = VpnController.getInstance().getState(this)
        if (state.activationRequested && !state.on) {
            Log.i(LOG_TAG, "Autostart enabled")
            prepareAndStartDnsVpn()
        }
    }

    private fun startDnsVpnService() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        Log.d("DEBUG_INTRA", "Starting with URL: ${prefs.getString(PersistentState.URL_KEY, "is still null!")}")
        VpnController.getInstance().start(this)
        syncDnsStatus()
    }

    private fun stopDnsVpnService() {
        VpnController.getInstance().stop(this)
        syncDnsStatus()
    }

    private fun getHistory(): LinkedList<Transaction> =
        VpnController.getInstance().getTracker(this).getRecentTransactions()

    private fun getNumRequests(): Long =
        VpnController.getInstance().getTracker(this).getNumRequests()

    private fun prepareAndStartDnsVpn() {
        if (hasVpnService()) {
            if (prepareVpnService()) startDnsVpnService()
        } else {
            Log.e(LOG_TAG, "Device does not support system-wide VPN mode.")
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun hasVpnService(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH

    private fun prepareVpnService(): Boolean {
        val prepareVpnIntent = try {
            VpnService.prepare(this)
        } catch (e: NullPointerException) {
            Log.e(LOG_TAG, "Failed to prepare VPN", e)
            return false
        }
        if (prepareVpnIntent != null) {
            Log.i(LOG_TAG, "Prepare VPN with activity")
            vpnPermissionLauncher.launch(prepareVpnIntent)
            syncDnsStatus()
            return false
        }
        return true
    }

    private fun getLinkProperties(): LinkProperties? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork ?: return null
        return cm.getLinkProperties(activeNetwork)
    }

    private fun getSystemDnsServer(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null
        val linkProperties = getLinkProperties() ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val privateDnsServerName = linkProperties.privateDnsServerName
            if (privateDnsServerName != null) return privateDnsServerName
        }
        return linkProperties.dnsServers.firstOrNull()?.hostAddress
    }

    private enum class PrivateDnsMode { NONE, UPGRADED, STRICT }

    private fun getPrivateDnsMode(): PrivateDnsMode {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return PrivateDnsMode.NONE
        val linkProperties = getLinkProperties() ?: return PrivateDnsMode.NONE
        if (linkProperties.privateDnsServerName != null) return PrivateDnsMode.STRICT
        if (linkProperties.isPrivateDnsActive) return PrivateDnsMode.UPGRADED
        return PrivateDnsMode.NONE
    }

    private fun isAnotherVpnActive(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val ni = networkInterfaces.nextElement()
                val name = ni.name
                if (ni.isUp && name != null &&
                    (name.startsWith("tun") || name.startsWith("pptp") || name.startsWith("l2tp"))
                ) return true
            }
        } catch (e: SocketException) {
            Log.e(LOG_TAG, "Failed to check VPN interfaces", e)
        }
        return false
    }

    private fun registerBackPressedCallback() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
    }

    private fun showGuideDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.DarkAlertDialogTheme)
            .setTitle(getString(R.string.help_title))
            .setMessage(getString(R.string.help_message))
            .setPositiveButton(getString(R.string.got_it), null)
            .show()
    }

    private fun showSupportDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.DarkAlertDialogTheme)
            .setTitle(getString(R.string.support_feedback_title))
            .setMessage(getString(R.string.support_message))
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.continuee)) { _, _ ->
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = android.net.Uri.parse("mailto:")
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("dev.qissy@gmail.com"))
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_subject_feedback))
                }
                try {
                    startActivity(intent)
                } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(this, getString(R.string.email_app_not_found), Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showGitHubRedirectDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.DarkAlertDialogTheme)
            .setTitle(getString(R.string.github_redirect_title))
            .setMessage(getString(R.string.github_redirect_message))
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.continuee)) { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = "https://github.com/DevQissy/Dsky".toUri()
                }
                try {
                    startActivity(intent)
                } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(this, getString(R.string.browser_not_found), Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showRateDialogManually() {
        showRateDialog(isManual = true)
    }

    private fun showRateDialogAutomatically() {
        showRateDialog(isManual = false)
    }

    private fun showRateDialog(isManual: Boolean) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.DarkAlertDialogTheme)
            .setTitle(getString(R.string.rate_app_title))
            .setMessage(getString(R.string.rate_app_message))
            .setNegativeButton(getString(R.string.later)) { _, _ ->
                if (!isManual) {
                    updateRateAppLastShown()
                }
            }
            .setPositiveButton(getString(R.string.submit_rating)) { _, _ ->
                openRateAppLink()
                if (!isManual) {
                    markRateAppAsRated()
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun openRateAppLink() {
        val packageName = packageName
        val myketIntent = Intent(Intent.ACTION_VIEW).apply {
            data = "myket://details?id=$packageName".toUri()
            setPackage("ir.mservices.market")
        }

        try {
            startActivity(myketIntent)
        } catch (_: android.content.ActivityNotFoundException) {
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "https://myket.ir/app/$packageName".toUri()
            }
            try {
                startActivity(webIntent)
            } catch (_: android.content.ActivityNotFoundException) {
                Toast.makeText(this, getString(R.string.browser_not_found), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun incrementAppLaunchCount() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val currentCount = prefs.getInt(PREF_APP_LAUNCH_COUNT, 0)
        prefs.edit().putInt(PREF_APP_LAUNCH_COUNT, currentCount + 1).apply()
    }

    private fun getAppLaunchCount(): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getInt(PREF_APP_LAUNCH_COUNT, 0)
    }

    private fun markRateAppAsRated() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit()
            .putBoolean(PREF_RATE_APP, true)
            .remove(PREF_RATE_APP_LAST_SHOWN)
            .apply()
    }

    private fun isRateAppRated(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getBoolean(PREF_RATE_APP, false)
    }

    private fun updateRateAppLastShown() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putLong(PREF_RATE_APP_LAST_SHOWN, System.currentTimeMillis()).apply()
    }

    private fun getRateAppLastShown(): Long {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getLong(PREF_RATE_APP_LAST_SHOWN, 0)
    }

    private fun shouldShowRateDialog(): Boolean {
        if (isRateAppRated()) {
            return false
        }

        val launchCount = getAppLaunchCount()
        if (launchCount < 2) {
            return false
        }

        val lastShown = getRateAppLastShown()
        val threeDaysInMillis = 3 * 24 * 60 * 60 * 1000L
        if (lastShown > 0 && System.currentTimeMillis() - lastShown < threeDaysInMillis) {
            return false
        }

        return true
    }

    private fun checkAndShowRateDialog() {
        if (shouldShowRateDialog()) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                showRateDialogAutomatically()
            }, 2000)
        }
    }

    private fun showRateDialog() {
        showRateDialogManually()
    }

    private fun getCurrentServerName(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val url = prefs.getString(PersistentState.URL_KEY, "https://dns.google/dns-query") ?: ""
        val expanded = PersistentState.expandUrl(this, url)
        val servers = ServerRepository.getServers(this)
        return servers.firstOrNull { it.url == expanded }?.name ?: getString(R.string.custom_server)
    }

    private fun getSelectableItemBackground(): android.graphics.drawable.Drawable? {
        val typedValue = android.util.TypedValue()
        if (theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true)) {
            return ContextCompat.getDrawable(this, typedValue.resourceId)
        }
        return null
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}