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

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.Window
import android.view.WindowManager
import android.widget.OverScroller
import androidx.core.view.WindowCompat
import androidx.customview.widget.ViewDragHelper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.stario.launcher.R
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.Measurements
import com.stario.launcher.ui.keyboard.KeyboardHeightProvider
import com.stario.launcher.ui.utils.HomeWatcher
import com.stario.launcher.ui.utils.UiUtils
import com.stario.launcher.ui.utils.animation.KeyboardAnimationHelper
import com.stario.launcher.utils.Utils
import java.lang.reflect.Field
import java.util.Objects

abstract class ActionDialog(activity: ThemedActivity) : BottomSheetDialog(activity) {
    companion object {
        private const val DIMMING_MULTIPLIER = 0.7f
    }

    @JvmField
    protected val activity: ThemedActivity = activity

    private val homeWatcher: HomeWatcher
    private var dimmingController: DialogBackgroundDimmingController.DimmingController? = null
    private var heightProvider: KeyboardHeightProvider? = null
    private var canCollapse = false
    private var root: View? = null

    init {
        homeWatcher = HomeWatcher(activity)
        homeWatcher.setOnHomePressedListener {
            val behavior = getBehavior()
            behavior.setDraggable(false)
            behavior.setState(BottomSheetBehavior.STATE_HIDDEN)

            UiUtils.hideKeyboard(root!!)
        }

        activity.addOnConfigurationChangedListener {
            heightProvider?.dismiss()
            dismissWithoutSheetAnimation()
        }

        val lifecycle = activity.lifecycle
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                lifecycle.removeObserver(this)
            }

            override fun onPause(owner: LifecycleOwner) {
                heightProvider?.dismiss()
                dismissWithoutSheetAnimation()
            }
        })
    }

    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val behavior = getBehavior()
        behavior.setSkipCollapsed(true)

        val inflater = activity.layoutInflater
        root = inflater.inflate(R.layout.pop_up_root, android.widget.FrameLayout(activity), false)
        val rootView = root!!
        val content: ViewGroup? = rootView.findViewById(R.id.content)
        val contentView = content!!
        contentView.addView(inflateContent(activity.layoutInflater, contentView))

        heightProvider = KeyboardHeightProvider(activity)

        if (Utils.isMinimumSDK(Build.VERSION_CODES.R)) {
            KeyboardAnimationHelper.configureKeyboardAnimator(
                Objects.requireNonNull(getWindow())!!.decorView,
                heightProvider!!,
                object : KeyboardAnimationHelper.ContentTranslationListener {
                    override fun translate(translation: Float) {
                        contentView.setPadding(
                            contentView.paddingLeft,
                            contentView.paddingTop,
                            contentView.paddingRight,
                            (Measurements.getNavHeight() - translation).toInt()
                        )
                    }
                }
            )

            heightProvider!!.addKeyboardHeightListener(object : KeyboardHeightProvider.KeyboardHeightListener {
                override fun onKeyboardHeightChanged(height: Int) {
                    behavior.setDraggable(height == 0)
                }
            })
        } else {
            heightProvider!!.addKeyboardHeightListener(object : KeyboardHeightProvider.KeyboardHeightListener {
                override fun onKeyboardHeightChanged(height: Int) {
                    contentView.setPadding(
                        contentView.paddingLeft,
                        contentView.paddingTop,
                        contentView.paddingRight,
                        Measurements.getNavHeight() + height
                    )

                    behavior.setDraggable(height == 0)
                }
            })
        }

        Measurements.addNavListener { value ->
            contentView.setPadding(
                contentView.paddingLeft,
                contentView.paddingTop,
                contentView.paddingRight,
                value!!
            )
        }

        rootView.setOnClickListener { dismiss() }
        contentView.setOnClickListener { }

        setContentView(rootView)

        (rootView.parent as View).setBackgroundColor(Color.TRANSPARENT)
        Measurements.addStatusBarListener { value ->
            val params = rootView.layoutParams as ViewGroup.MarginLayoutParams
            params.topMargin = value!!
        }

        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED ||
                    newState == BottomSheetBehavior.STATE_HALF_EXPANDED ||
                    newState == BottomSheetBehavior.STATE_DRAGGING
                ) {
                    canCollapse = true
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (slideOffset <= -1 && canCollapse) {
                    dismissFromSheet()

                    heightProvider?.dismiss()
                    canCollapse = false
                }

                val adjustedSlideOffset = Math.min(slideOffset, 0f)
                val window: Window? = getWindow()

                if (!activity.isDestroyed && !activity.isFinishing && window != null) {
                    rootView.scaleX = 1 + adjustedSlideOffset * 0.08f
                    rootView.scaleY = 1 + adjustedSlideOffset * 0.08f
                    dimmingController?.setFactor(1 + adjustedSlideOffset)
                }
            }
        })
    }

    private fun dismissFromSheet() {
        super.dismiss()
    }

    override fun onAttachedToWindow() {
        val window: Window? = getWindow()

        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)

            if (blurBehind() && dimmingController == null) {
                dimmingController = DialogBackgroundDimmingController.attach(
                    activity,
                    this,
                    blurBehind(),
                    DIMMING_MULTIPLIER
                )
            }

            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            window.setWindowAnimations(R.style.ActionDialogAnimations)
            window.decorView.visibility = View.INVISIBLE

            val behavior = getBehavior()
            behavior.setState(BottomSheetBehavior.STATE_HIDDEN)
        }

        homeWatcher.startWatch()

        val rootView = root!!
        rootView.post {
            val frame: ViewParent? = rootView.parent

            if (frame != null) {
                fitToBottomInset(frame as View, false)

                val coordinator = frame.parent
                if (coordinator != null) {
                    fitToBottomInset(coordinator as View, false)

                    val container = coordinator.parent
                    if (container != null) {
                        fitToBottomInset(container as View, false)
                    }
                }
            }

            rootView.post {
                if (window != null) {
                    window.decorView.visibility = View.VISIBLE
                    getBehavior().setState(getDesiredInitialState())
                }
            }
        }
    }

    // hack to remove STATE_EXPANDED fit to system window jitter on layout pass
    override fun onDetachedFromWindow() {
        val frame: ViewParent? = root!!.parent

        homeWatcher.stopWatch()

        if (frame != null) {
            fitToBottomInset(frame as View, true)

            val coordinator = frame.parent
            if (coordinator != null) {
                fitToBottomInset(coordinator as View, true)

                val container = coordinator.parent
                if (container != null) {
                    fitToBottomInset(container as View, true)
                }
            }
        }

        super.onDetachedFromWindow()
    }

    private fun fitToBottomInset(view: View, fit: Boolean) {
        view.fitsSystemWindows = fit
        view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, 0)
    }

    override fun show() {
        super.show()

        val behavior = getBehavior()
        behavior.setDraggable(heightProvider!!.getKeyboardHeight() == 0)

        val rootView = root!!
        rootView.post {
            UiUtils.Notch.applyNotchMargin(rootView, UiUtils.Notch.Treatment.CENTER)
        }

        heightProvider?.start()
    }

    override fun hide() {
        dismiss()
    }

    private fun dismissWithoutSheetAnimation() {
        dismiss()

        // skip framework animation
        try {
            val helper = getViewDragHelper(getBehavior())
            if (helper != null) {
                val scroller = getScroller(helper)
                if (scroller != null) {
                    scroller.abortAnimation()
                }
            }
        } catch (exception: Exception) {
            Log.e("ActionDialog", "onAttachedToWindow: ", exception)
        }
    }

    override fun dismiss() {
        val behavior = getBehavior()

        if (heightProvider!!.getKeyboardHeight() > 0) {
            UiUtils.hideKeyboard(root!!)
        } else if (canCollapse ||
            behavior.getState() == BottomSheetBehavior.STATE_EXPANDED ||
            behavior.getState() == BottomSheetBehavior.STATE_HALF_EXPANDED
        ) {
            canCollapse = true
            getBehavior().setState(BottomSheetBehavior.STATE_HIDDEN)
        }
    }

    protected override fun onStop() {
        dismiss()
        super.onStop()
    }

    private fun getViewDragHelper(behavior: BottomSheetBehavior<*>): ViewDragHelper? {
        return try {
            val field: Field = BottomSheetBehavior::class.java.getDeclaredField("viewDragHelper")
            field.isAccessible = true
            field.get(behavior) as ViewDragHelper
        } catch (exception: Exception) {
            null
        }
    }

    private fun getScroller(viewDragHelper: ViewDragHelper): OverScroller? {
        return try {
            val field: Field = ViewDragHelper::class.java.getDeclaredField("mScroller")
            field.isAccessible = true
            field.get(viewDragHelper) as OverScroller
        } catch (exception: Exception) {
            null
        }
    }

    protected abstract fun inflateContent(inflater: LayoutInflater, parent: android.view.ViewGroup): View

    protected abstract fun getDesiredInitialState(): Int

    protected abstract fun blurBehind(): Boolean
}
