package com.devqissy.dsky.ui

import android.content.pm.PackageManager
import android.view.ContextThemeWrapper
import android.graphics.Color
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.setPadding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.devqissy.dsky.R
import com.devqissy.dsky.sys.PersistentState
import com.devqissy.dsky.ui.fragment.ParticleBackground
import kotlinx.coroutines.*
import androidx.core.graphics.toColorInt

class ExcludedAppsActivity : ComponentActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppListAdapter
    private lateinit var loadingView: FrameLayout

    private var allAppsList = listOf<AppInfo>()
    private var excludedSet = mutableSetOf<String>()

    private val iconCache = LruCache<String, Drawable>(300)
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupWindowInsets()

        setupUI()
        loadApps()
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
        mainScope.cancel()
        iconCache.evictAll()
    }


    private fun loadApps() {
        loadingView.visibility = View.VISIBLE
        mainScope.launch {
            val (apps, excluded) = withContext(Dispatchers.IO) {
                val a = loadInstalledApps()
                val e = PersistentState.getExcludedPackages(this@ExcludedAppsActivity)
                Pair(a, e)
            }
            allAppsList = apps
            excludedSet = excluded.toMutableSet()
            loadingView.visibility = View.GONE
            adapter.submitList(allAppsList)
        }
    }

    private fun loadInstalledApps(): List<AppInfo> {
        val pm = packageManager
        val selfPkg = packageName
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != selfPkg }
            .map { app ->
                val name = pm.getApplicationLabel(app).toString()
                val icon = iconCache.get(app.packageName) ?: run {
                    val loaded = pm.getApplicationIcon(app)
                    iconCache.put(app.packageName, loaded)
                    loaded
                }
                AppInfo(app.packageName, name, icon)
            }
            .sortedBy { it.name }
    }


    fun onItemChecked(packageName: String, isChecked: Boolean) {
        if (isChecked) excludedSet.add(packageName) else excludedSet.remove(packageName)
        PersistentState.setExcludedPackages(this, excludedSet)
    }

    fun isExcluded(packageName: String) = excludedSet.contains(packageName)


    private fun showHelpDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.excluded_apps_title))
            .setMessage(getString(R.string.excluded_apps_help_message))
            .setPositiveButton(getString(R.string.got_it), null)
            .apply {
                val dialog = create()
                dialog.setOnShowListener {
                    val button = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                    button.setTextColor("#BB86FC".toColorInt())
                    button.setTypeface(null, Typeface.BOLD)
                }
                dialog.show()
            }
    }


    private fun setupUI() {
        val rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        rootLayout.addView(ParticleBackground(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        })

        val mainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        mainContainer.addView(buildHeader())
        mainContainer.addView(buildDivider())
        mainContainer.addView(buildRecyclerView())

        rootLayout.addView(mainContainer)
        rootLayout.addView(buildLoadingView())
        setContentView(rootLayout)
    }

    private fun buildHeader(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
                marginStart = dp(12)
                marginEnd = dp(12)
            }
            gravity = Gravity.CENTER_VERTICAL

            addView(buildIconButton(R.drawable.ic_question) { showHelpDialog() })

            addView(View(this@ExcludedAppsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(8), 1)
            })

            addView(buildSearchBox())

            addView(View(this@ExcludedAppsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(8), 1)
            })

            addView(buildIconButton(R.drawable.ic_close) { finishWithAnimation() })
        }
    }

    private fun buildIconButton(iconRes: Int, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            background = glassBackground(dp(22).toFloat())
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }

            addView(ImageView(this@ExcludedAppsActivity).apply {
                setImageResource(iconRes)
                setColorFilter(Color.WHITE)
                val size = dp(22)
                layoutParams = LinearLayout.LayoutParams(size, size)
            })
        }
    }

    private fun buildSearchBox(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
            background = glassBackground(dp(22).toFloat())
            setPadding(dp(12), 0, dp(12), 0)
            gravity = Gravity.CENTER_VERTICAL

            addView(ImageView(this@ExcludedAppsActivity).apply {
                setImageResource(R.drawable.ic_search)
                setColorFilter("#AAFFFFFF".toColorInt())
                val size = dp(18)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = dp(8)
                }
            })

            addView(EditText(this@ExcludedAppsActivity).apply {
                hint = getString(R.string.search_hint)
                setHintTextColor("#88FFFFFF".toColorInt())
                setTextColor(Color.WHITE)
                textSize = 14f
                background = null
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        val q = s?.toString() ?: ""
                        val filtered = if (q.isEmpty()) allAppsList
                        else allAppsList.filter { it.name.contains(q, ignoreCase = true) }
                        adapter.submitList(filtered)
                    }
                })
            })
        }
    }

    private fun buildDivider() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
        ).apply {
            topMargin = dp(12); bottomMargin = dp(12)
            marginStart = dp(12); marginEnd = dp(12)
        }
        setBackgroundColor("#33FFFFFF".toColorInt())
    }

    private fun buildRecyclerView(): RecyclerView {
        adapter = AppListAdapter(this)
        return RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply { marginStart = dp(12); marginEnd = dp(12) }
            layoutManager = LinearLayoutManager(this@ExcludedAppsActivity)
            this.adapter = this@ExcludedAppsActivity.adapter
            setHasFixedSize(true)
            itemAnimator = null
        }.also { recyclerView = it }
    }

    private fun buildLoadingView(): FrameLayout {
        val iconSize = dp(48)
        val cardSize = dp(96)

        val card = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(cardSize, cardSize, Gravity.CENTER)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24).toFloat()
                setColor("#55000000".toColorInt())
                setStroke(1, "#66FFFFFF".toColorInt())
            }
            visibility = View.GONE
        }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_loader)
            setColorFilter(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
        }
        card.addView(icon)

        val rotate = RotateAnimation(
            0f, 360f,
            RotateAnimation.RELATIVE_TO_SELF, 0.5f,
            RotateAnimation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 1400
            repeatCount = android.view.animation.Animation.INFINITE
            interpolator = LinearInterpolator()
        }
        icon.startAnimation(rotate)

        return card.also { loadingView = it }
    }


    private fun glassBackground(radius: Float = dp(16).toFloat()) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor("#33FFFFFF".toColorInt())
        setStroke(1, "#66FFFFFF".toColorInt())
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


class AppListAdapter(
    private val activity: ExcludedAppsActivity
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    private var items = listOf<AppInfo>()

    fun submitList(newList: List<AppInfo>) {
        val old = items
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(op: Int, np: Int) =
                old[op].packageName == newList[np].packageName
            override fun areContentsTheSame(op: Int, np: Int) =
                old[op].packageName == newList[np].packageName
        })
        items = newList
        diff.dispatchUpdatesTo(this)
    }

    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor("#33FFFFFF".toColorInt())
            }
            setPadding(dp(16), dp(12), dp(16), dp(12))
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true

            addView(ImageView(activity).apply {
                tag = "icon"
                val size = dp(40)
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(16) }
            })
            addView(TextView(activity).apply {
                tag = "name"
                textSize = 16f
                setTypeface(null, Typeface.NORMAL)
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(CheckBox(activity).apply {
                tag = "checkbox"
                isClickable = false
                isFocusable = false
            })
        }
        return AppViewHolder(row)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) =
        holder.bind(items[position])

    inner class AppViewHolder(private val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        private val iconView = row.findViewWithTag<ImageView>("icon")
        private val nameView = row.findViewWithTag<TextView>("name")
        private val checkBox = row.findViewWithTag<CheckBox>("checkbox")

        fun bind(app: AppInfo) {
            iconView.setImageDrawable(app.icon)
            nameView.text = app.name
            val checked = activity.isExcluded(app.packageName)
            checkBox.isChecked = checked
            applyTint(checked)
            row.setOnClickListener {
                val new = !checkBox.isChecked
                checkBox.isChecked = new
                applyTint(new)
                activity.onItemChecked(app.packageName, new)
            }
        }

        private fun applyTint(checked: Boolean) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                checkBox.buttonTintList = android.content.res.ColorStateList.valueOf(
                    if (checked) "#FFFFFF".toColorInt() else Color.WHITE
                )
            }
        }
    }
}

data class AppInfo(
    val packageName: String,
    val name: String,
    val icon: Drawable
)