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

package com.stario.launcher.ui.recyclers.managers

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager

open class ScrollControlStaggeredGridLayoutManager(
    spanCount: Int
) : StaggeredGridLayoutManager(spanCount, RecyclerView.VERTICAL) {
    private var scrollEnabled = true

    open override fun supportsPredictiveItemAnimations(): Boolean = false

    open override fun setOrientation(orientation: Int) {
        if (orientation != RecyclerView.VERTICAL) {
            throw RuntimeException("This layout manager supports only vertical orientation.")
        }

        super.setOrientation(orientation)
    }

    open override fun canScrollHorizontally(): Boolean = false

    open override fun canScrollVertically(): Boolean {
        return scrollEnabled && super.canScrollVertically()
    }

    open fun setScrollEnabled(enabled: Boolean) {
        scrollEnabled = enabled
    }
}
