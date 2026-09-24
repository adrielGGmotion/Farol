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

package com.stario.launcher.activities.launcher.widgets.pins

import android.content.SharedPreferences
import android.graphics.Rect
import android.view.View
import android.widget.RelativeLayout
import androidx.core.math.MathUtils
import androidx.recyclerview.widget.RecyclerView
import com.stario.launcher.R
import com.stario.launcher.activities.launcher.widgets.pins.dialog.PinnedAppsGroupDialog
import com.stario.launcher.apps.CategoryManager
import com.stario.launcher.preferences.Entry
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.Measurements
import com.stario.launcher.ui.common.grid.DraggableGridItem
import com.stario.launcher.ui.common.grid.DynamicGridLayout
import com.stario.launcher.ui.icons.AdaptiveIconView
import com.stario.launcher.ui.recyclers.autogrid.AutoGridLayoutManager
import com.stario.launcher.ui.utils.LayoutSizeObserver
import com.stario.launcher.ui.utils.UiUtils

open class PinnedCategory(private val activity: ThemedActivity) {
    private val categoryManager: CategoryManager = CategoryManager.getInstance()
    private val preferences: SharedPreferences = activity.applicationContext.getSharedPreferences(Entry.PINNED_CATEGORY)

    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var recycler: RecyclerView? = null
    private var layoutData: DynamicGridLayout.ItemLayoutData? = null
    private var gridItem: DraggableGridItem? = null
    private var isAttached = false

    fun attach(
        container: DynamicGridLayout,
        popUpShowListener: PinnedAppsAdapter.OnPopUpShowListener?,
        transitionListener: PinnedAppsGroupDialog.TransitionListener?
    ) {
        if (gridItem != null) {
            return
        }

        gridItem = DraggableGridItem(activity)
        gridItem?.itemId = CATEGORY_TAG

        layoutData = DynamicGridLayout.ItemLayoutData(CATEGORY_TAG, 0, 0, 4, 1)
        layoutData?.minColSpan = 1
        layoutData?.maxRowSpan = 1

        val root = activity.layoutInflater.inflate(R.layout.pinned_apps, gridItem, false) as RelativeLayout
        recycler = root.findViewById(R.id.recycler)
        gridItem?.addView(root)

        listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (PINNED_CATEGORY_VISIBLE == key) {
                updateContainerState(container, sharedPreferences.getBoolean(PINNED_CATEGORY_VISIBLE, false))
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)

        val manager = AutoGridLayoutManager(activity, 1)
        val adapter = PinnedAppsAdapter(activity, preferences, popUpShowListener, transitionListener)

        LayoutSizeObserver.attach(root, LayoutSizeObserver.WIDTH, object : LayoutSizeObserver.OnChange {
            override fun onChange(view: View, watchFlags: Int, rect: Rect) {
                val columns = MathUtils.clamp(
                    rect.width() / (AdaptiveIconView.getMaxIconSize() + Measurements.getDefaultPadding()),
                    1,
                    6
                )
                manager.setSpanCount(columns)
                adapter.setMaxItemCount(columns)
            }
        })

        recycler?.itemAnimator = null
        recycler?.layoutManager = manager

        categoryManager.addOnReadyListener {
            if (activity.isFinishing || activity.isDestroyed) {
                return@addOnReadyListener
            }
            recycler?.adapter = adapter
        }

        updateContainerState(container, preferences.getBoolean(PINNED_CATEGORY_VISIBLE, false))
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
        recycler?.adapter = null
        listener?.let { preferences.unregisterOnSharedPreferenceChangeListener(it) }
    }

    companion object {
        const val PINNED_CATEGORY_VISIBLE = "com.stario.IS_PINNED_CATEGORY_VISIBLE"
        const val PINNED_CATEGORY = "com.stario.PINNED_CATEGORY"
        private const val CATEGORY_TAG = "CategoryGlance"
    }
}
