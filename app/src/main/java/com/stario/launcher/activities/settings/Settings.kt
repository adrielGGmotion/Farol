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

package com.stario.launcher.activities.settings

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.app.Activity
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
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.CompoundButton
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.materialswitch.MaterialSwitch
import com.stario.launcher.BuildConfig
import com.stario.launcher.R
import com.stario.launcher.Stario
import com.stario.launcher.activities.pages.PageManager
import com.stario.launcher.activities.settings.dialogs.HomeScreenDialog
import com.stario.launcher.activities.settings.dialogs.hide.HideApplicationsDialog
import com.stario.launcher.activities.settings.dialogs.icons.IconsDialog
import com.stario.launcher.activities.settings.dialogs.license.LicensesDialog
import com.stario.launcher.activities.settings.dialogs.search.engine.SearchEngineDialog
import com.stario.launcher.activities.settings.dialogs.search.results.SearchResultsDialog
import com.stario.launcher.activities.settings.dialogs.theme.ThemeDialog
import com.stario.launcher.apps.IconPackManager
import com.stario.launcher.apps.LauncherApplication
import com.stario.launcher.apps.ProfileApplicationManager
import com.stario.launcher.apps.ProfileManager
import com.stario.launcher.preferences.Entry
import com.stario.launcher.preferences.Vibrations
import com.stario.launcher.sheet.drawer.search.SearchEngine
import com.stario.launcher.sheet.drawer.search.SearchFragment
import com.stario.launcher.sheet.drawer.search.recyclers.adapters.WebAdapter
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.Measurements
import com.stario.launcher.ui.dialogs.DialogBackgroundDimmingController
import com.stario.launcher.ui.utils.LayoutSizeObserver
import com.stario.launcher.ui.utils.UiUtils

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
    private lateinit var titleLandscape: View
    private var content: ViewGroup? = null

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
        setupWindowInsets()
        setupLifecycleObservers()

        initGeneralSection()
        initDisplaySection()
        initSearchSection()
        initMiscSection()
        initFooterLinks()

        handleOrientation()
        getRoot()!!.post { startPostponedEnterTransition() }
    }

    protected override fun onDestroy() {
        super.onDestroy()
        batterySaverReceiver?.let { unregisterReceiver(it) }
    }

    override fun onConfigurationChanged(configuration: Configuration) {
        // Prevent layout transition glitches during rotation
        content?.setLayoutTransition(null)

        super.onConfigurationChanged(configuration)

        handleOrientation()
        val currentContent = content
        if (currentContent != null) {
            currentContent.post { currentContent.setLayoutTransition(LayoutTransition()) }
        }
    }

    private fun init() {
        resources = getResources()
        val stario: Stario = getApplicationContext()

        settingsPrefs = stario.getSettings()
        iconsPrefs = stario.getSharedPreferences(Entry.ICONS)
        searchPrefs = stario.getSharedPreferences(Entry.SEARCH)
        themePrefs = stario.getSharedPreferences(Entry.THEME)

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
        searchEngineContainer = findViewById(R.id.search_engine)
        lowSpecContainer = findViewById(R.id.low_spec_container)
        collapsingToolbarLayout = findViewById(R.id.collapsing_toolbar)

        appBar = findViewById(R.id.app_bar)
        val titlePortrait = findViewById<TextView>(R.id.title_portrait)
        LayoutSizeObserver.attach(titlePortrait, LayoutSizeObserver.HEIGHT, object : LayoutSizeObserver.OnChange {
            override fun onChange(view: View, watchFlags: Int) {
                titlePortrait.setPivotY(titlePortrait.getHeight() / 2f)
            }
        })
        appBar.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            // The text should scale from 30sp to 20sp
            var factor = -verticalOffset.toFloat() / appBarLayout.getTotalScrollRange()
            factor = 1f - 0.333f * factor

            if (!factor.isNaN()) {
                titlePortrait.setScaleX(factor)
                titlePortrait.setScaleY(factor)
            }
        }
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
                searchPrefs.edit()
                    .putBoolean(WebAdapter.SEARCH_RESULTS, checked)
                    .apply()
                updateEngineName()
            }
        }

        // Search Hidden Apps
        setupSwitch(
            findViewById<MaterialSwitch>(R.id.search_hidden_apps),
            findViewById(R.id.search_hidden_apps_container),
            searchPrefs.getBoolean(SearchFragment.SEARCH_HIDDEN_APPS, false)
        ) { _, checked ->
            searchPrefs.edit()
                .putBoolean(SearchFragment.SEARCH_HIDDEN_APPS, checked)
                .apply()
        }
    }

    private fun initMiscSection() {
        updateLowSpecState()
        lowSpecSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isBatterySaverOn) {
                settingsPrefs.edit()
                    .putBoolean(DialogBackgroundDimmingController.LOW_SPEC_KEY, isChecked)
                    .apply()
            }
        }
        lowSpecSwitch.jumpDrawablesToCurrentState()

        setupSwitch(
            findViewById<MaterialSwitch>(R.id.vibrations),
            findViewById(R.id.vibrations_container),
            settingsPrefs.getBoolean(Vibrations.PREFERENCE_ENTRY, true)
        ) { _, checked ->
            settingsPrefs.edit()
                .putBoolean(Vibrations.PREFERENCE_ENTRY, checked)
                .apply()
        }

        findViewById<View>(R.id.restart).setOnClickListener { restart() }
        findViewById<View>(R.id.def_launcher).setOnClickListener { requestDefaultLauncherRole() }
    }

    private fun initFooterLinks() {
        //noinspection SetTextI18n
        findViewById<TextView>(R.id.version).setText(BuildConfig.VERSION_NAME + " • Răzvan Albu")

        findViewById<View>(R.id.about).setOnClickListener {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.setData(Uri.parse("package:" + getPackageName()))
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

        setupUrlButton(R.id.github, "https://github.com/albu-razvan/Stario")
        setupUrlButton(R.id.website, "https://www.razvanalbu.com")
        setupUrlButton(R.id.discord, "https://discord.gg/WuVapMt9gY")
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
            view.startAnimation(AnimationUtils.loadAnimation(this, R.anim.bounce_small))
            startActivity(Intent(Intent.ACTION_DEFAULT, Uri.parse(url)))
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
            titleLandscape.setVisibility(View.VISIBLE)
        } else {
            params.height = Measurements.dpToPx(Measurements.HEADER_SIZE_DP.toFloat())
            params.setScrollFlags(
                AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                    AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED or
                    AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
            )

            collapsingToolbarLayout.setTitleEnabled(true)
            titleLandscape.setVisibility(View.GONE)
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
}
