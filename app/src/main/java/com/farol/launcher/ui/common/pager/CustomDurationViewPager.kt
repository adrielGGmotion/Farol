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

package com.farol.launcher.ui.common.pager

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.animation.Interpolator
import androidx.viewpager.widget.ViewPager

open class CustomDurationViewPager : ViewPager {
    private var durationScroller: CustomDurationScroller? = null

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    /**
     * Override the Scroller instance with our own class so we can change the
     * duration
     */
    private fun init() {
        try {
            val scroller = ViewPager::class.java.getDeclaredField("mScroller")
            scroller.isAccessible = true
            val interpolator = ViewPager::class.java.getDeclaredField("sInterpolator")
            interpolator.isAccessible = true

            durationScroller = CustomDurationScroller(
                context,
                interpolator.get(null) as Interpolator,
            )
            scroller.set(this, durationScroller)
        } catch (exception: Exception) {
            Log.e(
                "com.farol.CustomDurationViewPager",
                "postInitViewPager: Unknown reflection exception",
                exception,
            )
        }

        setScrollDurationFactor(0.5)
    }

    /**
     * Set the factor by which the duration will change
     */
    open fun setScrollDurationFactor(scrollFactor: Double) {
        durationScroller!!.setScrollDurationFactor(scrollFactor)
    }
}
