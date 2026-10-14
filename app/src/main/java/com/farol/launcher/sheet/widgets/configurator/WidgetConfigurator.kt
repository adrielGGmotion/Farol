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

package com.farol.launcher.sheet.widgets.configurator

import android.appwidget.AppWidgetProviderInfo
import android.view.LayoutInflater
import android.view.View
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.farol.launcher.R
import com.farol.launcher.sheet.widgets.WidgetSize
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.ui.Measurements
import com.farol.launcher.ui.dialogs.ActionDialog
import com.farol.launcher.ui.recyclers.DividerItemDecorator

open class WidgetConfigurator(
    activity: ThemedActivity,
    private val requestListener: Request,
    // false: tapping a widget adds it at its default size (the home screen resizes by handle instead)
    private val showSizes: Boolean = true
) : ActionDialog(activity) {
    private lateinit var scroller: NestedScrollView
    private lateinit var adapter: WidgetListAdapter

    override fun inflateContent(inflater: LayoutInflater, parent: android.view.ViewGroup): View {
        val contentView = inflater.inflate(R.layout.widget_picker, parent, false)

        scroller = contentView.findViewById(R.id.scroller)
        scroller.setClipToOutline(true)

        val recycler: RecyclerView = contentView.findViewById(R.id.container_widgets)
        val layoutManager = LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false)

        adapter = WidgetListAdapter(activity, recycler, requestListener, showSizes)

        recycler.setAdapter(adapter)
        recycler.setLayoutManager(layoutManager)
        recycler.addItemDecoration(
            DividerItemDecorator(activity, MaterialDividerItemDecoration.VERTICAL)
        )

        return contentView
    }

    override fun blurBehind(): Boolean = true

    override fun show() {
        super.show()

        scroller.scrollTo(0, 0)
        adapter.update()
    }

    override fun getDesiredInitialState(): Int {
        return if (!Measurements.isLandscape()) {
            BottomSheetBehavior.STATE_HALF_EXPANDED
        } else {
            BottomSheetBehavior.STATE_EXPANDED
        }
    }

    fun interface Request {
        fun requestAddition(info: AppWidgetProviderInfo, size: WidgetSize)
    }
}
