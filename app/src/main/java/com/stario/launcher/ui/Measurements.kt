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

package com.stario.launcher.ui

import android.app.Activity
import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import com.stario.launcher.utils.Utils
import com.stario.launcher.utils.objects.ObservableObject
import java.util.WeakHashMap

object Measurements {
    const val HEADER_SIZE_DP = 250

    private val NAV_HEIGHT = ObservableObject(0)
    private val SYS_UI_HEIGHT = ObservableObject(0)
    private val WINDOW_ANIMATION_SCALE = ObservableObject(1f)
    private val ANIMATOR_DURATION_SCALE = ObservableObject(1f)
    private val TRANSITION_ANIMATION_SCALE = ObservableObject(1f)
    private val LISTENERS = WeakHashMap<View, OnMeasureRoot>()

    private var contentObserver: ContentObserver? = null
    private var measured = false
    private var defaultPadding = 0
    private var width = 0
    private var height = 0
    private var dp = 0f
    private var dpi = 0
    private var sp = 0f

    @JvmStatic
    fun measure(root: View, onMeasureListener: OnMeasureRoot) {
        LISTENERS[root] = onMeasureListener
        measure(root)
    }

    @JvmStatic
    fun remeasure(root: View?) {
        if (root != null && measured) {
            if (LISTENERS.containsKey(root)) {
                measure(root)
            }
        } else {
            throw RuntimeException("remeasure() should not be called without a prior measure() call.")
        }
    }

    private fun measure(root: View) {
        val activity = root.context as Activity

        registerContentObservers(activity)
        val displayMetrics: DisplayMetrics = activity.resources.displayMetrics

        dp = displayMetrics.density
        dpi = displayMetrics.densityDpi
        sp = displayMetrics.scaledDensity

        width = displayMetrics.widthPixels
        height = displayMetrics.heightPixels

        defaultPadding = dpToPx(20f)

        root.setOnApplyWindowInsetsListener { _, insets ->
            if (Utils.isMinimumSDK(Build.VERSION_CODES.R)) {
                SYS_UI_HEIGHT.updateObject(
                    insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()).top
                )

                val isKeyboardVisible = insets.isVisible(WindowInsets.Type.ime())

                if (isKeyboardVisible &&
                    (activity.window.attributes.softInputMode and
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING) !=
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                ) {
                    NAV_HEIGHT.updateObject(
                        insets.getInsets(WindowInsets.Type.ime()).bottom
                    )
                } else {
                    NAV_HEIGHT.updateObject(
                        insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()).bottom
                    )
                }
            } else {
                SYS_UI_HEIGHT.updateObject(insets.systemWindowInsetTop)
                NAV_HEIGHT.updateObject(insets.systemWindowInsetBottom)
            }

            val listener = LISTENERS[root]
            if (listener != null) {
                listener.onMeasure(insets)
            } else {
                insets
            }
        }

        root.requestApplyInsets()
        measured = true
    }

    private fun registerContentObservers(activity: Activity) {
        if (contentObserver != null) {
            return
        }

        val resolver: ContentResolver = activity.contentResolver
        WINDOW_ANIMATION_SCALE.updateObject(
            Settings.Global.getFloat(
                resolver,
                Settings.Global.WINDOW_ANIMATION_SCALE,
                1.0f
            )
        )
        TRANSITION_ANIMATION_SCALE.updateObject(
            Settings.Global.getFloat(
                resolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1.0f
            )
        )
        ANIMATOR_DURATION_SCALE.updateObject(
            Settings.Global.getFloat(
                resolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
        )

        val observer = object : ContentObserver(Handler()) {
            override fun onChange(selfChange: Boolean) {
                onChange(selfChange, null)
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                WINDOW_ANIMATION_SCALE.updateObject(
                    Settings.Global.getFloat(
                        resolver,
                        Settings.Global.WINDOW_ANIMATION_SCALE,
                        1.0f
                    )
                )
                TRANSITION_ANIMATION_SCALE.updateObject(
                    Settings.Global.getFloat(
                        resolver,
                        Settings.Global.TRANSITION_ANIMATION_SCALE,
                        1.0f
                    )
                )
                ANIMATOR_DURATION_SCALE.updateObject(
                    Settings.Global.getFloat(
                        resolver,
                        Settings.Global.ANIMATOR_DURATION_SCALE,
                        1.0f
                    )
                )
            }
        }

        contentObserver = observer

        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.WINDOW_ANIMATION_SCALE),
            true,
            observer
        )
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE),
            true,
            observer
        )
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            true,
            observer
        )
    }

    @JvmStatic
    fun dpToPx(value: Float): Int = (dp * value).toInt()

    @JvmStatic
    fun getDensity(): Float = dp

    @JvmStatic
    fun spToPx(value: Float): Int = (sp * value).toInt()

    @JvmStatic
    fun getScaledDensity(): Float = sp

    @JvmStatic
    fun getDotsPerInch(): Int = dpi

    @JvmStatic
    fun getWidth(): Int = width

    @JvmStatic
    fun getHeight(): Int = height

    @JvmStatic
    fun getDefaultPadding(): Int = defaultPadding

    @JvmStatic
    fun getNavHeight(): Int = NAV_HEIGHT.getObject()

    @JvmStatic
    fun getSysUIHeight(): Int = SYS_UI_HEIGHT.getObject()

    @JvmStatic
    fun getWindowAnimationScale(): Float = WINDOW_ANIMATION_SCALE.getObject()

    @JvmStatic
    fun getTransitionAnimationScale(): Float = TRANSITION_ANIMATION_SCALE.getObject()

    @JvmStatic
    fun getAnimatorDurationScale(): Float = ANIMATOR_DURATION_SCALE.getObject()

    @JvmStatic
    fun addStatusBarListener(listener: ObservableObject.OnSet<Int>?) {
        if (listener != null) {
            SYS_UI_HEIGHT.addListener(listener)
            listener.onSet(SYS_UI_HEIGHT.getObject())
        }
    }

    @JvmStatic
    fun addNavListener(listener: ObservableObject.OnSet<Int>?) {
        if (listener != null) {
            NAV_HEIGHT.addListener(listener)
            listener.onSet(NAV_HEIGHT.getObject())
        }
    }

    @JvmStatic
    fun wereTaken(): Boolean = measured

    @JvmStatic
    fun isLandscape(): Boolean = width > height

    fun interface OnMeasureRoot {
        fun onMeasure(insets: WindowInsets): WindowInsets
    }
}
