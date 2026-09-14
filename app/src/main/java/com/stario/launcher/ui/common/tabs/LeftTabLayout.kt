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

package com.stario.launcher.ui.common.tabs

import android.content.Context
import android.util.AttributeSet
import android.view.View

class LeftTabLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : CenterTabLayout(context, attrs, defStyle) {
    private var centerTranslation = 0
    private var centerBias = 0

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val parentView = parent as View
        centerBias = (parentView.paddingRight - parentView.paddingLeft) / 2
        centerTranslation = paddingLeft
        setPaddingRelative(0, paddingTop, centerTranslation + centerBias, paddingBottom)
    }

    override fun scrollTo(x: Int, y: Int) {
        val percentage = minOf(1f, x.toFloat() / centerTranslation)
        val adjustedX = (x + (centerBias - centerTranslation) * Math.pow(percentage.toDouble(), 0.7)).toInt()
        super.scrollTo(adjustedX, y)
        (tabStrip as View).translationX = 2f * centerBias
    }
}
