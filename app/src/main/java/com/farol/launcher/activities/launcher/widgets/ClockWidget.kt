/*
 * Copyright (C) 2026 Răzvan Albu
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

package com.farol.launcher.activities.launcher.widgets

import android.app.ActivityOptions
import android.content.Intent
import android.content.SharedPreferences
import android.provider.AlarmClock
import android.view.View
import com.farol.launcher.R
import com.farol.launcher.preferences.Vibrations
import com.farol.launcher.sheet.SheetsFocusController
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.ui.common.grid.DraggableGridItem
import com.farol.launcher.ui.common.grid.DynamicGridLayout
import com.farol.launcher.ui.utils.UiUtils

open class ClockWidget(private val activity: ThemedActivity) {
    private val preferences: SharedPreferences = activity.applicationContext.getSettings()
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var layoutData: DynamicGridLayout.ItemLayoutData? = null
    private var gridItem: DraggableGridItem? = null
    private var isAttached = false

    fun attach(container: DynamicGridLayout) {
        if (gridItem != null) {
            return
        }

        gridItem = DraggableGridItem(activity)
        gridItem?.itemId = CLOCK_TAG

        layoutData = DynamicGridLayout.ItemLayoutData(CLOCK_TAG, 0, 0, 2, 2)
        layoutData?.minColSpan = 2
        layoutData?.minRowSpan = 1

        val root = activity.layoutInflater.inflate(R.layout.home_clock, gridItem, false)
        root.isHapticFeedbackEnabled = false
        root.setOnTouchListener(SheetsFocusController.createClickTouchListener { _ ->
            Vibrations.getInstance().vibrate()

            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
            if (intent.resolveActivity(activity.packageManager) != null) {
                activity.startActivity(
                    intent,
                    ActivityOptions.makeScaleUpAnimation(
                        root,
                        0,
                        0,
                        root.measuredWidth,
                        root.measuredHeight
                    ).toBundle()
                )
            }
        })

        gridItem?.addView(root)

        listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (CLOCK_WIDGET_KEY == key) {
                updateContainerState(container, sharedPreferences.getBoolean(CLOCK_WIDGET_KEY, true))
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)

        updateContainerState(container, preferences.getBoolean(CLOCK_WIDGET_KEY, true))
    }

    private fun updateContainerState(container: DynamicGridLayout, shouldBeVisible: Boolean) {
        UiUtils.post {
            val item = gridItem
            val data = layoutData
            if (shouldBeVisible && !isAttached && item != null && data != null) {
                container.addItem(item, data)
                isAttached = true
            } else if (!shouldBeVisible && isAttached) {
                container.removeItem(item)
                isAttached = false
            }
        }
    }

    fun detach() {
        listener?.let { preferences.unregisterOnSharedPreferenceChangeListener(it) }
    }

    companion object {
        const val CLOCK_WIDGET_KEY = "com.stario.HOMESCREEN_CLOCK_WIDGET_VISIBLE"
        private const val CLOCK_TAG = "StylizedClockGlance"
    }
}
