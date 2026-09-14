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

package com.stario.launcher.ui.utils.animation

import android.graphics.Path
import androidx.transition.PathMotion

class SharedElementMotion : PathMotion() {
    override fun getPath(startX: Float, startY: Float, endX: Float, endY: Float): Path {
        return Path().apply {
            moveTo(startX, startY)
            cubicTo(
                startX + endX * (Math.random().toFloat() * 0.5f),
                startY + endY * (Math.random().toFloat() * 0.5f),
                endX + startX * (Math.random().toFloat() * 0.5f),
                endY + startY * (Math.random().toFloat() * 0.5f),
                endX,
                endY,
            )
        }
    }
}
