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

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.SharedPreferences
import android.view.View
import android.view.animation.LinearInterpolator
import com.farol.launcher.R
import com.farol.launcher.preferences.Vibrations
import com.farol.launcher.sheet.SheetsFocusController
import com.farol.launcher.sheet.drawer.dialog.ApplicationsDialog
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.ui.common.grid.DraggableGridItem
import com.farol.launcher.ui.common.grid.DynamicGridLayout
import com.farol.launcher.ui.common.grid.GridItemVisibility
import com.farol.launcher.ui.utils.LayoutSizeObserver

open class SearchWidget(private val activity: ThemedActivity) {
    private val preferences: SharedPreferences = activity.applicationContext.getSettings()
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun attach(container: DynamicGridLayout) {
        if (listener != null) {
            return
        }

        val item = DraggableGridItem(activity)
        item.itemId = SEARCH_TAG

        val layoutData = DynamicGridLayout.ItemLayoutData(SEARCH_TAG, 0, 0, 1, 1)
        layoutData.maxColSpan = 1
        layoutData.maxRowSpan = 1

        val root = activity.layoutInflater.inflate(R.layout.home_search, item, false)

        val background = root.findViewById<View>(R.id.background)
        LayoutSizeObserver.attach(
            background,
            LayoutSizeObserver.WIDTH or LayoutSizeObserver.HEIGHT,
            object : LayoutSizeObserver.OnChange {
                override fun onChange(view: View, watchFlags: Int) {
                    background.pivotX = background.width / 2f
                    background.pivotY = background.height / 2f
                }
            }
        )
        background.pivotX = background.width / 2f
        background.pivotY = background.height / 2f

        val rotate = ObjectAnimator.ofFloat(background, View.ROTATION, 0f, 360f)
        rotate.duration = 50000L
        rotate.repeatCount = ValueAnimator.INFINITE
        rotate.interpolator = LinearInterpolator()
        rotate.start()

        root.setOnTouchListener(SheetsFocusController.createClickTouchListener { _ ->
            Vibrations.getInstance().vibrate()
            @Suppress("DEPRECATION")
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(activity)
                .sendBroadcastSync(Intent(ApplicationsDialog.INTENT_LAUNCH_SEARCH))
        })

        item.addView(root)

        val visibility = GridItemVisibility(container, item, layoutData)
        listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (SEARCH_WIDGET_KEY == key) {
                visibility.update(sharedPreferences.getBoolean(SEARCH_WIDGET_KEY, SEARCH_WIDGET_DEFAULT_VISIBLE))
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)

        visibility.update(preferences.getBoolean(SEARCH_WIDGET_KEY, SEARCH_WIDGET_DEFAULT_VISIBLE))
    }

    fun detach() {
        listener?.let { preferences.unregisterOnSharedPreferenceChangeListener(it) }
    }

    companion object {
        const val SEARCH_WIDGET_KEY = "com.stario.HOMESCREEN_SEARCH_WIDGET_VISIBLE"
        const val SEARCH_WIDGET_DEFAULT_VISIBLE = false
        private const val SEARCH_TAG = "SearchWidget"
    }
}
