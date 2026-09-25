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

package com.farol.launcher.ui.widgets

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.widget.GridLayout
import android.widget.RelativeLayout
import com.farol.launcher.sheet.widgets.Widget
import com.farol.launcher.sheet.widgets.WidgetSize
import com.farol.launcher.ui.Measurements
import androidx.core.view.size

@SuppressLint("ViewConstructor")
open class WidgetContainer internal constructor(
    context: Context,
    private val hostValue: AppWidgetHostView,
    private val widgetValue: Widget,
    cell: WidgetMap.Cell
) : RelativeLayout(context), Comparable<WidgetContainer> {
    private var origin: WidgetMap.Cell = cell

    init {
        val padding = Measurements.dpToPx(10f)
        setPadding(padding, padding, padding, padding)
        setRotation(180f)
        addView(hostValue)
    }

    protected override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (parent !is WidgetGrid) {
            throw RuntimeException("WidgetContainer views can only be children of WidgetGrid")
        }
    }

    protected override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val params = layoutParams as GridLayout.LayoutParams
        val cellSize = (parent as WidgetGrid).getCellSize()

        params.rowSpec = GridLayout.spec(origin.row, widgetValue.size.height)
        params.columnSpec = GridLayout.spec(origin.column, widgetValue.size.width)
        params.width = cellSize * widgetValue.size.width
        params.height = cellSize * widgetValue.size.height

        setLayoutParams(params)

        val hostWidth = params.width - paddingLeft - paddingRight
        val hostHeight = params.height - paddingTop - paddingBottom

        if (measuredWidth > 0 && measuredHeight > 0) {
            @Suppress("DEPRECATION")
            hostValue.updateAppWidgetSize(
                null,
                (hostWidth / Measurements.getDensity()).toInt(),
                (hostHeight / Measurements.getDensity()).toInt(),
                (hostWidth / Measurements.getDensity()).toInt(),
                (hostHeight / Measurements.getDensity()).toInt()
            )
        }
    }

    open fun getSize(): WidgetSize = widgetValue.size

    open fun getPosition(): Int = widgetValue.position

    open fun getWidget(): Widget = widgetValue

    open fun getOriginRow(): Int = origin.row

    open fun getOriginColumn(): Int = origin.column

    internal open fun updateOrigin(origin: WidgetMap.Cell?) {
        if (origin != null && origin != this.origin) {
            this.origin = origin
            requestLayout()
        }
    }

    override fun compareTo(other: WidgetContainer): Int = widgetValue.compareTo(other.widgetValue)
}
