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

open class LayoutSizeObserver {
    companion object {
        const val WIDTH = 0b000001

        const val HEIGHT = 0b000010

        const val LEFT = 0b000100

        const val TOP = 0b001000

        const val RIGHT = 0b010000

        const val BOTTOM = 0b100000

        @JvmStatic
        fun attach(
            view: View,
            @IntRange(from = 1, to = 0b111111) watchFlags: Int,
            listener: OnChange
        ) {
            attach(view, watchFlags, listener, true)
        }

        @JvmStatic
        fun attach(
            view: View,
            @IntRange(from = 1, to = 0b111111) watchFlags: Int,
            listener: OnChange,
            invalidateOnAttach: Boolean
        ) {
            val viewChangeListener = object : View.OnLayoutChangeListener {
                private var previousRect: Rect? = null

                override fun onLayoutChange(
                    view: View,
                    left: Int,
                    top: Int,
                    right: Int,
                    bottom: Int,
                    oldLeft: Int,
                    oldTop: Int,
                    oldRight: Int,
                    oldBottom: Int
                ) {
                    val currentRect = Rect(view.left, view.top, view.right, view.bottom)

                    if (previousRect == null) {
                        val flags = WIDTH or HEIGHT or LEFT or TOP or RIGHT or BOTTOM
                        previousRect = currentRect

                        listener.onChange(view, flags and watchFlags)
                        listener.onChange(view, flags and watchFlags, currentRect)
                    }

                    var flags = 0

                    if (previousRect!!.width() != currentRect.width()) {
                        flags = flags or WIDTH
                    }

                    if (previousRect!!.height() != currentRect.height()) {
                        flags = flags or HEIGHT
                    }

                    if (previousRect!!.left != currentRect.left) {
                        flags = flags or LEFT
                    }

                    if (previousRect!!.top != currentRect.top) {
                        flags = flags or TOP
                    }

                    if (previousRect!!.right != currentRect.right) {
                        flags = flags or RIGHT
                    }

                    if (previousRect!!.bottom != currentRect.bottom) {
                        flags = flags or BOTTOM
                    }

                    previousRect = currentRect
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
