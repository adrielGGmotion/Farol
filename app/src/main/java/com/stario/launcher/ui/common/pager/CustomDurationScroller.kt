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

package com.stario.launcher.ui.common.pager

import android.content.Context
import android.view.animation.Interpolator
import android.widget.Scroller

class CustomDurationScroller @JvmOverloads constructor(
    context: Context,
    interpolator: Interpolator? = null,
    flywheel: Boolean = true,
) : Scroller(context, interpolator, flywheel) {
    private var scrollFactor = 1.0

    fun setScrollDurationFactor(scrollFactor: Double) {
        this.scrollFactor = scrollFactor
    }

    override fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int, duration: Int) {
        super.startScroll(startX, startY, dx, dy, (duration * scrollFactor).toInt())
    }
}
