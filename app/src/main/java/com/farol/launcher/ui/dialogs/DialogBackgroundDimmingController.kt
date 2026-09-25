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

package com.farol.launcher.ui.dialogs

import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.ActionMode
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.SearchEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.FloatRange
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.utils.Utils
import kotlin.jvm.JvmStatic
import androidx.core.graphics.drawable.toDrawable

open class DialogBackgroundDimmingController private constructor(
    private val activity: ThemedActivity
) : SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        const val LOW_SPEC_KEY = "DialogBackgroundBlurController.LOW_SPEC"

        private const val TAG = "DialogBackgroundBlurController"
        private const val MAX_BACKGROUND_ALPHA_LOW_SPEC = 240
        private const val MAX_BACKGROUND_ALPHA = 190
        private const val MAX_BLUR_SIZE = 100

        @JvmStatic
        fun attach(
            activity: ThemedActivity,
            dialog: Dialog,
            blur: Boolean
        ): DimmingController? = attach(activity, dialog, blur, 1f)

        @JvmStatic
        fun attach(
            activity: ThemedActivity,
            dialog: Dialog,
            blur: Boolean,
            multiplier: Float
        ): DimmingController? {
            val controller = DialogBackgroundDimmingController(activity)
            if (controller.hook(dialog)) {
                controller.dimmingController = object : DimmingController {
                    private var lastBlurStep = -1
                    private var lastFactor = 0f

                    override fun setFactor(factor: Float) {
                        val window = controller.window ?: return
                        var adjustedFactor = factor * multiplier

                        updateAlpha(adjustedFactor)
                        lastFactor = adjustedFactor

                        if (!blur || controller.hasLimitedResources) {
                            return
                        }

                        if (Utils.isMinimumSDK(Build.VERSION_CODES.S)) {
                            val blurRadius = (MAX_BLUR_SIZE * adjustedFactor).toInt()

                            if (blurRadius != lastBlurStep) {
                                window.setBackgroundBlurRadius(blurRadius)
                                lastBlurStep = blurRadius
                            }
                        }
                    }

                    override fun invalidate() {
                        val window = controller.window ?: return

                        updateAlpha(lastFactor)

                        if (!blur || controller.hasLimitedResources) {
                            if (Utils.isMinimumSDK(Build.VERSION_CODES.S)) {
                                window.setBackgroundBlurRadius(0)
                            }

                            lastBlurStep = -1
                        }
                    }

                    private fun updateAlpha(factor: Float) {
                        controller.background.alpha = (
                            (if (controller.hasLimitedResources) {
                                MAX_BACKGROUND_ALPHA_LOW_SPEC
                            } else {
                                MAX_BACKGROUND_ALPHA
                            }) * factor
                        ).toInt()
                    }
                }

                return controller.dimmingController
            }

            return null
        }
    }

    private val batterySaverReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            invalidateLowSpec()
        }
    }
    private val settings: SharedPreferences = activity.applicationContext.getSettings()
    private val powerManager: PowerManager = activity.getSystemService(PowerManager::class.java)
    private val background: Drawable = activity.getAttributeData(com.google.android.material.R.attr.colorSurfaceContainer).toDrawable().apply {
        alpha = 0
    }

    private var dimmingController: DimmingController? = null
    private var hasLimitedResources = false
    private var window: Window? = null

    private fun hook(dialog: Dialog): Boolean {
        val window = dialog.window
        if (window == null) {
            Log.w(TAG, "hook() was called too early. Are you sure the window is available?")
            return false
        }

        val original = window.callback
        if (original is WindowDimmingCallbackWrapper) {
            Log.w(TAG, "hook() has already been called before on this window. Ignoring...")
            return false
        }

        this.window = window
        window.callback = WindowDimmingCallbackWrapper(original)

        if (window.decorView.isAttachedToWindow) {
            attach()
        }

        return true
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String?
    ) {
        if (LOW_SPEC_KEY == key) {
            invalidateLowSpec()
        }
    }

    private fun invalidateLowSpec() {
        hasLimitedResources = settings.getBoolean(LOW_SPEC_KEY, false) ||
            powerManager.isPowerSaveMode

        if (window == null || dimmingController == null) {
            return
        }

        dimmingController?.invalidate()
    }

    private fun attach() {
        invalidateLowSpec()

        window?.let {
            it.setBackgroundDrawable(background)
            it.setDimAmount(0f)
            it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        activity.registerReceiver(
            batterySaverReceiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        )
        settings.registerOnSharedPreferenceChangeListener(this)
    }

    private fun detach() {
        activity.unregisterReceiver(batterySaverReceiver)
        settings.unregisterOnSharedPreferenceChangeListener(this)
    }

    interface DimmingController {
        fun setFactor(@FloatRange(from = 0.0, to = 1.0) factor: Float)

        fun invalidate()
    }

    inner open class WindowDimmingCallbackWrapper(private val base: Window.Callback?) : Window.Callback {
        override fun onAttachedToWindow() {
            attach()
            base?.onAttachedToWindow()
        }

        override fun onDetachedFromWindow() {
            detach()
            base?.onDetachedFromWindow()
        }

        // forward everything else to base

        override fun dispatchGenericMotionEvent(motionEvent: MotionEvent): Boolean {
            return base?.dispatchGenericMotionEvent(motionEvent) ?: false
        }

        override fun dispatchKeyEvent(keyEvent: KeyEvent): Boolean {
            return base?.dispatchKeyEvent(keyEvent) ?: false
        }

        override fun dispatchKeyShortcutEvent(keyEvent: KeyEvent): Boolean {
            return base?.dispatchKeyShortcutEvent(keyEvent) ?: false
        }

        override fun dispatchPopulateAccessibilityEvent(accessibilityEvent: AccessibilityEvent): Boolean {
            return base?.dispatchPopulateAccessibilityEvent(accessibilityEvent) ?: false
        }

        override fun dispatchTouchEvent(motionEvent: MotionEvent): Boolean {
            return base?.dispatchTouchEvent(motionEvent) ?: false
        }

        override fun dispatchTrackballEvent(motionEvent: MotionEvent): Boolean {
            return base?.dispatchTrackballEvent(motionEvent) ?: false
        }

        override fun onActionModeFinished(actionMode: ActionMode) {
            base?.onActionModeFinished(actionMode)
        }

        override fun onActionModeStarted(actionMode: ActionMode) {
            base?.onActionModeStarted(actionMode)
        }

        override fun onContentChanged() {
            base?.onContentChanged()
        }

        override fun onCreatePanelMenu(index: Int, menu: Menu): Boolean {
            return base?.onCreatePanelMenu(index, menu) ?: false
        }

        override fun onCreatePanelView(index: Int): View? {
            return base?.onCreatePanelView(index)
        }

        override fun onMenuItemSelected(index: Int, menuItem: MenuItem): Boolean {
            return base?.onMenuItemSelected(index, menuItem) ?: false
        }

        override fun onMenuOpened(index: Int, menu: Menu): Boolean {
            return base?.onMenuOpened(index, menu) ?: false
        }

        override fun onPanelClosed(index: Int, menu: Menu) {
            base?.onPanelClosed(index, menu)
        }

        override fun onPreparePanel(index: Int, view: View?, menu: Menu): Boolean {
            return base?.onPreparePanel(index, view, menu) ?: false
        }

        override fun onSearchRequested(): Boolean {
            return base?.onSearchRequested() ?: false
        }

        override fun onSearchRequested(searchEvent: SearchEvent): Boolean {
            return base?.onSearchRequested(searchEvent) ?: false
        }

        override fun onWindowAttributesChanged(layoutParams: WindowManager.LayoutParams) {
            base?.onWindowAttributesChanged(layoutParams)
        }

        override fun onWindowFocusChanged(changed: Boolean) {
            base?.onWindowFocusChanged(changed)
        }

        override fun onWindowStartingActionMode(callback: ActionMode.Callback): ActionMode? {
            return base?.onWindowStartingActionMode(callback)
        }

        override fun onWindowStartingActionMode(callback: ActionMode.Callback, index: Int): ActionMode? {
            return base?.onWindowStartingActionMode(callback, index)
        }
    }
}
