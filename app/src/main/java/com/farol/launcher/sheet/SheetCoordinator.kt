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

package com.farol.launcher.sheet

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout

open class SheetCoordinator : CoordinatorLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private var interceptStatus = ALL

    override fun isPointInChildBounds(child: View, x: Int, y: Int): Boolean = true

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return if (interceptStatus == ALL) {
            requestDisallowInterceptTouchEvent(true)
            true
        } else {
            super.onInterceptTouchEvent(ev)
        }
    }

    fun intercept(value: Int) {
        interceptStatus = value
    }

    companion object {
        const val OWN = 0
        const val ALL = 1
    }
}
