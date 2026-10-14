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

package com.farol.launcher.activities.settings.dialogs

import android.Manifest
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.farol.launcher.R
import com.farol.launcher.Farol
import com.farol.launcher.activities.launcher.widgets.ClockWidget
import com.farol.launcher.activities.launcher.widgets.SearchWidget
import com.farol.launcher.activities.launcher.widgets.glance.extensions.media.Media
import com.farol.launcher.activities.launcher.widgets.glance.extensions.weather.Weather
import com.farol.launcher.activities.launcher.widgets.home.HomeWidgets
import com.farol.launcher.activities.launcher.widgets.pins.PinnedCategory
import com.farol.launcher.activities.settings.dialogs.location.LocationDialog
import com.farol.launcher.activities.settings.dialogs.pin.PinnedCategoryDialog
import com.farol.launcher.apps.CategoryManager
import com.farol.launcher.preferences.Entry
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.ui.common.StylizedClockView
import com.farol.launcher.ui.common.lock.LockDetector
import com.farol.launcher.ui.dialogs.ActionDialog
import com.farol.launcher.utils.Utils
import java.util.UUID
import androidx.core.content.edit

open class HomeScreenDialog(activity: ThemedActivity) : ActionDialog(activity) {
    private lateinit var scroller: NestedScrollView
    private lateinit var root: ViewGroup

    // Data
    private lateinit var pinsPrefs: SharedPreferences
    private lateinit var clockPrefs: SharedPreferences
    private lateinit var weatherPrefs: SharedPreferences
    private lateinit var settingsPrefs: SharedPreferences
    private lateinit var resources: Resources

    // Dynamic views
    private lateinit var pinnedCategorySwitch: MaterialSwitch
    private lateinit var lockAnimSwitch: MaterialSwitch
    private lateinit var pinnedCategoryName: TextView
    private lateinit var mediaSwitch: MaterialSwitch
    private lateinit var lockSwitch: MaterialSwitch
    private lateinit var lockAnimContainer: View

    @SuppressLint("ClickableViewAccessibility")
    override fun inflateContent(inflater: LayoutInflater, parent: android.view.ViewGroup): View {
        root = inflater.inflate(R.layout.pop_up_home, parent, false) as ViewGroup

        resources = activity.getResources()
        val farol: Farol = activity.getApplicationContext()

        pinsPrefs = farol.getSharedPreferences(Entry.PINNED_CATEGORY)
        weatherPrefs = farol.getSharedPreferences(Entry.WEATHER)
        clockPrefs = farol.getSharedPreferences(Entry.CLOCK)
        settingsPrefs = farol.getSettings()

        pinnedCategoryName = root.findViewById(R.id.pinned_category_name)
        pinnedCategorySwitch = root.findViewById(R.id.pinned_category)
        lockAnimContainer = root.findViewById(R.id.lock_animation_container)
        lockAnimSwitch = root.findViewById(R.id.lock_animation)
        mediaSwitch = root.findViewById(R.id.media)
        scroller = root.findViewById(R.id.scroller)
        lockSwitch = root.findViewById(R.id.lock)

        initGeneralSection()
        initClockSection()
        initWeatherSection()
        initGestureSection()

        return root
    }

    private fun initGeneralSection() {
        // Media Player Switch
        setupSwitch(
            mediaSwitch,
            root.findViewById(R.id.media_container),
            settingsPrefs.getBoolean(Media.PREFERENCE_ENTRY, false)
        ) { _, checked ->
            settingsPrefs.edit {
                    putBoolean(Media.PREFERENCE_ENTRY, checked)
                }

            if (checked && !Utils.isNotificationServiceEnabled(activity)) {
                showNotificationPermissionDialog()
            }
        }

        // Search
        setupSwitch(
            root.findViewById<MaterialSwitch>(R.id.search),
            root.findViewById(R.id.search_container),
            settingsPrefs.getBoolean(SearchWidget.SEARCH_WIDGET_KEY, true)
        ) { _, checked ->
            settingsPrefs.edit {
                    putBoolean(SearchWidget.SEARCH_WIDGET_KEY, checked)
                }
        }

        // Widgets: let every widget be resized, whatever the provider declares
        setupSwitch(
            root.findViewById<MaterialSwitch>(R.id.force_resize),
            root.findViewById(R.id.force_resize_container),
            settingsPrefs.getBoolean(HomeWidgets.FORCE_RESIZE_KEY, true)
        ) { _, checked ->
            settingsPrefs.edit {
                    putBoolean(HomeWidgets.FORCE_RESIZE_KEY, checked)
                }
        }

        // Pinned Category
        val pinnedCategoryContainer = root.findViewById<View>(R.id.pinned_category_container)

        updatePinnedCategoryName()
        pinnedCategorySwitch.setChecked(pinsPrefs.getBoolean(PinnedCategory.PINNED_CATEGORY_VISIBLE, false))
        pinnedCategorySwitch.jumpDrawablesToCurrentState()

        pinnedCategorySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isPinnedCategoryValid()) {
                pinnedCategorySwitch.setChecked(false)
                pinnedCategoryContainer.performClick()
            } else {
                pinsPrefs.edit {
                        putBoolean(PinnedCategory.PINNED_CATEGORY_VISIBLE, isChecked)
                    }
            }
        }

        pinnedCategoryContainer.setOnClickListener(object : View.OnClickListener {
            private var dialog: PinnedCategoryDialog? = null
            private var showing = false

            override fun onClick(view: View) {
                val currentDialog = dialog ?: PinnedCategoryDialog(
                    activity,
                    pinsPrefs,
                    PinnedCategoryDialog.OnCheckedChangeListener { isChecked ->
                        pinnedCategorySwitch.setChecked(isChecked)
                        isPinnedCategoryValid() && isChecked
                    }
                ).also {
                    dialog = it
                    it.setOnDismissListener {
                        updatePinnedCategoryName()
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

    private fun initClockSection() {
        setupSwitch(
            root.findViewById<MaterialSwitch>(R.id.clock),
            root.findViewById(R.id.clock_container),
            settingsPrefs.getBoolean(ClockWidget.CLOCK_WIDGET_KEY, true)
        ) { _, checked ->
            settingsPrefs.edit {
                    putBoolean(ClockWidget.CLOCK_WIDGET_KEY, checked)
                }
        }

        val slider = root.findViewById<Slider>(R.id.background_slider)

        slider.setValueFrom(0f)
        slider.setValueTo(255f)
        slider.setStepSize(1f)
        slider.setValue(clockPrefs.getInt(StylizedClockView.BACKGROUND_ALPHA_KEY, 0).toFloat())
        slider.addOnChangeListener { _, value, _ ->
            clockPrefs.edit {
                    putInt(StylizedClockView.BACKGROUND_ALPHA_KEY, value.toInt())
                }
        }

        setupSwitch(
            root.findViewById<MaterialSwitch>(R.id.imperial_clock),
            root.findViewById(R.id.imperial_clock_container),
            clockPrefs.getBoolean(StylizedClockView.IMPERIAL_KEY, Utils.isSystemUsingImperial(activity))
        ) { _, checked ->
            clockPrefs.edit {
                    putBoolean(StylizedClockView.IMPERIAL_KEY, checked)
                }
        }
    }

    private fun initWeatherSection() {
        // Weather
        setupSwitch(
            root.findViewById<MaterialSwitch>(R.id.weather),
            root.findViewById(R.id.weather_container),
            weatherPrefs.getBoolean(Weather.FORECAST_KEY, true)
        ) { _, checked ->
            weatherPrefs.edit {
                    putBoolean(Weather.FORECAST_KEY, checked)
                }

        }

        // Location
        val locationName = root.findViewById<TextView>(R.id.location_name)
        locationName.setText(getLocationString())

        root.findViewById<View>(R.id.location).setOnClickListener(object : View.OnClickListener {
            private var dialog: LocationDialog? = null
            private var showing = false

            override fun onClick(view: View) {
                val currentDialog = dialog ?: LocationDialog(activity).also {
                    dialog = it
                    it.setOnLocationUpdateListener(LocationDialog.OnLocationUpdate {
                        locationName.setText(getLocationString())
                    })
                    it.setOnDismissListener {
                        showing = false
                    }
                }

                if (!showing) {
                    currentDialog.show()
                    showing = true
                }
            }
        })

        setupSwitch(
            root.findViewById<MaterialSwitch>(R.id.imperial_weather),
            root.findViewById(R.id.imperial_weather_container),
            weatherPrefs.getBoolean(Weather.IMPERIAL_KEY, Utils.isSystemUsingImperial(activity))
        ) { _, checked ->
            weatherPrefs.edit {
                    putBoolean(Weather.IMPERIAL_KEY, checked)
                }
        }
    }

    private fun initGestureSection() {
        // Double Tap Lock Switch
        setupSwitch(
            lockSwitch,
            root.findViewById(R.id.lock_container),
            settingsPrefs.getBoolean(LockDetector.PREFERENCE_ENTRY, false)
        ) { _, checked ->
            settingsPrefs.edit {
                    putBoolean(LockDetector.PREFERENCE_ENTRY, checked)
                }

            if (checked && !Utils.isAccessibilityServiceEnabled(activity)) {
                showAccessibilityPermissionDialog()
            }

            updateLockAnimationState(checked)
        }

        // Legacy Lock Animation
        setupSwitch(
            lockAnimSwitch,
            root.findViewById(R.id.lock_animation_container),
            settingsPrefs.getBoolean(LockDetector.LEGACY_ANIMATION, false)
        ) { _, checked ->
            settingsPrefs.edit {
                    putBoolean(LockDetector.LEGACY_ANIMATION, checked)
                }
        }
        updateLockAnimationState(settingsPrefs.getBoolean(LockDetector.PREFERENCE_ENTRY, false))
    }

    override fun show() {
        super.show()

        checkNotificationPermission()
        checkAccessibilityPermission()

        scroller.scrollTo(0, 0)
    }

    private fun checkNotificationPermission() {
        if (!Utils.isNotificationServiceEnabled(activity)) {
            mediaSwitch.setChecked(false)
        }
    }

    private fun checkAccessibilityPermission() {
        if (!Utils.isAccessibilityServiceEnabled(activity)) {
            lockSwitch.setChecked(false)
        }
    }

    private fun isPinnedCategoryValid(): Boolean {
        val identifier = pinsPrefs.getString(PinnedCategory.PINNED_CATEGORY, null)
        if (identifier == null) {
            return false
        }

        return try {
            CategoryManager.getInstance().get(UUID.fromString(identifier)) != null
        } catch (exception: IllegalArgumentException) {
            false
        }
    }

    private fun updateLockAnimationState(enabled: Boolean) {
        lockAnimContainer.setAlpha(if (enabled) 1f else 0.6f)

        if (enabled) {
            lockAnimContainer.setOnClickListener { lockAnimSwitch.performClick() }
        } else {
            lockAnimContainer.setOnClickListener(null)
        }
    }

    private fun updatePinnedCategoryName() {
        if (pinsPrefs.contains(PinnedCategory.PINNED_CATEGORY)) {
            try {
                pinnedCategoryName.setText(
                    CategoryManager.getInstance().getCategoryName(
                        UUID.fromString(pinsPrefs.getString(PinnedCategory.PINNED_CATEGORY, ""))
                    )
                )
                pinnedCategoryName.setVisibility(View.VISIBLE)
            } catch (exception: IllegalArgumentException) {
                pinnedCategoryName.setVisibility(View.GONE)
            } catch (exception: NullPointerException) {
                pinnedCategoryName.setVisibility(View.GONE)
            }
        } else {
            pinnedCategoryName.setVisibility(View.GONE)
        }
    }

    private fun getLocationString(): String? {
        var location = weatherPrefs.getString(Weather.LOCATION_NAME, resources.getString(R.string.location_ip_based))

        if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            weatherPrefs.getBoolean(Weather.PRECISE_LOCATION, false)
        ) {
            location = resources.getString(R.string.precise_location)
        }

        return location
    }

    // Permission Dialogs
    private fun showNotificationPermissionDialog() {
        val dialog = NotificationConfigurator(activity)
        dialog.setOnDismissListener { checkNotificationPermission() }
        dialog.show()
    }

    private fun showAccessibilityPermissionDialog() {
        val dialog = AccessibilityConfigurator(activity)
        dialog.setOnDismissListener { checkAccessibilityPermission() }
        dialog.show()
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

    override fun blurBehind(): Boolean = true

    override fun getDesiredInitialState(): Int = BottomSheetBehavior.STATE_HALF_EXPANDED
}
