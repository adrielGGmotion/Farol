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

package com.stario.launcher.ui.common

import android.graphics.Canvas
import android.graphics.Point
import android.view.View
import com.stario.launcher.utils.ImageUtils

class DragShadowBuilder(
    private val view: View,
    private val touchPoint: Point,
) : View.DragShadowBuilder(view) {
    override fun onDrawShadow(canvas: Canvas) {
        super.onDrawShadow(canvas)
        canvas.drawBitmap(ImageUtils.toBitmap(view), 0f, 0f, null)
    }

    override fun onProvideShadowMetrics(shadowSize: Point, touchPoint: Point) {
        shadowSize.set(view.width, view.height)
        touchPoint.set(this.touchPoint.x, this.touchPoint.y)
    }
}
