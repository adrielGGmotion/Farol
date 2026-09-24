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

package com.stario.launcher.ui.keyboard

import android.app.Activity
import android.graphics.Insets
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Display
import android.view.DisplayCutout
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager.LayoutParams
import android.widget.LinearLayout
import android.widget.PopupWindow
import com.stario.launcher.ui.Measurements
import com.stario.launcher.utils.Utils
import java.util.concurrent.CopyOnWriteArrayList

open class KeyboardHeightProvider(activity: Activity) : PopupWindow(activity) {
    private val activity: Activity = activity
    private val observers: MutableList<KeyboardHeightListener> = CopyOnWriteArrayList()
    private val popupView: View = LinearLayout(activity)
    private val parentView: View = activity.findViewById(android.R.id.content)
    private val listener: ViewTreeObserver.OnGlobalLayoutListener
    private var oldHeight = Int.MIN_VALUE

    init {
        popupView.setLayoutParams(
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        popupView.setBackground(ColorDrawable(0))

        listener = ViewTreeObserver.OnGlobalLayoutListener {
            notifyKeyboardHeightChanged(getKeyboardHeight())
            // on API 29, the GlobalLayoutListener fires before
            // PopupWindow finishes the resizing. Add a post
            // call just to be safe
            popupView.post { notifyKeyboardHeightChanged(getKeyboardHeight()) }
        }

        setContentView(popupView)

        setSoftInputMode(LayoutParams.SOFT_INPUT_ADJUST_RESIZE or LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED)

        setWidth(0)
        setHeight(LayoutParams.MATCH_PARENT)
    }

    open fun start() {
        if (!isShowing && parentView.windowToken != null) {
            setBackgroundDrawable(ColorDrawable(0))
            showAtLocation(parentView, Gravity.NO_GRAVITY, 0, 0)

            parentView.viewTreeObserver.addOnGlobalLayoutListener(listener)

            notifyKeyboardHeightChanged(getKeyboardHeight(), true)
        }
    }

    open fun close() {
        dismiss()

        parentView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
    }

    open fun addKeyboardHeightListener(observer: KeyboardHeightListener?) {
        if (observer != null) {
            observers.add(observer)
        }
    }

    open fun removeKeyboardHeightListener(observer: KeyboardHeightListener?) {
        if (observer != null) {
            observers.remove(observer)
        }
    }

    private fun notifyKeyboardHeightChanged(height: Int) {
        notifyKeyboardHeightChanged(height, false)
    }

    private fun notifyKeyboardHeightChanged(height: Int, force: Boolean) {
        if (oldHeight == height && !force) {
            return
        }

        oldHeight = height

        for (observer in observers) {
            observer.onKeyboardHeightChanged(height)
        }
    }

    open fun getKeyboardHeight(): Int {
        if (!isShowing) {
            return 0
        }

        if (Utils.isMinimumSDK(Build.VERSION_CODES.R)) {
            val windowInsets: WindowInsets? = parentView.rootWindowInsets

            if (windowInsets != null) {
                val insets: Insets = windowInsets.getInsets(WindowInsets.Type.ime())

                return maxOf(0, insets.bottom - Measurements.getNavHeight())
            }

            return 0
        } else {
            val screenSize = Point()
            val display: Display = activity.windowManager.defaultDisplay
            display.getSize(screenSize)

            val displayCutout: DisplayCutout? = display.cutout
            if (displayCutout != null) {
                screenSize.y += displayCutout.safeInsetTop
            }

            val rect = Rect()
            popupView.getWindowVisibleDisplayFrame(rect)

            return screenSize.y - rect.bottom
        }
    }

    fun interface KeyboardHeightListener {
        fun onKeyboardHeightChanged(height: Int)
    }
}
