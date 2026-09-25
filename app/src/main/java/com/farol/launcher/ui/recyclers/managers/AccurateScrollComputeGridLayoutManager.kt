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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.farol.launcher.ui.recyclers.managers

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * A custom GridLayoutManager that provides more accurate vertical scroll offset calculation.
 * <p>
 * <b>NOTE:</b> The layout manager assumes consistent item sizes for proper scroll offset calculations.
 *
 * @see GridLayoutManager
 */
open class AccurateScrollComputeGridLayoutManager(
    context: Context,
    spanCount: Int
) : GridLayoutManager(context, spanCount, RecyclerView.VERTICAL, false) {
    private var scrollEnabled = true

    open override fun supportsPredictiveItemAnimations(): Boolean = false

    open override fun setOrientation(orientation: Int) {
        if (orientation != RecyclerView.VERTICAL) {
            throw RuntimeException("This layout manager supports only vertical orientation.")
        }

        super.setOrientation(orientation)
    }

    open override fun computeVerticalScrollOffset(state: RecyclerView.State): Int {
        if (getChildCount() == 0) {
            return 0
        }

        val position = findFirstVisibleItemPosition()
        if (position != RecyclerView.NO_POSITION) {
            val view = findViewByPosition(position)
            if (view != null) {
                val marginLayoutParams = view.getLayoutParams() as ViewGroup.MarginLayoutParams
                val offset = view.getTop() - marginLayoutParams.topMargin -
                    view.getHeight() * Math.ceil((position / getSpanCount().toFloat()).toDouble()) -
                    getPaddingTop()
                return (-offset).toInt()
            }
        }

        return 0
    }

    open override fun computeVerticalScrollRange(state: RecyclerView.State): Int {
        if (getChildCount() == 0) {
            return 0
        }

        val position = findFirstVisibleItemPosition()
        if (position != RecyclerView.NO_POSITION) {
            val view = findViewByPosition(position)
            val rows = Math.ceil((getItemCount() / getSpanCount().toFloat()).toDouble()).toInt()
            if (view != null) {
                return view.getHeight() * rows
            }
        }

        return 0
    }

    open override fun computeVerticalScrollExtent(state: RecyclerView.State): Int {
        return getHeight() - getPaddingTop() - getPaddingBottom()
    }

    open override fun canScrollHorizontally(): Boolean = false

    open override fun canScrollVertically(): Boolean {
        return scrollEnabled && super.canScrollVertically()
    }

    open fun setScrollEnabled(enabled: Boolean) {
        scrollEnabled = enabled
    }
}
