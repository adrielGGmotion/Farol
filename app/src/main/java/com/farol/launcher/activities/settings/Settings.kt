/*
 * Copyright (C) 2025 Răzvan Albu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */

package com.farol.launcher.activities.settings

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.app.ActivityOptions
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityOptionsCompat
import androidx.core.widget.NestedScrollView
import androidx.transition.Fade
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.transition.MaterialContainerTransform
import com.bumptech.glide.Glide
import com.farol.launcher.BuildConfig
import com.farol.launcher.R
import com.farol.launcher.Farol
import com.farol.launcher.activities.pages.PageManager
import com.farol.launcher.activities.settings.dialogs.HomeScreenDialog
import com.farol.launcher.activities.settings.dialogs.hide.HideApplicationsDialog
import com.farol.launcher.activities.settings.dialogs.icons.IconsDialog
import com.farol.launcher.activities.settings.dialogs.license.LicensesDialog
import com.farol.launcher.activities.settings.dialogs.search.engine.SearchEngineDialog
import com.farol.launcher.activities.settings.dialogs.search.results.SearchResultsDialog
import com.farol.launcher.activities.settings.dialogs.theme.ThemeDialog
import com.farol.launcher.apps.IconPackManager
import com.farol.launcher.apps.LauncherApplication
import com.farol.launcher.apps.ProfileApplicationManager
import com.farol.launcher.apps.ProfileManager
import com.farol.launcher.preferences.Entry
import com.farol.launcher.preferences.Vibrations
import com.farol.launcher.sheet.drawer.search.SearchEngine
import com.farol.launcher.sheet.drawer.search.SearchFragment
import com.farol.launcher.sheet.drawer.search.recyclers.adapters.WebAdapter
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.ui.Measurements
import com.farol.launcher.ui.dialogs.DialogBackgroundDimmingController
import com.farol.launcher.ui.utils.LayoutSizeObserver
import com.farol.launcher.ui.utils.UiUtils
import androidx.core.net.toUri
import androidx.core.view.isEmpty
import androidx.core.content.edit

open class Settings : ThemedActivity() {
    // Data
    private lateinit var settingsPrefs: SharedPreferences
    private lateinit var searchPrefs: SharedPreferences
    private lateinit var iconsPrefs: SharedPreferences
    private lateinit var themePrefs: SharedPreferences
    private lateinit var powerManager: PowerManager
    private var isBatterySaverOn = false
    private lateinit var resources: Resources

    // Views
    private lateinit var collapsingToolbarLayout: CollapsingToolbarLayout
    private lateinit var scroller: NestedScrollView
    private lateinit var appBar: AppBarLayout
    private lateinit var titleLandscape: TextView
    private lateinit var overviewLandscape: View
    private lateinit var titlePortrait: TextView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var categoryBackCallback: OnBackPressedCallback
    private var content: ViewGroup? = null
    private var selectedCategory = -1
    private var titleCollapseFraction = 0f
    private val categoryIds = intArrayOf(R.id.category_home, R.id.category_drawer, R.id.category_search, R.id.category_general, R.id.category_about)
    private val categoryGroups = intArrayOf(R.id.group_home, R.id.group_drawer, R.id.group_search, R.id.group_general, R.id.group_about)
    private val categoryTitles = intArrayOf(R.string.home_appearance, R.string.drawer, R.string.search, R.string.general_settings, R.string.about_farol)

    // Dynamic views
    private lateinit var lowSpecSwitch: MaterialSwitch
    private lateinit var searchEngineContainer: View
    private lateinit var searchEngineName: TextView
    private lateinit var lowSpecContainer: View
    private lateinit var iconPackName: TextView
    private lateinit var hideCount: TextView

    // Misc
    private lateinit var homeRoleLauncher: ActivityResultLauncher<Intent>
    private var batterySaverReceiver: BroadcastReceiver? = null

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings)
        postponeEnterTransition()

        init()
        initViews()
        setupCategories()
        setupWindowInsets()
        setupLifecycleObservers()

        initGeneralSection()
        initDisplaySection()
        initSearchSection()
        initMiscSection()
        initFooterLinks()

        showCategory(savedInstanceState?.getInt(CATEGORY_STATE, -1) ?: -1, animate = false)
        getRoot()!!.post { startPostponedEnterTransition() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(CATEGORY_STATE, selectedCategory)
        super.onSaveInstanceState(outState)
    }

    protected override fun onDestroy() {
        super.onDestroy()
        batterySaverReceiver?.let { unregisterReceiver(it) }
    }

    override fun onConfigurationChanged(configuration: Configuration) {
        super.onConfigurationChanged(configuration)
        handleOrientation()
    }

    private fun init() {
        resources = getResources()
        val farol: Farol = getApplicationContext()

        settingsPrefs = farol.getSettings()
        iconsPrefs = farol.getSharedPreferences(Entry.ICONS)
        searchPrefs = farol.getSharedPreferences(Entry.SEARCH)
        themePrefs = farol.getSharedPreferences(Entry.THEME)

        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        homeRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                finishAfterTransition()
            }
        }
    }

    private fun initViews() {
        content = findViewById(R.id.content)
        scroller = findViewById(R.id.scroller)
        iconPackName = findViewById(R.id.pack_name)
        hideCount = findViewById(R.id.hidden_count)
        lowSpecSwitch = findViewById(R.id.low_spec)
        searchEngineName = findViewById(R.id.engine_name)
        titleLandscape = findViewById(R.id.title_landscape)
        overviewLandscape = findViewById(R.id.overview_landscape)
        toolbar = findViewById(R.id.toolbar)
        searchEngineContainer = findViewById(R.id.search_engine)
        lowSpecContainer = findViewById(R.id.low_spec_container)
        collapsingToolbarLayout = findViewById(R.id.collapsing_toolbar)

        appBar = findViewById(R.id.app_bar)
        titlePortrait = findViewById(R.id.title_portrait)
        LayoutSizeObserver.attach(titlePortrait, LayoutSizeObserver.HEIGHT, object : LayoutSizeObserver.OnChange {
            override fun onChange(view: View, watchFlags: Int) {
                titlePortrait.setPivotY(titlePortrait.getHeight() / 2f)
            }
        })
        appBar.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            // The text should scale from 30sp to 20sp
            val collapseFraction = -verticalOffset.toFloat() / appBarLayout.getTotalScrollRange()
            val factor = 1f - 0.333f * collapseFraction

            if (!factor.isNaN()) {
                titleCollapseFraction = collapseFraction.coerceIn(0f, 1f)
                titlePortrait.setScaleX(factor)
                titlePortrait.setScaleY(factor)
                updateTitlePortraitMargin()
            }
        }
    }

    private fun setupCategories() {
        val summaries = intArrayOf(R.string.home_category_summary, R.string.drawer_category_summary, R.string.search_category_summary, R.string.general_category_summary, R.string.about_category_summary)
        val icons = intArrayOf(R.drawable.ic_home, R.drawable.ic_hidden, R.drawable.ic_search_engine, R.drawable.ic_settings, R.drawable.ic_farol)
        categoryBackCallback = onBackPressedDispatcher.addCallback(this, false) { showCategory(-1) }
        toolbar.setNavigationOnClickListener { showCategory(-1) }
        titleLandscape.setOnClickListener { showCategory(-1) }
        titleLandscape.compoundDrawablePadding = Measurements.dpToPx(12f)
        titleLandscape.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_back, 0, 0, 0)
        categoryIds.forEachIndexed { index, id ->
            val card = findViewById<View>(id)
            when (index) {
                0 -> card.setBackgroundResource(R.drawable.grouped_category_top)
                categoryIds.lastIndex -> card.setBackgroundResource(R.drawable.grouped_category_bottom)
            }
            card.findViewById<ImageView>(R.id.category_icon).setImageResource(icons[index])
            card.findViewById<TextView>(R.id.category_title).setText(categoryTitles[index])
            card.findViewById<TextView>(R.id.category_summary).setText(summaries[index])
            card.contentDescription = "${getString(categoryTitles[index])}. ${getString(summaries[index])}"
            card.setOnClickListener { showCategory(index) }
        }
        categoryGroups.take(4).forEach { id ->
            val group = findViewById<ViewGroup>(id)
            for (position in 0 until group.childCount) {
                val background = when (position) {
                    0 -> R.drawable.grouped_category_top
                    group.childCount - 1 -> R.drawable.grouped_category_bottom
                    else -> R.drawable.grouped_category_middle
                }
                group.getChildAt(position).setBackgroundResource(background)
            }
        }
    }

    private fun showCategory(index: Int, animate: Boolean = true) {
        val detail = index in categoryGroups.indices
        val previous = selectedCategory
        val pages = findViewById<ViewGroup>(R.id.settings_pages)
        if (animate && previous != index && (previous == -1 || !detail) &&
            pages.isLaidOut && scroller.scrollY == 0 && motionEnabled()
        ) {
            TransitionManager.endTransitions(pages)
            val category = if (detail) index else previous
            val overviewCard = findViewById<View>(categoryIds[category])
            val detailCard = findViewById<ViewGroup>(categoryGroups[category]).getChildAt(0)
            val outer = Measurements.dpToPx(26f).toFloat()
            val inner = Measurements.dpToPx(8f).toFloat()
            fun shape(top: Float, bottom: Float) = ShapeAppearanceModel.builder()
                .setTopLeftCornerSize(top).setTopRightCornerSize(top)
                .setBottomLeftCornerSize(bottom).setBottomRightCornerSize(bottom).build()
            val overviewShape = shape(if (category == 0) outer else inner, if (category == categoryIds.lastIndex) outer else inner)
            val detailShape = if (category == categoryIds.lastIndex) shape(Measurements.dpToPx(32f).toFloat(), Measurements.dpToPx(32f).toFloat()) else shape(outer, inner)
            val transform = MaterialContainerTransform(this, detail).apply {
                startView = if (detail) overviewCard else detailCard
                endView = if (detail) detailCard else overviewCard
                startShapeAppearanceModel = if (detail) overviewShape else detailShape
                endShapeAppearanceModel = if (detail) detailShape else overviewShape
                containerColor = MaterialColors.getColor(pages, com.google.android.material.R.attr.colorSurfaceContainerHighest)
                scrimColor = Color.TRANSPARENT
                fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
                fadeProgressThresholds = MaterialContainerTransform.ProgressThresholds(0f, 1f)
                shapeMaskProgressThresholds = MaterialContainerTransform.ProgressThresholds(0f, 1f)
                transitionDirection = if (detail) MaterialContainerTransform.TRANSITION_DIRECTION_ENTER else MaterialContainerTransform.TRANSITION_DIRECTION_RETURN
                drawingViewId = R.id.scroller
                duration = 380L
                addTarget(pages)
            }
            val fade = Fade().apply {
                duration = 200L
                addTarget(R.id.overview)
                addTarget(R.id.content)
            }
            TransitionManager.beginDelayedTransition(pages, TransitionSet().apply {
                addTransition(transform)
                addTransition(fade)
            })
        }
        selectedCategory = if (detail) index else -1
        content?.visibility = if (detail) View.VISIBLE else View.GONE
        findViewById<View>(R.id.overview).visibility = if (detail) View.GONE else View.VISIBLE
        categoryGroups.forEachIndexed { position, id ->
            findViewById<View>(id).visibility = if (detail && position == index) View.VISIBLE else View.GONE
        }
        val title = if (detail) categoryTitles[index] else R.string.settings
        titlePortrait.setText(title)
        titleLandscape.setText(title)
        titleLandscape.contentDescription = if (detail) getString(R.string.category_back_description, getString(title)) else null
        titleLandscape.isClickable = detail
        titleLandscape.isFocusable = detail
        updateTitlePortraitMargin()
        toolbar.navigationIcon = if (detail) AppCompatResources.getDrawable(this, R.drawable.ic_back) else null
        toolbar.navigationContentDescription = if (detail) getString(R.string.return_button) else null
        categoryBackCallback.isEnabled = detail
        handleOrientation()
        if (index == categoryGroups.lastIndex || previous == categoryGroups.lastIndex) {
            appBar.setExpanded(index != categoryGroups.lastIndex, false)
        }
        if (animate) findViewById<View>(R.id.container).announceForAccessibility(getString(title))
        scroller.post {
            scroller.scrollTo(0, 0)
            if (selectedCategory == categoryGroups.lastIndex && index != previous) {
                findViewById<FarolBeaconView>(R.id.about_beacon).illuminate(animate && motionEnabled())
            }
        }
    }

    private fun motionEnabled() = ValueAnimator.areAnimatorsEnabled() &&
        !powerManager.isPowerSaveMode &&
        !settingsPrefs.getBoolean(DialogBackgroundDimmingController.LOW_SPEC_KEY, false)

    private fun updateTitlePortraitMargin() {
        val titleParams = titlePortrait.layoutParams as ViewGroup.MarginLayoutParams
        val start = if (selectedCategory == -1) 30f else 30f + 42f * titleCollapseFraction
        titleParams.marginStart = Measurements.dpToPx(start)
        titlePortrait.layoutParams = titleParams
    }

    private fun setupWindowInsets() {
        val container = findViewById<View>(R.id.container)
        Measurements.addStatusBarListener { value ->
            container.setPadding(
                container.getPaddingLeft(),
                value!!,
                container.getPaddingRight(),
                Measurements.getNavHeight()
            )
        }

        Measurements.addNavListener { value ->
            container.setPadding(
                container.getPaddingLeft(),
                Measurements.getSysUIHeight(),
                container.getPaddingRight(),
                value!!
            )
        }

        UiUtils.Notch.applyNotchMargin(
            findViewById(R.id.coordinator),
            UiUtils.Notch.Treatment.CENTER
        )
    }

    private fun setupLifecycleObservers() {
        isBatterySaverOn = powerManager.isPowerSaveMode
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                isBatterySaverOn = powerManager.isPowerSaveMode
                updateLowSpecState()
                if (isBatterySaverOn) findViewById<FarolBeaconView>(R.id.about_beacon).settle()
            }
        }
        batterySaverReceiver = receiver

        registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
    }

    // Settings
    private fun initGeneralSection() {
        // Home Screen
        findViewById<View>(R.id.home).setOnClickListener(object : View.OnClickListener {
            private var dialog: HomeScreenDialog? = null
            private var showing = false

            override fun onClick(view: View) {
                val currentDialog = dialog ?: HomeScreenDialog(this@Settings).also { dialog = it }
                currentDialog.setOnDismissListener { showing = false }

                if (!showing) {
                    currentDialog.show()
                    showing = true
                }
            }
        })

        // Page Manager
        findViewById<View>(R.id.pages).setOnClickListener {
            startActivity(
                Intent(this, PageManager::class.java),
                ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
            )
        }

        // Theme
        val themeName = findViewById<TextView>(R.id.theme_name)
        themeName.setText(getThemeType().getDisplayName())

        if (themePrefs.getBoolean(FORCE_DARK, false)) {
            themeName.append(" " + resources.getString(R.string.dark))
        }

        findViewById<View>(R.id.theme).setOnClickListener(object : View.OnClickListener {
            private var dialog: ThemeDialog? = null
            private var showing = false

            override fun onClick(view: View) {
                val currentDialog = dialog ?: ThemeDialog(this@Settings).also {
                    dialog = it
                    it.setOnDismissListener(ThemeDialog.OnDismissListener { stateChanged ->
                        showing = false

                        if (stateChanged) {
                            restart()
                        }
                    })
                }

                if (!showing) {
                    currentDialog.show()
                    showing = true
                }
            }
        })
    }

    private fun initDisplaySection() {
        // Hidden Apps
        updateHiddenAppsCount()
        findViewById<View>(R.id.hidden_apps).setOnClickListener(object : View.OnClickListener {
            private var dialog: HideApplicationsDialog? = null
            private var showing = false

            override fun onClick(view: View) {
                val currentDialog = dialog ?: HideApplicationsDialog().also {
                    dialog = it
                    it.setOnHideListener(HideApplicationsDialog.OnHideListener {
                        updateHiddenAppsCount()
                        showing = false
                    })
                }

                if (!showing) {
                    if (!currentDialog.isAdded) {
                        currentDialog.show(getSupportFragmentManager(), "HideApplications")
                    } else {
                        currentDialog.show()
                    }

                    showing = true
                }
            }
        })

        // Icons
        updateIconPackName()
        findViewById<View>(R.id.icons).setOnClickListener(object : View.OnClickListener {
            private var dialog: IconsDialog? = null
            private var showing = false

            override fun onClick(view: View) {
                val currentDialog = dialog ?: IconsDialog(this@Settings).also {
                    dialog = it
                    it.setOnDismissListener {
                        updateIconPackName()
                        showing = false
                    }
                }

                if (!showing) {
                    currentDialog.show()
                    showing = true
                }
            }
        })
    }

    private fun initSearchSection() {
        updateEngineName()

        // Search Engine
        searchEngineContainer.setOnClickListener(object : View.OnClickListener {
            private var dialog: SearchEngineDialog? = null
            private var showing = false

            override fun onClick(view: View) {
                val currentDialog = dialog ?: SearchEngineDialog(this@Settings).also {
                    dialog = it
                    it.setOnDismissListener {
                        updateEngineName()
                        showing = false
                    }
                }

                if (!showing) {
                    currentDialog.show()
                    showing = true
                }
            }
        })

        // Search Results
        val searchResultsContainer = findViewById<View>(R.id.search_results_container)
        val resultsSwitch = findViewById<MaterialSwitch>(R.id.search_results)

        searchResultsContainer.setOnClickListener(object : View.OnClickListener {
            private var dialog: SearchResultsDialog? = null
            private var showing = false

            override fun onClick(view: View) {
                val currentDialog = dialog ?: SearchResultsDialog(this@Settings).also {
                    dialog = it
                    it.setStatusListener(SearchResultsDialog.StatusListener { enabled ->
                        resultsSwitch.setChecked(enabled)
                    })
                    it.setOnDismissListener { showing = false }
                }

                if (!showing) {
                    currentDialog.show()
                    showing = true
                }
            }
        })

        setupSwitch(resultsSwitch, searchPrefs.getBoolean(WebAdapter.SEARCH_RESULTS, false)) { button, checked ->
            val key = searchPrefs.getString(WebAdapter.KAGI_API_KEY, null)
            if (checked && (key == null || key.isEmpty())) {
                searchResultsContainer.performClick()
                button.setChecked(false)
            } else {
                searchPrefs.edit {
                        putBoolean(WebAdapter.SEARCH_RESULTS, checked)
                    }
                updateEngineName()
            }
        }

        // Search Hidden Apps
        setupSwitch(
            findViewById<MaterialSwitch>(R.id.search_hidden_apps),
            findViewById(R.id.search_hidden_apps_container),
            searchPrefs.getBoolean(SearchFragment.SEARCH_HIDDEN_APPS, false)
        ) { _, checked ->
            searchPrefs.edit {
                    putBoolean(SearchFragment.SEARCH_HIDDEN_APPS, checked)
                }
        }
    }

    private fun initMiscSection() {
        updateLowSpecState()
        lowSpecSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatterySaverOn) {
                settingsPrefs.edit {
                        putBoolean(DialogBackgroundDimmingController.LOW_SPEC_KEY, isChecked)
                    }
            }
        }
        lowSpecSwitch.jumpDrawablesToCurrentState()

        setupSwitch(
            findViewById<MaterialSwitch>(R.id.vibrations),
            findViewById(R.id.vibrations_container),
            settingsPrefs.getBoolean(Vibrations.PREFERENCE_ENTRY, true)
        ) { _, checked ->
            settingsPrefs.edit {
                    putBoolean(Vibrations.PREFERENCE_ENTRY, checked)
                }
        }

        findViewById<View>(R.id.restart).setOnClickListener { restart() }
        findViewById<View>(R.id.def_launcher).setOnClickListener { requestDefaultLauncherRole() }
    }

    private fun initFooterLinks() {
        findViewById<FarolBeaconView>(R.id.about_beacon).setOnClickListener {
            (it as FarolBeaconView).illuminate(motionEnabled())
        }
        findViewById<TextView>(R.id.version).text = getString(R.string.farol_version, BuildConfig.VERSION_NAME)
        Glide.with(this)
            .load("https://avatars.githubusercontent.com/u/200536612?v=4&s=192")
            .placeholder(R.drawable.ic_farol)
            .error(R.drawable.ic_farol)
            .into(findViewById(R.id.creator_avatar))

        findViewById<View>(R.id.app_info_action).setOnClickListener {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.setData(("package:" + getPackageName()).toUri())
            startActivity(intent)
        }

        findViewById<View>(R.id.licenses).setOnClickListener(object : View.OnClickListener {
            private var dialog: LicensesDialog? = null
            private var showing = false

            override fun onClick(view: View) {
                val currentDialog = dialog ?: LicensesDialog(this@Settings).also {
                    dialog = it
                    it.setOnDismissListener { showing = false }
                }

                if (!showing) {
                    currentDialog.show()
                    showing = true
                }
            }
        })

        setupUrlButton(R.id.github, "https://github.com/adrielGGmotion/Farol")
        setupUrlButton(R.id.creator_profile, "https://github.com/adrielGGmotion")
        setupUrlButton(R.id.creator_github, "https://github.com/adrielGGmotion")
        setupUrlButton(R.id.creator_telegram, "https://t.me/adrielggmotion")
        setupUrlButton(R.id.creator_instagram, "https://instagram.com/adrielggmotion")
        setupUrlButton(R.id.creator_website, "https://adriel.men")
    }

    // Helpers
    private fun setupSwitch(
        switchView: MaterialSwitch,
        defaultValue: Boolean,
        listener: CompoundButton.OnCheckedChangeListener
    ) {
        setupSwitch(switchView, null, defaultValue, listener)
    }

    private fun setupSwitch(
        switchView: MaterialSwitch,
        container: View?,
        defaultValue: Boolean,
        listener: CompoundButton.OnCheckedChangeListener
    ) {
        switchView.setChecked(defaultValue)
        switchView.jumpDrawablesToCurrentState()
        switchView.setOnCheckedChangeListener(listener)

        if (container != null) {
            container.setOnClickListener { switchView.performClick() }
        }
    }

    private fun setupUrlButton(viewId: Int, url: String) {
        findViewById<View>(viewId).setOnClickListener { view ->
            if (motionEnabled()) view.startAnimation(AnimationUtils.loadAnimation(this, R.anim.bounce_small))
            try {
                startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            } catch (error: ActivityNotFoundException) {
                Toast.makeText(this, R.string.cannot_open_link, Toast.LENGTH_SHORT).show()
            } catch (error: SecurityException) {
                Toast.makeText(this, R.string.cannot_open_link, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestDefaultLauncherRole() {
        val roleManager = getSystemService(RoleManager::class.java) as RoleManager

        if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                homeRoleLauncher.launch(intent, ActivityOptionsCompat.makeBasic())
            } else {
                val intent = Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }
    }

    private fun updateLowSpecState() {
        lowSpecContainer.setAlpha(if (isBatterySaverOn) 0.6f else 1f)

        if (isBatterySaverOn) {
            lowSpecContainer.setOnClickListener(null)
            lowSpecSwitch.setChecked(true)
        } else {
            lowSpecContainer.setOnClickListener { lowSpecSwitch.performClick() }
            lowSpecSwitch.setChecked(
                settingsPrefs.getBoolean(DialogBackgroundDimmingController.LOW_SPEC_KEY, false)
            )
        }
    }

    private fun updateEngineName() {
        searchEngineName.setText(SearchEngine.getEngine(getApplicationContext()).toString())

        val resultsEnabled = searchPrefs.getBoolean(WebAdapter.SEARCH_RESULTS, false)
        searchEngineContainer.setEnabled(!resultsEnabled)
        searchEngineContainer.setAlpha(if (!resultsEnabled) 1f else 0.6f)
    }

    private fun updateIconPackName() {
        val packPackageName = iconsPrefs.getString(IconPackManager.ICON_PACK_ENTRY, null)

        if (packPackageName != null) {
            val iconPackApp: LauncherApplication? = ProfileManager.getInstance().getApplication(packPackageName)

            if (iconPackApp != null && iconPackApp != LauncherApplication.FALLBACK_APP) {
                iconPackName.setText(iconPackApp.getLabel())
                return
            }
        }

        iconPackName.setText(R.string.default_text)
    }

    @SuppressLint("SetTextI18n")
    private fun updateHiddenAppsCount() {
        var count = 0

        for (manager: ProfileApplicationManager in ProfileManager.getInstance().getProfiles()) {
            count += manager.getActualSize() - manager.getSize()
        }

        hideCount.setText(resources.getString(R.string.hidden_apps) + ": " + count)
    }

    // Utils
    private fun handleOrientation() {
        scroller.stopNestedScroll()
        scroller.setNestedScrollingEnabled(false)

        val params = collapsingToolbarLayout.getLayoutParams() as AppBarLayout.LayoutParams

        if (Measurements.isLandscape()) {
            params.height = 0
            params.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL)

            collapsingToolbarLayout.setTitleEnabled(false)
            titleLandscape.setVisibility(if (content?.visibility == View.VISIBLE) View.VISIBLE else View.GONE)
            overviewLandscape.setVisibility(if (content?.visibility == View.VISIBLE) View.GONE else View.VISIBLE)
        } else {
            params.height = Measurements.dpToPx(Measurements.HEADER_SIZE_DP.toFloat())
            params.setScrollFlags(
                AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                    AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED or
                    AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
            )

            collapsingToolbarLayout.setTitleEnabled(true)
            titleLandscape.setVisibility(View.GONE)
            overviewLandscape.setVisibility(View.GONE)
        }

        collapsingToolbarLayout.setLayoutParams(params)

        scroller.setNestedScrollingEnabled(true)
        scroller.setScrollY(0)
    }

    private fun restart() {
        val packageManager = getPackageManager()
        val intent = packageManager.getLaunchIntentForPackage(BuildConfig.APPLICATION_ID)

        if (intent != null) {
            val componentName: ComponentName? = intent.getComponent()
            val mainIntent = Intent.makeRestartActivityTask(componentName!!)
            mainIntent.setPackage(BuildConfig.APPLICATION_ID)

            startActivity(mainIntent)
            System.exit(0)
        }
    }

    protected override fun isOpaque(): Boolean = true

    protected override fun isAffectedByBackGesture(): Boolean = true

    private companion object {
        const val CATEGORY_STATE = "category"
    }
}
