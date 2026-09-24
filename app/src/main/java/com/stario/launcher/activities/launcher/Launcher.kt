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

package com.stario.launcher.activities.launcher

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.math.MathUtils
import com.stario.launcher.R
import com.stario.launcher.activities.launcher.sheets.LauncherSheets
import com.stario.launcher.activities.launcher.widgets.ClockWidget
import com.stario.launcher.activities.launcher.widgets.SearchWidget
import com.stario.launcher.activities.launcher.widgets.glance.Glance
import com.stario.launcher.activities.launcher.widgets.glance.GlanceDialogExtension
import com.stario.launcher.activities.launcher.widgets.glance.extensions.calendar.Calendar
import com.stario.launcher.activities.launcher.widgets.glance.extensions.media.Media
import com.stario.launcher.activities.launcher.widgets.glance.extensions.weather.Weather
import com.stario.launcher.activities.launcher.widgets.pins.PinnedCategory
import com.stario.launcher.activities.settings.Settings
import com.stario.launcher.preferences.Vibrations
import com.stario.launcher.sheet.SheetsFocusController
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.Measurements
import com.stario.launcher.ui.back.BackEvent
import com.stario.launcher.ui.back.BackEventType
import com.stario.launcher.ui.back.BackGestureEventBus
import com.stario.launcher.ui.common.grid.DynamicGridLayout
import com.stario.launcher.ui.common.lock.ClosingAnimationView
import com.stario.launcher.ui.popup.PopupMenu
import com.stario.launcher.ui.utils.HomeWatcher
import com.stario.launcher.ui.utils.UiUtils
import com.stario.launcher.ui.utils.animation.Animation
import com.stario.launcher.ui.utils.animation.WallpaperAnimator
import com.stario.launcher.utils.Utils

open class Launcher : ThemedActivity() {
    private var backEventListener: BackGestureEventBus.BackEventListener? = null
    private lateinit var screenOnReceiver: BroadcastReceiver
    private lateinit var controller: SheetsFocusController
    private lateinit var killReceiver: BroadcastReceiver
    private lateinit var pinnedCategory: PinnedCategory
    private lateinit var container: DynamicGridLayout
    private lateinit var main: ClosingAnimationView
    private lateinit var searchWidget: SearchWidget
    private lateinit var clockWidget: ClockWidget
    private lateinit var homeWatcher: HomeWatcher
    private var showWhenLocked = false
    private lateinit var statusBarContrast: View
    private lateinit var navBarContrast: View
    private lateinit var decorView: View
    private lateinit var glance: Glance

    override fun onCreate(savedInstanceState: Bundle?) {
        val intent = Intent(ACTION_KILL_TASK)
        intent.putExtra(INTENT_KILL_TASK_ID_EXTRA, taskId)
        intent.setPackage(packageName)
        sendBroadcast(intent)

        killReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val task = intent.getIntExtra(INTENT_KILL_TASK_ID_EXTRA, taskId)
                if (task != taskId) finishAndRemoveTask()
            }
        }

        homeWatcher = HomeWatcher(this)
        homeWatcher.setOnHomePressedListener {
            setContrastVisibility(View.GONE)
            setRearrangeable(false)
        }

        ContextCompat.registerReceiver(this, killReceiver, IntentFilter(ACTION_KILL_TASK), ContextCompat.RECEIVER_EXPORTED)

        val window = window
        window.clearFlags(WindowManager.LayoutParams.FLAG_SPLIT_TOUCH)
        UiUtils.enforceLightSystemUI(window)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.launcher)

        controller = findViewById(R.id.controller)
        container = findViewById(R.id.container)
        main = findViewById(R.id.main)
        decorView = window.decorView

        Measurements.measure(requireNotNull(getRoot())) { insets ->
            if (Measurements.isLandscape()) {
                container.setPadding(0, Measurements.getSysUIHeight(), 0, Measurements.getNavHeight())
            } else {
                container.setPadding(
                    Measurements.getDefaultPadding(),
                    Measurements.getSysUIHeight() + Measurements.getDefaultPadding(),
                    Measurements.getDefaultPadding(),
                    Measurements.getNavHeight() + Measurements.getDefaultPadding()
                )
            }
            insets
        }

        if (Utils.isMinimumSDK(Build.VERSION_CODES.TIRAMISU)) {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    setRearrangeable(false)
                }
            })
        }

        statusBarContrast = findViewById(R.id.status_bar_contrast)
        navBarContrast = findViewById(R.id.navigation_bar_contrast)
        statusBarContrast.setOnTouchListener { _, _ -> false }
        navBarContrast.setOnTouchListener { _, _ -> false }

        Measurements.addNavListener { value ->
            navBarContrast.layoutParams.height = ((value ?: return@addNavListener) * 1.5f).toInt()
            navBarContrast.requestLayout()
        }
        Measurements.addStatusBarListener { value ->
            statusBarContrast.layoutParams.height = ((value ?: return@addStatusBarListener) * 1.5f).toInt()
            statusBarContrast.requestLayout()
        }

        UiUtils.Notch.applyNotchMargin(controller, UiUtils.Notch.Treatment.CENTER)
        controller.setOnLongClickListener {
            Vibrations.getInstance().vibrate()
            displayLauncherOptions(this, controller)
            true
        }

        screenOnReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                main.reset()
            }
        }
        if (Utils.isMinimumSDK(Build.VERSION_CODES.TIRAMISU)) {
            registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        }

        LauncherSheets.attach(this, ::animateSheet)
        attachPinnedCategory(container)
        attachGlance(container)
        attachSearch(container)
        attachClock(container)
    }

    private fun attachPinnedCategory(container: DynamicGridLayout) {
        pinnedCategory = PinnedCategory(this)
        pinnedCategory.attach(container,
            { controller.hideAllSheets() },
            { slideOffset -> animateSheet(slideOffset, false, false) })
    }

    private fun attachClock(container: DynamicGridLayout) {
        clockWidget = ClockWidget(this)
        clockWidget.attach(container)
    }

    private fun attachSearch(container: DynamicGridLayout) {
        searchWidget = SearchWidget(this)
        searchWidget.attach(container)
    }

    private fun attachGlance(container: DynamicGridLayout) {
        glance = Glance(this)
        glance.attach(container)
        val listener = GlanceDialogExtension.TransitionListener { slideOffset ->
            animateSheet(slideOffset, false, false)
        }
        glance.attachViewExtension(Calendar())
        glance.attachDialogExtension(Media(), listener)
        glance.attachDialogExtension(Weather(), listener)
    }

    open fun displayLauncherOptions(activity: Launcher, controller: SheetsFocusController) {
        val menu = PopupMenu(activity, false)
        val resources: Resources = activity.resources

        menu.add(PopupMenu.Item(resources.getString(R.string.settings),
            ResourcesCompat.getDrawable(resources, R.drawable.ic_settings, activity.theme)) { view ->
            view.post {
                val intent = Intent(activity, Settings::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(activity).toBundle())

                backEventListener = object : BackGestureEventBus.BackEventListener(Settings::class.java) {
                    override fun onBackEvent(event: BackEvent) {
                        val root = getRoot() ?: return
                        when (event.type) {
                            BackEventType.BACK_PROGRESS -> {
                                root.animate().cancel()
                                root.visibility = View.VISIBLE
                                root.alpha = event.progress
                                root.scaleX = 0.9f + 0.1f * event.progress
                                root.scaleY = 0.9f + 0.1f * event.progress
                            }
                            BackEventType.BACK_CANCELLED -> {
                                root.visibility = View.INVISIBLE
                                root.animate().alpha(0f).scaleY(0.9f).scaleX(0.9f)
                                    .setDuration(Animation.SHORT.getDuration().toLong())
                                    .withEndAction { root.visibility = View.INVISIBLE }
                            }
                            else -> Unit
                        }
                    }
                }
                backEventListener?.let { BackGestureEventBus.getInstance().addListener(it) }
            }
        })

        menu.add(PopupMenu.Item(resources.getString(R.string.rearrange),
            ResourcesCompat.getDrawable(resources, R.drawable.ic_move, activity.theme)) { view ->
            view.post {
                setRearrangeable(true)
                menu.dismiss()
            }
        })

        menu.add(PopupMenu.Item(resources.getString(R.string.wallpaper),
            ResourcesCompat.getDrawable(resources, R.drawable.ic_palette, activity.theme)) { view ->
            view.post {
                val intent = Intent(Intent.ACTION_SET_WALLPAPER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("com.android.wallpaper.LAUNCH_SOURCE", "app_launched_launcher")
                activity.startActivity(intent, ActivityOptions.makeScaleUpAnimation(
                    view, 0, 0, view.width, view.height).toBundle())
            }
        })

        menu.showAtLocation(activity, controller, controller.getLastX(), controller.getLastY(),
            PopupMenu.PIVOT_CENTER_HORIZONTAL, false)
    }

    open fun getSheetsController(): SheetsFocusController = controller

    private fun animateSheet(slideOffset: Float) {
        animateSheet(slideOffset, true, true)
    }

    private fun animateSheet(offset: Float, scale: Boolean, animateOpacity: Boolean) {
        var slideOffset = if (offset.isNaN()) 0f else offset
        controller.animate().cancel()

        val value = slideOffset < 0.5f || getAttributeData(android.R.attr.windowLightStatusBar) == 0
        controller.updateSheetSystemUI(value)
        glance.updateSheetSystemUI(value)

        val targetAlpha = 1f - slideOffset * slideOffset * 4f
        slideOffset *= slideOffset
        if (!animateOpacity) {
            controller.alpha = 1f
            controller.visibility = View.VISIBLE
        } else if (targetAlpha > 0) {
            controller.alpha = Math.sqrt(targetAlpha.toDouble()).toFloat()
            if (scale) {
                val scaleFactor = 1f - slideOffset / 3
                controller.scaleY = scaleFactor
                controller.scaleX = scaleFactor
            }
            controller.visibility = View.VISIBLE
        } else {
            controller.visibility = View.INVISIBLE
        }
        updateWallpaperZoom(slideOffset)
    }

    private fun updateWallpaperZoom(value: Float) {
        if (decorView.windowToken != null && Utils.isMinimumSDK(Build.VERSION_CODES.R)) {
            WallpaperAnimator.updateZoom(this, MathUtils.clamp(value, 0f, 1f))
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenOnReceiver)
        } catch (exception: Exception) {
            Log.e("Launcher", "onDestroy: Screen On receiver was not registered.")
        }
        try {
            unregisterReceiver(killReceiver)
        } catch (exception: Exception) {
            Log.e("Launcher", "onDestroy: Kill receiver was not registered.")
        }
        pinnedCategory.detach()
        searchWidget.detach()
        clockWidget.detach()
        super.onDestroy()
    }

    override fun onResume() {
        glance.update()
        updateWallpaperZoom(0f)
        val root = requireNotNull(getRoot())
        root.visibility = View.VISIBLE
        root.animate().alpha(1f).scaleY(1f).scaleX(1f).setDuration(Animation.SHORT.getDuration().toLong())
        BackGestureEventBus.getInstance().removeListener(backEventListener)
        backEventListener = null
        controller.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(Animation.LONG.getDuration().toLong())
            .setInterpolator(DecelerateInterpolator(2f))
        super.onResume()
    }

    override fun onEnterAnimationComplete() {
        super.onEnterAnimationComplete()
        setContrastVisibility(View.VISIBLE)
    }

    private fun setContrastVisibility(visible: Int) {
        if (visible == View.VISIBLE) {
            navBarContrast.animate().alpha(1f).setDuration(Animation.LONG.getDuration().toLong())
            statusBarContrast.animate().alpha(1f).setDuration(Animation.LONG.getDuration().toLong())
        } else {
            navBarContrast.animate().alpha(0f).setDuration(0)
            statusBarContrast.animate().alpha(0f).setDuration(0)
        }
    }

    private fun setRearrangeable(value: Boolean) {
        if (::controller.isInitialized && ::container.isInitialized) {
            container.setRearrangeable(value)
            controller.setControllerEnabled(!value)
        }
    }

    override fun onStart() {
        super.onStart()
        homeWatcher.startWatch()
    }

    override fun onStop() {
        setContrastVisibility(View.GONE)
        setRearrangeable(false)
        homeWatcher.stopWatch()
        if (showWhenLocked) moveTaskToBack(true)
        super.onStop()

        main.reset()
        if (showWhenLocked) {
            setShowWhenLocked(false)
            startActivity(Intent(this, Launcher::class.java))
        }
        controller.alpha = 0f
        controller.scaleX = 0.9f
        controller.scaleY = 0.9f
    }

    override fun setShowWhenLocked(showWhenLocked: Boolean) {
        this.showWhenLocked = showWhenLocked
        super.setShowWhenLocked(showWhenLocked)
    }

    override fun onPause() {
        updateWallpaperZoom(1f)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) setContrastVisibility(View.VISIBLE)
        super.onWindowFocusChanged(hasFocus)
    }

    override fun hasWindowFocus(): Boolean =
        super.hasWindowFocus() || !(controller.hasSheetFocus() || glance.hasFocus())

    protected override fun isOpaque() = false

    protected override fun isAffectedByBackGesture() = false

    @SuppressLint("MissingSuperCall", "GestureBackNavigation")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        setRearrangeable(false)
    }

    companion object {
        const val INTENT_KILL_TASK_ID_EXTRA = "com.stario.launcher.INTENT_KILL_TASK_ID_EXTRA"
        const val ACTION_KILL_TASK = "com.stario.launcher.ACTION_KILL_TASK"
    }
}
