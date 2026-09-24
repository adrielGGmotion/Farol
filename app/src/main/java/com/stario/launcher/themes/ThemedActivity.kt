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

package com.stario.launcher.themes

import android.animation.ValueAnimator
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.PaintDrawable
import android.os.Build
import android.os.Bundle
import android.transition.Transition
import android.transition.TransitionListenerAdapter
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.annotation.AttrRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.core.app.ActivityCompat
import com.stario.launcher.Stario
import com.stario.launcher.preferences.Entry
import com.stario.launcher.themes.Theme.THEME_BLUE
import com.stario.launcher.themes.Theme.THEME_DYNAMIC
import com.stario.launcher.ui.Measurements
import com.stario.launcher.ui.back.BackEvent
import com.stario.launcher.ui.back.BackEventType
import com.stario.launcher.ui.back.BackGestureEventBus
import com.stario.launcher.ui.common.AnimatedInsetDrawable
import com.stario.launcher.ui.utils.UiUtils
import com.stario.launcher.ui.utils.animation.Animation
import com.stario.launcher.utils.Utils
import java.util.Arrays
import java.util.HashMap

abstract class ThemedActivity : AppCompatActivity() {
    private val activityResultListeners = HashMap<Int, OnActivityResult?>()
    private val requestPermissionResultListeners = HashMap<Int, OnPermissionRequestResult?>()

    private lateinit var uiInsetBackgroundAnimator: AnimatedInsetDrawable
    private lateinit var roundedCornerBackground: PaintDrawable
    private lateinit var themePreferences: SharedPreferences
    private var backgroundAnimator: ValueAnimator? = null
    private lateinit var windowBackground: ColorDrawable
    private var allowTouches = true
    private var backgroundColor = 0
    private lateinit var currentTheme: Theme

    protected override fun onCreate(savedInstanceState: Bundle?) {
        themePreferences = getApplicationContext().getSharedPreferences(Entry.THEME)

        //default theme if it doesn't exist
        if (!themePreferences.contains(THEME)) {
            if (Utils.isMinimumSDK(Build.VERSION_CODES.R)) {
                themePreferences.edit().putString(THEME, THEME_DYNAMIC.toString()).apply()
            } else {
                themePreferences.edit().putString(THEME, THEME_BLUE.toString()).apply()
            }
        }

        //night mode flags
        val nightModeFlags = getResources().getConfiguration().uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkModeOn = nightModeFlags == Configuration.UI_MODE_NIGHT_YES ||
            themePreferences.getBoolean(FORCE_DARK, false)

        currentTheme = Theme.from(themePreferences.getString(THEME, THEME_BLUE.toString()))
        setTheme(if (isDarkModeOn) currentTheme.getDarkResourceID() else currentTheme.getLightResourceID())

        backgroundColor = getAttributeData(com.google.android.material.R.attr.colorSurface)

        roundedCornerBackground = PaintDrawable(backgroundColor)
        uiInsetBackgroundAnimator = AnimatedInsetDrawable(roundedCornerBackground)
        roundedCornerBackground.setCornerRadius(Measurements.dpToPx(30f).toFloat())

        val window = getWindow()

        if (window != null) {
            UiUtils.setWindowTransitions(window)
            UiUtils.makeSysUITransparent(window)

            if (isOpaque()) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                windowBackground = ColorDrawable(Color.TRANSPARENT)
            } else {
                window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)

                windowBackground = ColorDrawable(backgroundColor)
                windowBackground.setAlpha(0)
            }

            window.setBackgroundDrawable(windowBackground)
            window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)

            if (Utils.isMinimumSDK(Build.VERSION_CODES.R)) {
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            private val cubicBezier: Easing = CubicBezierEasing(0.43f, 0.1f, -0.2f, 1f)
            private var startingWindowBackgroundAlpha = if (this@ThemedActivity.isOpaque()) 255 else 0
            private var initialized = false

            private fun handleInit(): Boolean {
                if (initialized) {
                    return true
                }

                if (this@ThemedActivity.backgroundAnimator?.isRunning == true) {
                    this@ThemedActivity.backgroundAnimator?.pause()
                }

                val root = this@ThemedActivity.getRoot()
                if (root != null) {
                    root.animate().cancel()

                    startingWindowBackgroundAlpha = if (this@ThemedActivity.isActivityTransitionRunning()) {
                        if (this@ThemedActivity.isOpaque()) 255 else 0
                    } else {
                        this@ThemedActivity.windowBackground.getAlpha()
                    }

                    initialized = true
                    return true
                }

                return false
            }

            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                if (this@ThemedActivity.isAffectedByBackGesture() &&
                    !this@ThemedActivity.isActivityTransitionRunning()
                ) {
                    handleInit()
                }
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                if (this@ThemedActivity.isAffectedByBackGesture() && handleInit()) {
                    val progress = cubicBezier.transform(backEvent.progress)

                    if (!this@ThemedActivity.isActivityTransitionRunning()) {
                        val root = this@ThemedActivity.getRoot()

                        if (root != null) {
                            this@ThemedActivity.windowBackground.setAlpha(
                                (Math.max(1f - progress * 2, 0f) * 255).toInt()
                            )

                            root.setPivotY(backEvent.touchY / 1.3f)
                            var deltaX = 0f
                            if (backEvent.swipeEdge == BackEventCompat.EDGE_LEFT) {
                                deltaX = backEvent.touchX
                            } else if (backEvent.swipeEdge == BackEventCompat.EDGE_RIGHT) {
                                deltaX = backEvent.touchX - root.getWidth()
                            }
                            root.setPivotX(deltaX * 0.3f + root.getWidth() / 2f)

                            root.setScaleX(1f - progress * 0.15f)
                            root.setTranslationY(root.getHeight() / 10f * progress)
                            root.setScaleY(1f - progress * 0.15f)

                            val expProgress = Math.pow(progress.toDouble(), 0.3).toFloat()
                            this@ThemedActivity.roundedCornerBackground.setCornerRadius(
                                Measurements.dpToPx(10f).toFloat() +
                                    expProgress * Measurements.dpToPx(20f).toFloat()
                            )
                            this@ThemedActivity.uiInsetBackgroundAnimator.setInsets(
                                0,
                                (Measurements.getSysUIHeight() * expProgress).toInt(),
                                0,
                                (Measurements.getNavHeight() * expProgress).toInt()
                            )
                        }
                    }

                    BackGestureEventBus.getInstance().postEvent(
                        BackEvent(
                            BackEventType.BACK_PROGRESS,
                            progress,
                            this@ThemedActivity.javaClass
                        )
                    )
                }
            }

            override fun handleOnBackCancelled() {
                if (!initialized) {
                    return
                }

                if (this@ThemedActivity.isAffectedByBackGesture()) {
                    if (!this@ThemedActivity.isActivityTransitionRunning()) {
                        val root = this@ThemedActivity.getRoot()

                        if (root != null) {
                            val alpha = this@ThemedActivity.windowBackground.getAlpha()

                            root.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(Animation.MEDIUM.getDuration().toLong())
                                .setUpdateListener { animation ->
                                    val fraction = animation.getAnimatedFraction()

                                    this@ThemedActivity.windowBackground.setAlpha(
                                        alpha + ((startingWindowBackgroundAlpha - alpha) * fraction).toInt()
                                    )
                                }
                                .withEndAction {
                                    this@ThemedActivity.windowBackground.setAlpha(
                                        startingWindowBackgroundAlpha
                                    )
                                }
                        }
                    }

                    BackGestureEventBus.getInstance().postEvent(
                        BackEvent(
                            BackEventType.BACK_CANCELLED,
                            this@ThemedActivity.javaClass
                        )
                    )
                }

                initialized = false
            }

            override fun handleOnBackPressed() {
                if (initialized) {
                    if (this@ThemedActivity.backgroundAnimator?.isRunning == true) {
                        this@ThemedActivity.backgroundAnimator?.pause()
                    }

                    this@ThemedActivity.backgroundAnimator = ValueAnimator.ofInt(
                        this@ThemedActivity.windowBackground.getAlpha(),
                        0
                    )
                    this@ThemedActivity.backgroundAnimator!!.setDuration(
                        Animation.BRIEF.getDuration().toLong()
                    )
                    this@ThemedActivity.backgroundAnimator!!.addUpdateListener {
                        this@ThemedActivity.windowBackground.setAlpha(
                            this@ThemedActivity.backgroundAnimator!!.getAnimatedValue() as Int
                        )
                    }
                    this@ThemedActivity.backgroundAnimator!!.start()
                }

                if (this@ThemedActivity.isAffectedByBackGesture()) {
                    this@ThemedActivity.finishAfterTransition()

                    BackGestureEventBus.getInstance().postEvent(
                        BackEvent(
                            BackEventType.BACK_COMPLETED,
                            this@ThemedActivity.javaClass
                        )
                    )
                }

                initialized = false
            }
        })

        super.onCreate(savedInstanceState)
    }

    protected override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        val window = getWindow()

        if (window != null && isOpaque()) {
            getRoot()!!.setBackground(uiInsetBackgroundAnimator)

            if (isActivityTransitionRunning()) {
                if (backgroundAnimator?.isRunning == true) {
                    backgroundAnimator?.pause()
                }

                backgroundAnimator = ValueAnimator.ofFloat(30f, 0f)
                backgroundAnimator!!.setInterpolator(AccelerateInterpolator(2f))
                backgroundAnimator!!.setDuration(
                    (
                        Animation.EXTENDED.getDuration() *
                            Measurements.getTransitionAnimationScale() /
                            Measurements.getAnimatorDurationScale()
                        ).toInt().toLong()
                )
                backgroundAnimator!!.addUpdateListener { animation ->
                    roundedCornerBackground.setCornerRadius(
                        Measurements.dpToPx(animation.getAnimatedValue() as Float).toFloat()
                    )
                }
                backgroundAnimator!!.start()

                val transition = window.getEnterTransition()!!
                transition.addListener(object : TransitionListenerAdapter() {
                    override fun onTransitionEnd(transition: Transition) {
                        assignActualBackgroundColor(window)
                        transition.removeListener(this)
                    }

                    override fun onTransitionCancel(transition: Transition) {
                        assignActualBackgroundColor(window)
                        transition.removeListener(this)
                    }
                })
            } else {
                assignActualBackgroundColor(window)
            }
        }
    }

    private fun assignActualBackgroundColor(window: Window) {
        roundedCornerBackground.setCornerRadius(Measurements.dpToPx(0f).toFloat())
        windowBackground = ColorDrawable(backgroundColor)

        // If the window background, when set, is completely opaque (alpha 255),
        // The window will treat every alpha value for the wallpaper as black
        windowBackground.setAlpha(0)
        window.setBackgroundDrawable(windowBackground)
        windowBackground.setAlpha(255)
    }

    protected override fun onResume() {
        val window = getWindow()

        if (window != null) {
            UiUtils.makeSysUITransparent(window)
        }

        super.onResume()
    }

    protected override fun onDestroy() {
        super.onDestroy()

        activityResultListeners.clear()
        requestPermissionResultListeners.clear()
    }

    override fun onConfigurationChanged(configuration: Configuration) {
        if (Measurements.wereTaken()) {
            Measurements.remeasure(getRoot())
        }

        super.onConfigurationChanged(configuration)
    }

    protected open fun getRoot(): ViewGroup? {
        return findViewById<ViewGroup>(android.R.id.content)!!.getChildAt(0) as ViewGroup?
    }

    override fun getApplicationContext(): Stario {
        return super.getApplicationContext() as Stario
    }

    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
        throw RuntimeException("Use the application context to retrieve shared preferences.")
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return allowTouches && super.dispatchTouchEvent(ev)
    }

    open fun setTouchEnabled(enabled: Boolean) {
        allowTouches = enabled
    }

    open fun isTouchEnabled(): Boolean {
        return allowTouches
    }

    open fun getThemeType(): Theme {
        return currentTheme
    }

    open fun getThemeResourceId(): Int {
        val nightModeFlags = getResources().getConfiguration().uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkModeOn = nightModeFlags == Configuration.UI_MODE_NIGHT_YES ||
            themePreferences.getBoolean(FORCE_DARK, false)

        return if (isDarkModeOn) currentTheme.getDarkResourceID() else currentTheme.getLightResourceID()
    }

    open fun getAttributeData(@AttrRes attr: Int): Int {
        return getAttributeData(currentTheme, attr)
    }

    open fun getAttributeData(@AttrRes attr: Int, forceDark: Boolean): Int {
        return getAttributeData(currentTheme, attr, forceDark)
    }

    open fun getAttributeData(theme: Theme, @AttrRes attr: Int): Int {
        return getAttributeData(theme, attr, false)
    }

    open fun getAttributeData(theme: Theme, @AttrRes attr: Int, forceDark: Boolean): Int {
        val typedValue = TypedValue()

        val wrappedTheme = getThemeFor(theme, forceDark)
        wrappedTheme.resolveAttribute(attr, typedValue, true)

        return typedValue.data
    }

    open fun getTheme(forceDark: Boolean): Resources.Theme {
        return getThemeFor(currentTheme, forceDark)
    }

    private fun getThemeFor(theme: Theme, forceDark: Boolean): Resources.Theme {
        val nightModeFlags = getResources().getConfiguration().uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkModeOn = nightModeFlags == Configuration.UI_MODE_NIGHT_YES ||
            themePreferences.getBoolean(FORCE_DARK, false)
        val wrapper = if (isDarkModeOn || forceDark) {
            ContextThemeWrapper(getApplicationContext(), theme.getDarkResourceID())
        } else {
            ContextThemeWrapper(getApplicationContext(), theme.getLightResourceID())
        }

        return wrapper.getTheme()
    }

    protected abstract fun isOpaque(): Boolean

    protected abstract fun isAffectedByBackGesture(): Boolean

    protected override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        for (resultListener in activityResultListeners.values) {
            if (resultListener != null) {
                resultListener.onResult(resultCode, data)
            }
        }
    }

    open fun addOnActivityResultListener(
        configurationCode: Int,
        listener: OnActivityResult?
    ): Boolean {
        if (listener != null) {
            return activityResultListeners.putIfAbsent(configurationCode, listener) == null
        }

        return false
    }

    open fun removeOnActivityResultListener(configurationCode: Int) {
        activityResultListeners.remove(configurationCode)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val listener = requestPermissionResultListeners.remove(requestCode)
        if (listener != null) {
            listener.onResult(grantResults)
        }
    }

    open fun requestPermissions(
        permissions: Array<String>,
        listener: OnPermissionRequestResult?
    ) {
        var granted = true

        for (permission in permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                granted = false
            }
        }

        if (!granted) {
            requestCode = (requestCode + 1) % 1_000_000

            ActivityCompat.requestPermissions(this, permissions, requestCode)
            requestPermissionResultListeners[requestCode] = listener
        } else {
            val results = IntArray(permissions.size)
            Arrays.fill(results, PackageManager.PERMISSION_GRANTED)

            listener!!.onResult(results)
        }
    }

    fun interface OnActivityResult {
        fun onResult(resultCode: Int, data: Intent?)
    }

    fun interface OnPermissionRequestResult {
        fun onResult(grantResults: IntArray)
    }

    companion object {
        const val THEME = "com.stario.THEME"
        const val FORCE_DARK = "com.stario.FORCE_DARK"

        private var requestCode = 0
    }
}
