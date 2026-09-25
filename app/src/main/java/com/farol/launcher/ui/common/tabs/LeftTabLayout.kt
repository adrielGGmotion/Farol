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

package com.farol.launcher.ui.common.tabs

import android.content.Context
import android.util.AttributeSet
import android.view.View

open class LeftTabLayout : CenterTabLayout {
    private var centerTranslation = 0
    private var centerBias = 0

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val parent = parent as View
        centerBias = (parent.paddingRight - parent.paddingLeft) / 2
        centerTranslation = paddingLeft

        setPaddingRelative(0, paddingTop, centerTranslation + centerBias, paddingBottom)
    }

    override fun scrollTo(x: Int, y: Int) {
        val percentage = Math.min(1f, x.toFloat() / centerTranslation)
        val translatedX = (
            x.toDouble() + (centerBias - centerTranslation) * Math.pow(percentage.toDouble(), 0.7f.toDouble())
        ).toInt()

        super.scrollTo(translatedX, y)
        (tabStrip as View).setTranslationX((2 * centerBias).toFloat())
    }
}
