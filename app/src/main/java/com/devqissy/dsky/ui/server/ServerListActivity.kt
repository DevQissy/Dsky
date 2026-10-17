package com.devqissy.dsky.ui.server

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import com.devqissy.dsky.R
import com.devqissy.dsky.data.Server
import com.devqissy.dsky.data.ServerRepository
import com.devqissy.dsky.sys.PersistentState
import com.devqissy.dsky.ui.fragment.ParticleBackground
import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import androidx.core.graphics.toColorInt
import androidx.core.graphics.drawable.toDrawable
import javax.net.ssl.HostnameVerifier

class ServerListActivity : AppCompatActivity() {

    companion object { const val EXTRA_KEY = "server_key" }

    private var prefKey = "server_url"
    private lateinit var layoutBuiltinSection: LinearLayout
    private lateinit var filterContainer: LinearLayout
    private var currentFilter = "all"
    private var ipFilter = "both"

    private var servers: List<Server> = emptyList()
    private var selectedIndex = -1

    private val latencyMap = mutableMapOf<Int, Long>()
    private val executorService = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun getCurrentLanguage(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resources.configuration.locales[0].language
        } else {
            @Suppress("DEPRECATION")
            resources.configuration.locale.language
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.slide_up, R.anim.stay)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_up, R.anim.stay)
        }

        setupWindowInsets()

        prefKey = intent.getStringExtra(EXTRA_KEY) ?: "server_url"

        setupUI()
        loadData()

        onBackPressedDispatcher.addCallback(this) { finishWithAnimation() }
    }

    private fun setupWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.setOnApplyWindowInsetsListener { view, insets ->
                val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                val navigationBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

                view.setPadding(0, statusBarHeight, 0, navigationBarHeight)
                insets
            }

            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller?.let {
                it.isAppearanceLightStatusBars = false
                it.isAppearanceLightNavigationBars = false
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executorService.shutdownNow()
        pingScope.cancel()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finishWithAnimation()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadData() {
        val currentUrl = PreferenceManager.getDefaultSharedPreferences(this)
            .getString(prefKey, "") ?: ""

        servers = ServerRepository.getServers(this)

        layoutBuiltinSection.removeAllViews()
        servers.forEachIndexed { index, _ ->
            layoutBuiltinSection.addView(createServerCard(index))
        }

        val expanded = PersistentState.expandUrl(this, currentUrl)
        val index = servers.indexOfFirst { it.url == expanded }

        if (index >= 0) {
            selectedIndex = index
            selectServer(index)
        } else if (servers.isNotEmpty()) {
            selectedIndex = 0
            selectServer(0)
        }

        startPingTests()
    }

    private fun getCategoryForIndex(index: Int): String {
        val tag = servers.getOrNull(index)?.tag ?: return "general"
        return when (tag) {
            "U" -> "general"
            "C" -> "gaming"
            "G" -> "security"
            "F" -> "family"
            "S" -> "privacy"
            "A" -> "ads"
            "X" -> "sanction"
            else -> "general"
        }
    }

    private fun selectServer(index: Int) {
        selectedIndex = index
        for (i in 0 until layoutBuiltinSection.childCount) {
            updateServerCard(i)
        }
        saveServer()
    }

    private fun saveServer() {
        val server = servers.getOrNull(selectedIndex) ?: return
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putString(prefKey, server.url)
            .apply()
        Toast.makeText(this, getString(R.string.server_selected, server.name), Toast.LENGTH_SHORT).show()
    }

    private fun createServerCard(index: Int): LinearLayout {
        val server = servers[index]
        val isSelected = (index == selectedIndex)
        val category = getCategoryForIndex(index)
        val iconResId = getServerIcon(category)

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(12)
            }
            background = createCardBackground(isSelected)
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { selectServer(index) }

            val titleRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                textDirection = View.TEXT_DIRECTION_LTR
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER_VERTICAL
            }

            val iconView = ImageView(context).apply {
                setImageResource(iconResId)
                val size = dpToPx(24)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = dpToPx(12)
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                }
                setColorFilter(if (isSelected) "#3700B3".toColorInt() else Color.WHITE)
            }
            titleRow.addView(iconView)

            val nameView = TextView(context).apply {
                text = server.name
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (isSelected) "#3700B3".toColorInt() else Color.WHITE)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                textDirection = View.TEXT_DIRECTION_LTR
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dpToPx(0)
                    marginEnd = dpToPx(8)
                }
            }
            titleRow.addView(nameView)

            val latencyView = TextView(context).apply {
                text = getString(R.string.loading_dots)
                textSize = 12f
                setTextColor("#80FFFFFF".toColorInt())
                textDirection = View.TEXT_DIRECTION_LTR
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            titleRow.addView(latencyView)
            addView(titleRow)

            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(1)
                ).apply {
                    topMargin = dpToPx(8)
                    bottomMargin = dpToPx(8)
                    marginStart = dpToPx(4)
                    marginEnd = dpToPx(4)
                }
                setBackgroundColor(if (isSelected) "#33666666".toColorInt() else "#33FFFFFF".toColorInt())
            })

            val descView = TextView(context).apply {
                text = server.localizedDescription()
                textSize = 13f
                setTextColor(if (isSelected) Color.parseColor("#666666") else Color.parseColor("#CCFFFFFF"))
                setPadding(0, dpToPx(4), 0, 0)

                val currentLang = getCurrentLanguage()
                if (currentLang == "fa" && server.description_fa.isNotEmpty()) {
                    textDirection = View.TEXT_DIRECTION_RTL
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                    }
                    gravity = Gravity.END
                } else {
                    textDirection = View.TEXT_DIRECTION_LTR
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    }
                    gravity = Gravity.START
                }

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            addView(descView)

            iconView.tag = "icon_$index"
            nameView.tag = "name_$index"
            latencyView.tag = "latency_$index"
            descView.tag = "desc_$index"
        }
    }

    private fun startPingTests() {
        servers.forEachIndexed { i, server ->
            pingScope.launch {
                val host = extractHost(server.url)
                val ping = performSmartPing(host)
                withContext(Dispatchers.Main) {
                    latencyMap[i] = ping
                    if (ping == -1L) {
                        Log.w("ServerPing", "Failed to ping: ${server.name} ($host)")
                    } else {
                        Log.d("ServerPing", "Success ping: ${server.name} = ${ping}ms")
                    }
                    if (i < layoutBuiltinSection.childCount) {
                        updateServerCard(i)
                    }
                }
            }
        }
    }

    private suspend fun performSmartPing(host: String): Long {
        val systemResult = performSystemPing(host)
        if (systemResult > 0) return systemResult

        val inetResult = performJavaPing(host)
        if (inetResult > 0) return inetResult

        val socketResult = performSocketPing(host, 443)
        if (socketResult > 0) return socketResult

        return performSocketPing(host, 80)
    }

    private suspend fun performSystemPing(host: String): Long = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("/system/bin/ping -c 1 -W 2 $host")
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            process.destroy()

            val regex = Regex("time[=<]([\\d.]+)\\s*ms")
            val match = regex.find(output)
            match?.groupValues?.get(1)?.toDoubleOrNull()?.toLong() ?: -1L
        } catch (e: Exception) {
            -1L
        }
    }

    private suspend fun performJavaPing(host: String): Long = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            val inetAddress = InetAddress.getByName(host)
            if (inetAddress.isReachable(2000)) {
                System.currentTimeMillis() - startTime
            } else {
                -1L
            }
        } catch (e: Exception) {
            -1L
        }
    }

    private suspend fun performSocketPing(host: String, port: Int): Long = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 2000)
            }
            System.currentTimeMillis() - startTime
        } catch (e: Exception) {
            -1L
        }
    }

    private fun extractHost(url: String): String {
        return url
            .replace("https://", "")
            .replace("http://", "")
            .substringBefore("/")
            .substringBefore(":")
    }

    private fun trySimpleHeadRequest(urlStr: String): Long {
        return try {
            val start = System.currentTimeMillis()
            val conn = createUnsafeConnection(urlStr)
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            val code = conn.responseCode
            val elapsed = System.currentTimeMillis() - start
            conn.disconnect()

            if (code in 200..399) {
                elapsed
            } else {
                -1L
            }
        } catch (e: Exception) {
            -1L
        }
    }

    private fun createUnsafeConnection(urlStr: String): HttpsURLConnection {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpsURLConnection
        conn.sslSocketFactory = sslSocketFactory
        conn.hostnameVerifier = HostnameVerifier { _, _ -> true }
        conn.setRequestProperty("User-Agent", "DSKY-Client/1.0")
        conn.instanceFollowRedirects = true
        return conn
    }

    private val trustAllCerts = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val sslSocketFactory: SSLSocketFactory by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustAllCerts), SecureRandom())
        }.socketFactory
    }

    @SuppressLint("SetTextI18n")
    private fun setupUI() {
        val rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val particleBackground = ParticleBackground(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(particleBackground)

        val glassOverlay = View(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = createGlassOverlay()
        }
        rootLayout.addView(glassOverlay)

        val mainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val headerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(12)
                bottomMargin = dpToPx(8)
            }
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            gravity = Gravity.CENTER_VERTICAL
        }

        val rightGlassCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(24).toFloat()
                setColor("#33FFFFFF".toColorInt())
                setStroke(1, "#66FFFFFF".toColorInt())
            }
            setPadding(dpToPx(16), dpToPx(6), dpToPx(12), dpToPx(6))
        }

        val titleText = TextView(this).apply {
            text = getString(R.string.select_server_title)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dpToPx(8)
            }
            gravity = Gravity.CENTER_VERTICAL
            textDirection = View.TEXT_DIRECTION_RTL
        }
        rightGlassCard.addView(titleText)

        val closeButton = ImageView(this).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(Color.WHITE)
            val size = dpToPx(32)
            layoutParams = LinearLayout.LayoutParams(size, size)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener { finishWithAnimation() }
        }
        rightGlassCard.addView(closeButton)

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
        }

        val leftGlassCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(24).toFloat()
                setColor("#33FFFFFF".toColorInt())
                setStroke(1, "#66FFFFFF".toColorInt())
            }
            setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6))
        }

        val filterIconButton = ImageView(this).apply {
            setImageResource(R.drawable.ic_ip)
            setColorFilter(Color.WHITE)
            val size = dpToPx(32)
            layoutParams = LinearLayout.LayoutParams(size, size)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener { showIpFilterDialog() }
        }
        leftGlassCard.addView(filterIconButton)

        headerContainer.addView(leftGlassCard)
        headerContainer.addView(spacer)
        headerContainer.addView(rightGlassCard)
        mainContainer.addView(headerContainer)

        val dividerLine = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(1)
            )
            setBackgroundColor("#33FFFFFF".toColorInt())
        }
        mainContainer.addView(dividerLine)

        val filterScrollView = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(16)
                bottomMargin = dpToPx(16)
            }
            isHorizontalScrollBarEnabled = false
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        filterContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(16), 0, dpToPx(16), 0)
            gravity = Gravity.START
        }

        val filters = listOf(
            FilterData("all", getString(R.string.filter_all), "#4CAF50"),
            FilterData("gaming", getString(R.string.filter_gaming), "#FF5722"),
            FilterData("privacy", getString(R.string.filter_privacy), "#9C27B0"),
            FilterData("security", getString(R.string.filter_security), "#2196F3"),
            FilterData("ads", getString(R.string.filter_ads), "#FF9800"),
            FilterData("family", getString(R.string.filter_family), "#E91E63"),
//            FilterData("sanction", getString(R.string.filter_sanction), "#F44336"),
            FilterData("general", getString(R.string.filter_general), "#607D8B")
        )

        for (filter in filters) {
            filterContainer.addView(createFilterChip(filter.id, filter.name, filter.color))
        }

        filterScrollView.addView(filterContainer)
        mainContainer.addView(filterScrollView)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }

        layoutBuiltinSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(16))
        }
        scrollView.addView(layoutBuiltinSection)
        mainContainer.addView(scrollView)

        rootLayout.addView(mainContainer)
        setContentView(rootLayout)
    }

    private fun createFilterChip(id: String, text: String, colorHex: String): TextView {
        return TextView(this).apply {
            this.text = text
            tag = id
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            textDirection = View.TEXT_DIRECTION_RTL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(38)
            ).apply {
                marginStart = dpToPx(10)
            }
            gravity = Gravity.CENTER
            updateChipStyle(this, id == currentFilter, colorHex)
            setOnClickListener {
                currentFilter = id
                updateFilterSelection()
                applyFilter()
            }
        }
    }

    private fun updateChipStyle(chip: TextView, isSelected: Boolean, colorHex: String) {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(19).toFloat()
            if (isSelected) {
                setColor(Color.WHITE)
                setStroke(0, Color.TRANSPARENT)
            } else {
                setColor("#33FFFFFF".toColorInt())
                setStroke(dpToPx(1), "#80FFFFFF".toColorInt())
            }
        }
        chip.background = bg
        chip.setTextColor(if (isSelected) "#3700B3".toColorInt() else "#CCFFFFFF".toColorInt())
    }

    private fun updateFilterSelection() {
        val colors = mapOf(
            "all" to "#4CAF50", "gaming" to "#FF5722", "privacy" to "#9C27B0",
            "security" to "#2196F3", "ads" to "#FF9800", "family" to "#E91E63",
            "sanction" to "#F44336", "general" to "#607D8B"
        )
        for (i in 0 until filterContainer.childCount) {
            val chip = filterContainer.getChildAt(i) as TextView
            val chipId = chip.tag as String
            updateChipStyle(chip, chipId == currentFilter, colors[chipId] ?: "#4CAF50")
        }
    }

    private fun applyFilter() {
        for (i in 0 until layoutBuiltinSection.childCount) {
            val card = layoutBuiltinSection.getChildAt(i)
            val category = getCategoryForIndex(i)
            val server = servers.getOrNull(i)

            val categoryMatch = when (currentFilter) {
                "all" -> true
                else -> category == currentFilter
            }

            val ipMatch = when (ipFilter) {
                "ipv4" -> server?.hasIPv6() == false
                "ipv6" -> server?.hasIPv6() == true
                else -> true
            }

            card.visibility = if (categoryMatch && ipMatch) View.VISIBLE else View.GONE
        }
    }

    private fun getServerIcon(category: String): Int {
        return when (category) {
            "gaming" -> R.drawable.ic_gaming
            "privacy" -> R.drawable.ic_privacy
            "security" -> R.drawable.ic_security
            "ads" -> R.drawable.ic_ads
            "family" -> R.drawable.ic_family
            "sanction" -> R.drawable.ic_sanction
            "general" -> R.drawable.ic_general
            else -> R.drawable.ic_else
        }
    }

    private fun updateServerCard(index: Int) {
        val card = layoutBuiltinSection.getChildAt(index) as? LinearLayout ?: return
        val isSelected = (index == selectedIndex)
        card.background = createCardBackground(isSelected)

        val iconView = card.findViewWithTag<ImageView>("icon_$index")
        val nameView = card.findViewWithTag<TextView>("name_$index")
        val latencyView = card.findViewWithTag<TextView>("latency_$index")
        val descView = card.findViewWithTag<TextView>("desc_$index")

        iconView?.setColorFilter(if (isSelected) "#3700B3".toColorInt() else Color.WHITE)
        nameView?.setTextColor(if (isSelected) "#3700B3".toColorInt() else Color.WHITE)
        descView?.setTextColor(if (isSelected) "#666666".toColorInt() else "#CCFFFFFF".toColorInt())

        val separator = card.getChildAt(1) as? View
        separator?.setBackgroundColor(if (isSelected) Color.parseColor("#33666666") else Color.parseColor("#33FFFFFF"))

        val server = servers.getOrNull(index)
        if (server != null) {
            val currentLang = getCurrentLanguage()
            val isFarsiDesc = currentLang == "fa" && server.description_fa.isNotEmpty()
            descView?.apply {
                text = server.localizedDescription()
                setPadding(0, dpToPx(4), 0, 0)
                if (isFarsiDesc) {
                    textDirection = View.TEXT_DIRECTION_RTL
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                    }
                    gravity = Gravity.END
                } else {
                    textDirection = View.TEXT_DIRECTION_LTR
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    }
                    gravity = Gravity.START
                }
            }
        }

        when {
            index !in latencyMap -> {
                latencyView?.text = getString(R.string.loading_dots)
                latencyView?.setTextColor(Color.parseColor("#80FFFFFF"))
            }
            latencyMap[index]!! > 0 -> {
                val ping = latencyMap[index]!!
                latencyView?.text = "${ping}ms"
                latencyView?.setTextColor(when {
                    ping < 500 -> "#4CAF50".toColorInt()
                    ping < 1500 -> "#FFC107".toColorInt()
                    else -> "#FF9800".toColorInt()
                })
            }
            else -> {
                latencyView?.text = getString(R.string.loading_dots)
                latencyView?.setTextColor(Color.parseColor("#80FFFFFF"))
            }
        }
    }

    private fun createCardBackground(isSelected: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpToPx(16).toFloat()
        if (isSelected) {
            setColor(Color.parseColor("#E6FFFFFF"))
            setStroke(0, Color.TRANSPARENT)
        } else {
            setColor("#1AFFFFFF".toColorInt())
            setStroke(dpToPx(1), "#4DFFFFFF".toColorInt())
        }
    }

    private fun createGlassOverlay() = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor("#991E1E2E".toColorInt())
    }

    private fun showIpFilterDialog() {
        val options = listOf(
            Triple(getString(R.string.ip_filter_both), "both", R.drawable.ic_ips),
            Triple(getString(R.string.ip_filter_ipv4), "ipv4", R.drawable.ic_ipv4),
            Triple(getString(R.string.ip_filter_ipv6), "ipv6", R.drawable.ic_ipv6)
        )

        val bgColor = "#1E1E1E".toColorInt()
        val textColor = "#FFFFFF".toColorInt()
        val subTextColor = "#CCFFFFFF".toColorInt()
        val strokeColor = "#33FFFFFF".toColorInt()
        val selectedBg = "#33FFFFFF".toColorInt()
        val selectedStroke = "#FFFFFF".toColorInt()

        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(24).toFloat()
                setColor(bgColor)
                setStroke(dpToPx(1), strokeColor)
            }
            setPadding(dpToPx(8), dpToPx(20), dpToPx(8), dpToPx(12))
            elevation = dpToPx(8).toFloat()
        }

        val titleView = TextView(this).apply {
            text = getString(R.string.ip_filter_title)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textColor)
            gravity = Gravity.CENTER
            textDirection = View.TEXT_DIRECTION_RTL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(8) }
        }
        dialogView.addView(titleView)

        dialogView.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)
            ).apply {
                marginStart = dpToPx(8)
                marginEnd = dpToPx(8)
                bottomMargin = dpToPx(8)
            }
            setBackgroundColor(strokeColor)
        })

        val dialog = Dialog(this)

        options.forEach { (label, value, iconRes) ->
            val isSelected = ipFilter == value

            val itemRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                background = if (isSelected) {
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dpToPx(14).toFloat()
                        setColor(selectedBg)
                        setStroke(dpToPx(1), selectedStroke)
                    }
                } else {
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dpToPx(14).toFloat()
                        setColor(Color.TRANSPARENT)
                    }
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(56)
                ).apply {
                    marginStart = dpToPx(4)
                    marginEnd = dpToPx(4)
                    bottomMargin = dpToPx(4)
                }
                setPadding(dpToPx(16), 0, dpToPx(16), 0)
                setOnClickListener {
                    ipFilter = value
                    applyFilter()
                    dialog.dismiss()
                }
            }

            val iconView = ImageView(this).apply {
                setImageResource(iconRes)
                val size = dpToPx(22)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = dpToPx(14)
                }
                setColorFilter(if (isSelected) Color.WHITE else subTextColor)
            }
            itemRow.addView(iconView)

            val labelView = TextView(this).apply {
                text = label
                textSize = 15f
                setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (isSelected) textColor else subTextColor)
                textDirection = View.TEXT_DIRECTION_RTL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            itemRow.addView(labelView)

            if (isSelected) {
                val checkView = ImageView(this).apply {
                    setImageResource(R.drawable.ic_check)
                    val size = dpToPx(20)
                    layoutParams = LinearLayout.LayoutParams(size, size)
                    setColorFilter(Color.WHITE)
                }
                itemRow.addView(checkView)
            }

            dialogView.addView(itemRow)
        }

        dialogView.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)
            ).apply {
                marginStart = dpToPx(8)
                marginEnd = dpToPx(8)
                topMargin = dpToPx(8)
                bottomMargin = dpToPx(8)
            }
            setBackgroundColor(strokeColor)
        })

        val cancelButton = TextView(this).apply {
            text = getString(R.string.close)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(subTextColor)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            textDirection = View.TEXT_DIRECTION_RTL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(44)
            )
            setOnClickListener { dialog.dismiss() }
        }
        dialogView.addView(cancelButton)

        dialog.setContentView(dialogView)
        dialog.window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(
                (resources.displayMetrics.widthPixels * 0.85).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.CENTER)
        }
        dialog.show()
    }

    private fun finishWithAnimation() {
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.stay, R.anim.slide_down)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.stay, R.anim.slide_down)
        }
    }
}

data class FilterData(val id: String, val name: String, val color: String)