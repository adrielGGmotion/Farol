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

package com.stario.launcher.ui.dialogs

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.annotation.FloatRange
import androidx.appcompat.app.AppCompatDialog
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.utils.UiUtils
import kotlin.jvm.JvmField

private fun getThemeResId(context: Context, themeId: Int): Int {
    var resolvedThemeId = themeId

    // reuse the bottomSheetDialog theme
    if (resolvedThemeId == 0) {
        val outValue = TypedValue()

        resolvedThemeId = if (context.theme.resolveAttribute(
                com.google.android.material.R.attr.bottomSheetDialogTheme,
                outValue,
                true
            )
        ) {
            outValue.resourceId
        } else {
            com.google.android.material.R.style.Theme_Design_Light_BottomSheetDialog
        }
    }

    return resolvedThemeId
}

open class PersistentFullscreenDialog(
    activity: ThemedActivity,
    theme: Int,
    blur: Boolean
) : AppCompatDialog(activity, getThemeResId(activity, theme)) {
    private var dimmingController: DialogBackgroundDimmingController.DimmingController? = null

    @JvmField
    protected var listener: OnBackPressed? = null

    private val activity: ThemedActivity = activity
    private val blur: Boolean = blur

    init {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        supportRequestWindowFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
    }

    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val window = getWindow()
        if (window != null) {
            dimmingController = DialogBackgroundDimmingController.attach(activity, this, blur)

            window.setWindowAnimations(0)
            window.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

            UiUtils.makeSysUITransparent(window)
        }
    }

    open fun setDimmingFactor(@FloatRange(from = 0.0, to = 1.0) factor: Float) {
        dimmingController?.setFactor(factor)
    }

    @SuppressLint("GestureBackNavigation")
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (listener == null || listener!!.onPressed()) {
            super.onBackPressed()
        }
    }

    override fun show() {
        // Disable default show behaviour
    }

    override fun hide() {
        val window = getWindow()
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        }

        super.hide()
    }

    protected open fun superShow(): Boolean {
        if (activity.hasWindowFocus()) {
            super.show()
            return true
        }

        return false
    }

    open fun showDialog(): Boolean {
        if (activity.hasWindowFocus()) {
            super.show()

            val window = getWindow()
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            }

            return true
        }

        return false
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return activity.isTouchEnabled() && super.dispatchTouchEvent(ev)
    }

    open fun setOnBackPressed(listener: OnBackPressed?) {
        this.listener = listener
    }

    fun interface OnBackPressed {
        fun onPressed(): Boolean
    }
}
