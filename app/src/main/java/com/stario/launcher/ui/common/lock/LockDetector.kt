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

package com.stario.launcher.ui.common.lock

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import com.stario.launcher.BuildConfig
import com.stario.launcher.Stario
import com.stario.launcher.activities.launcher.Launcher
import com.stario.launcher.services.AccessibilityService
import com.stario.launcher.ui.common.grid.DynamicGridLayout

open class LockDetector(context: Context, attrs: AttributeSet?) : DynamicGridLayout(context, attrs) {
    companion object {
        const val PREFERENCE_ENTRY = "com.stario.LockDetector.LOCK"
        const val LEGACY_ANIMATION = "com.stario.LockDetector.LEGACY_LOCK_ANIMATION"
        private const val NOT_REGISTERED = -1L
    }

    private var detector: DoubleTapDetector? = null
    private var shouldIntercept = false

    init {
        if (context !is Launcher) {
            throw RuntimeException("LockDetector needs the Launcher context. (Is this view used in an activity other than Launcher.java?)")
        }
        detector = DoubleTapDetector(context)
        shouldIntercept = false
        isHapticFeedbackEnabled = false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        shouldIntercept = false
        if (detector?.onTouchEvent(ev) == true) {
            shouldIntercept = true
            requestDisallowInterceptTouchEvent(true)
            return true
        }
        return super.onInterceptTouchEvent(ev)
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = shouldIntercept || super.onTouchEvent(event)

    override fun performClick(): Boolean = super.performClick()

    inner class DoubleTapDetector(private val activity: Activity) {
        private val preferences: SharedPreferences = (activity.applicationContext as Stario).getSettings()
        private var lastEventTime = NOT_REGISTERED

        fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action != MotionEvent.ACTION_DOWN) return false
            val currentTime = System.currentTimeMillis()
            if (lastEventTime > 0 && currentTime - lastEventTime < ViewConfiguration.getDoubleTapTimeout()) {
                if (isAccessibilitySettingsOn(activity) && preferences.getBoolean(PREFERENCE_ENTRY, false)) {
                    if (!preferences.getBoolean(LEGACY_ANIMATION, false)) {
                        getClosingAnimationView().closeTo(event.rawX, event.rawY) { sleep(activity) }
                    } else {
                        sleep(activity)
                    }
                    lastEventTime = NOT_REGISTERED
                    return true
                }
                lastEventTime = NOT_REGISTERED
                return false
            }
            lastEventTime = currentTime
            return false
        }

        private fun getClosingAnimationView(): ClosingAnimationView {
            var view: View? = this@LockDetector
            while (view != null) {
                if (view is ClosingAnimationView) return view
                view = view.parent as? View
            }
            throw RuntimeException("This view must be a child of " + ClosingAnimationView::class.java.name)
        }

        private fun sleep(context: Context) {
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            @Suppress("DEPRECATION")
            val event = AccessibilityEvent.obtain()
            @Suppress("DEPRECATION")
            event.eventType = AccessibilityEvent.TYPE_ANNOUNCEMENT
            event.className = javaClass.name
            event.action = AccessibilityService.LOCK
            activity.setShowWhenLocked(true)
            manager.sendAccessibilityEvent(event)
        }

        private fun isAccessibilitySettingsOn(context: Context): Boolean {
            var accessibilityEnabled = 0
            val service = BuildConfig.APPLICATION_ID + "/" + AccessibilityService::class.java.canonicalName
            try {
                accessibilityEnabled = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
            } catch (exception: Settings.SettingNotFoundException) {
                Log.e("", "Error finding setting, default accessibility to not found: " + exception.message)
            }
            val splitter = TextUtils.SimpleStringSplitter(':')
            if (accessibilityEnabled == 1) {
                val settingValue = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                if (settingValue != null) {
                    splitter.setString(settingValue)
                    while (splitter.hasNext()) {
                        if (splitter.next().equals(service, ignoreCase = true)) return true
                    }
                }
            }
            return false
        }

    }
}
