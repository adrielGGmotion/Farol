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

package com.stario.launcher.ui.common.scrollers

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.PreEventNestedScrollView

open class BottomNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : PreEventNestedScrollView(context, attrs, defStyleAttr) {
    private var nestedScrolling = false

    init {
        rotation = 180f
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        super.setPadding(left, bottom, right, top)
    }

    override fun getPaddingBottom(): Int = super.getPaddingTop()
    override fun getPaddingTop(): Int = super.getPaddingBottom()

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        child.rotation += 180f
        super.addView(child, index, params)
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
    ) {
        super.onNestedScroll(target, dxConsumed, -dyConsumed, dxUnconsumed, -dyUnconsumed)
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray) {
        nestedScrolling = true
        super.onNestedPreScroll(target, dx, dy, consumed)
    }

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
        type: Int,
    ): Boolean {
        val result = if (!nestedScrolling) {
            val value = super.dispatchNestedPreScroll(dx, -dy, consumed, offsetInWindow, type)
            consumed?.let { it[1] = -it[1] }
            offsetInWindow?.let { it[1] = -it[1] }
            value
        } else {
            super.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)
        }
        nestedScrolling = false
        return result
    }

    override fun canScrollVertically(direction: Int): Boolean = super.canScrollVertically(-direction)
}
