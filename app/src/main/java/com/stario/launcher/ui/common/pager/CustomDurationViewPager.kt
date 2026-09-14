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
import android.util.AttributeSet
import android.util.Log
import android.view.animation.Interpolator
import androidx.viewpager.widget.ViewPager

class CustomDurationViewPager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewPager(context, attrs) {
    private var scroller: CustomDurationScroller? = null

    init {
        try {
            val scrollerField = ViewPager::class.java.getDeclaredField("mScroller").apply { isAccessible = true }
            val interpolatorField = ViewPager::class.java.getDeclaredField("sInterpolator").apply { isAccessible = true }
            scroller = CustomDurationScroller(context, interpolatorField.get(null) as Interpolator)
            scrollerField.set(this, scroller)
        } catch (exception: Exception) {
            Log.e(
                "com.stario.CustomDurationViewPager",
                "postInitViewPager: Unknown reflection exception",
                exception,
            )
        }
        setScrollDurationFactor(0.5)
    }

    fun setScrollDurationFactor(scrollFactor: Double) {
        scroller!!.setScrollDurationFactor(scrollFactor)
    }
}
