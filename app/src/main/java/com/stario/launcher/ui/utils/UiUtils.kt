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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.transition.TransitionSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.stario.launcher.R
import com.stario.launcher.ui.Measurements
import com.stario.launcher.ui.utils.animation.Animation
import androidx.core.view.isVisible

open class UiUtils {
    companion object {
        private val UIHandler = Handler(Looper.getMainLooper())

        @JvmStatic
        fun enforceLightSystemUI(window: Window) {
            setLightSystemBars(window, false)
        }

        @JvmStatic
        fun setLightSystemBars(window: Window, enabled: Boolean) {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = enabled
                isAppearanceLightNavigationBars = enabled
            }
        }

        @JvmStatic
        fun setWindowTransitions(window: Window) {
            window.setAllowEnterTransitionOverlap(true)
            window.setAllowReturnTransitionOverlap(true)

            val transition = TransitionSet()
            transition.addTransition(MaterialSharedAxis(MaterialSharedAxis.Z, true))
            transition.interpolator = FastOutSlowInInterpolator()
            transition.duration = Animation.EXTENDED.getDuration().toLong()

            transition.excludeTarget(R.id.navigation_bar_contrast, true)
            transition.excludeTarget(R.id.status_bar_contrast, true)

            window.enterTransition = transition
            window.exitTransition = transition
            window.reenterTransition = transition
            window.returnTransition = transition
        }

        @JvmStatic
        @Suppress("DEPRECATION")
        fun makeSysUITransparent(window: Window) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            window.navigationBarDividerColor = Color.TRANSPARENT

            window.clearFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
            )

            window.setStatusBarContrastEnforced(false)
            window.setNavigationBarContrastEnforced(false)

            WindowCompat.setDecorFitsSystemWindows(window, false)
        }

        @JvmStatic
        fun expandStatusBar(context: Context?) {
            context ?: return

            try {
                @SuppressLint("WrongConstant")
                val service = context.getSystemService("statusbar")
                val statusBarManager = Class.forName("android.app.StatusBarManager")
                statusBarManager.getMethod("expandNotificationsPanel").invoke(service)
            } catch (exception: Exception) {
                Log.e("UiUtils", "expandStatusBar: Could not expand the status bar.", exception)
            }
        }

        @JvmStatic
        fun areAnimationsOn(): Boolean {
            return Measurements.getAnimatorDurationScale() > 0
        }

        @JvmStatic
        fun areTransitionsOn(): Boolean {
            return Measurements.getTransitionAnimationScale() > 0
        }

        @JvmStatic
        fun areWindowAnimationsOn(): Boolean {
            return Measurements.getWindowAnimationScale() > 0
        }

        @JvmStatic
        fun isKeyboardVisible(view: View?): Boolean {
            if (view != null) {
                val insets = ViewCompat.getRootWindowInsets(view)

                if (insets != null) {
                    return insets.isVisible(WindowInsetsCompat.Type.ime())
                }
            }

            return false
        }

        @JvmStatic
        fun hideKeyboard(view: View) {
            val inputMethodManager =
                view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            view.clearFocus()
            inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
        }

        @JvmStatic
        fun showKeyboard(view: View) {
            val inputMethodManager =
                view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            view.requestFocus()
            inputMethodManager.showSoftInput(view, 0)
        }

        @JvmStatic
        fun post(runnable: Runnable) {
            UIHandler.post(runnable)
        }

        @JvmStatic
        fun removeUICallback(runnable: Runnable) {
            UIHandler.removeCallbacks(runnable)
        }

        @JvmStatic
        fun postDelayed(runnable: Runnable, delay: Long) {
            UIHandler.postDelayed(runnable, delay)
        }

        @JvmStatic
        fun loopOnUIThread(runnable: Runnable, period: Long, condition: Condition) {
            UIHandler.post(object : Runnable {
                override fun run() {
                    if (condition.evaluate()) {
                        runnable.run()
                        UIHandler.postDelayed(this, period)
                    }
                }
            })
        }

        @JvmStatic
        fun unwrapContext(context: Context?): Activity? {
            var currentContext = context
            while (currentContext !is Activity && currentContext is ContextWrapper) {
                currentContext = currentContext.baseContext
            }

            return currentContext as? Activity
        }

        @JvmStatic
        fun roundViewGroup(view: ViewGroup, radiusDp: Int) {
            view.clipChildren = true
            view.clipToOutline = true

            view.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(
                        Rect(0, 0, view.measuredWidth, view.measuredHeight),
                        Measurements.dpToPx(radiusDp.toFloat()).toFloat()
                    )
                }
            }
        }
    }

    fun interface Condition {
        fun evaluate(): Boolean
    }

    open class Notch {
        enum class Treatment {
            DEFAULT,
            CENTER,
            INVERSE
        }

        companion object {
            @JvmStatic
            fun applyNotchMargin(view: View) {
                applyNotchMargin(view, Treatment.DEFAULT)
            }

            @JvmStatic
            fun applyNotchMargin(view: View, treatment: Treatment) {
                applyNotchMargin(view, treatment, null)
            }

            @JvmStatic
            fun applyNotchMargin(
                view: View,
                treatment: Treatment,
                listener: OnNotchMarginApplied?
            ) {
                ViewCompat.setOnApplyWindowInsetsListener(view) { _, compatInset ->
                    val cutoutInsets = compatInset.getInsets(WindowInsetsCompat.Type.displayCutout())
                    val navigationInsets = compatInset.getInsets(WindowInsetsCompat.Type.navigationBars())

                    val params = view.layoutParams as ViewGroup.MarginLayoutParams

                    when (treatment) {
                        Treatment.CENTER -> {
                            val margin = maxOf(
                                cutoutInsets.left + navigationInsets.left,
                                cutoutInsets.right + navigationInsets.right
                            )
                            params.leftMargin = margin
                            params.rightMargin = margin
                        }

                        Treatment.INVERSE -> {
                            params.leftMargin = cutoutInsets.right + navigationInsets.right
                            params.rightMargin = cutoutInsets.left + navigationInsets.left
                        }

                        else -> {
                            params.leftMargin = cutoutInsets.left + navigationInsets.left
                            params.rightMargin = cutoutInsets.right + navigationInsets.right
                        }
                    }

                    view.layoutParams = params

                    listener?.onApplied()

                    compatInset
                }

                if (view.isAttachedToWindow) {
                    ViewCompat.requestApplyInsets(view)
                } else {
                    view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(view: View) {
                            ViewCompat.requestApplyInsets(view)
                            view.removeOnAttachStateChangeListener(this)
                        }

                        override fun onViewDetachedFromWindow(view: View) {
                            view.removeOnAttachStateChangeListener(this)
                        }
                    })
                }
            }
        }

        fun interface OnNotchMarginApplied {
            fun onApplied()
        }
    }
}
