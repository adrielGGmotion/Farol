/*
 * Copyright (C) 2026 Răzvan Albu
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

package com.stario.launcher.ui.common

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Rect
import android.graphics.drawable.Drawable

class AnimatedInsetDrawable(private val inner: Drawable) : Drawable() {
    private var left = 0
    private var top = 0
    private var right = 0
    private var bottom = 0

    fun setInsets(left: Int, top: Int, right: Int, bottom: Int) {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
        updateInnerBounds(bounds)
        invalidateSelf()
    }

    private fun updateInnerBounds(bounds: Rect) {
        inner.setBounds(
            bounds.left + left,
            bounds.top + top,
            bounds.right - right,
            bounds.bottom - bottom,
        )
    }

    override fun onBoundsChange(bounds: Rect) {
        updateInnerBounds(bounds)
    }

    override fun draw(canvas: Canvas) {
        inner.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
        inner.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        inner.colorFilter = colorFilter
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = inner.opacity
}
