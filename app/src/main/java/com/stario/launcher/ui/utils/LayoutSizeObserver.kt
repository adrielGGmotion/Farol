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

package com.stario.launcher.ui.utils

import android.graphics.Rect
import android.view.View
import androidx.annotation.IntRange

class LayoutSizeObserver private constructor() {
    companion object {
        @JvmField val WIDTH = 0b000001
        @JvmField val HEIGHT = 0b000010
        @JvmField val LEFT = 0b000100
        @JvmField val TOP = 0b001000
        @JvmField val RIGHT = 0b010000
        @JvmField val BOTTOM = 0b100000

        @JvmStatic
        @JvmOverloads
        fun attach(
            view: View,
            @IntRange(from = 1, to = 0b111111) watchFlags: Int,
            listener: OnChange,
            invalidateOnAttach: Boolean = true,
        ) {
            val viewChangeListener = object : View.OnLayoutChangeListener {
                private var rect: Rect? = null

                override fun onLayoutChange(
                    view: View,
                    left: Int,
                    top: Int,
                    right: Int,
                    bottom: Int,
                    oldLeft: Int,
                    oldTop: Int,
                    oldRight: Int,
                    oldBottom: Int,
                ) {
                    val currentRect = Rect(view.left, view.top, view.right, view.bottom)
                    val previousRect = rect

                    if (previousRect == null) {
                        val flags = WIDTH or HEIGHT or LEFT or TOP or RIGHT or BOTTOM
                        rect = currentRect
                        listener.onChange(view, flags and watchFlags)
                        listener.onChange(view, flags and watchFlags, currentRect)
                        return
                    }

                    var flags = 0
                    if (previousRect.width() != currentRect.width()) flags = flags or WIDTH
                    if (previousRect.height() != currentRect.height()) flags = flags or HEIGHT
                    if (previousRect.left != currentRect.left) flags = flags or LEFT
                    if (previousRect.top != currentRect.top) flags = flags or TOP
                    if (previousRect.right != currentRect.right) flags = flags or RIGHT
                    if (previousRect.bottom != currentRect.bottom) flags = flags or BOTTOM

                    rect = currentRect
                    if (flags and watchFlags != 0) {
                        listener.onChange(view, flags and watchFlags)
                        listener.onChange(view, flags and watchFlags, currentRect)
                    }
                }
            }

            if (invalidateOnAttach) {
                viewChangeListener.onLayoutChange(view, 0, 0, 0, 0, 0, 0, 0, 0)
            }
            view.addOnLayoutChangeListener(viewChangeListener)
        }
    }

    interface OnChange {
        fun onChange(view: View, watchFlags: Int) {}
        fun onChange(view: View, watchFlags: Int, rect: Rect) {}
    }
}
