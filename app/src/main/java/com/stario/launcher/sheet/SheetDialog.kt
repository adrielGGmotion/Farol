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

package com.stario.launcher.sheet

import android.annotation.SuppressLint
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.constraintlayout.widget.ConstraintLayout
import com.stario.launcher.preferences.Vibrations
import com.stario.launcher.sheet.behavior.SheetBehavior
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.dialogs.PersistentFullscreenDialog
import com.stario.launcher.ui.utils.UiUtils
import com.stario.launcher.utils.Utils

abstract class SheetDialog(activity: ThemedActivity, theme: Int) : PersistentFullscreenDialog(activity, theme, true) {
    private var dispatchedMotionEventToCoordinator = false
    private var shouldDispatchMotionEventsToParent = false
    private var dispatchMotionEventsToParent = false
    private var slideListener: OnSlideListener? = null
    private var receivedMoveEvent = false

    @get:JvmName("getBehaviorField")
    @set:JvmName("setBehaviorField")
    protected lateinit var behavior: SheetBehavior<ConstraintLayout>

    @get:JvmName("getSheetField")
    @set:JvmName("setSheetField")
    protected lateinit var sheet: ConstraintLayout

    override fun setContentView(view: View) {
        super.setContentView(wrapInSheet(view, null))
    }

    override fun setContentView(view: View, params: ViewGroup.LayoutParams?) {
        super.setContentView(wrapInSheet(view, params))
    }

    override fun cancel() = Unit

    override fun showDialog(): Boolean = superShow()

    @SuppressLint("ClickableViewAccessibility")
    private fun wrapInSheet(sourceView: View?, params: ViewGroup.LayoutParams?): View {
        val container = getContainer()
        val view = sourceView ?: layoutInflater.inflate(0, container, false)

        sheet.removeAllViews()

        if (params == null) {
            sheet.addView(view)
        } else {
            sheet.addView(view, params)
        }

        sheet.setOnTouchListener { _, _ -> true }

        window?.let(UiUtils::enforceLightSystemUI)

        behavior.addSheetCallback(object : SheetBehavior.SheetCallback {
            private var wasCollapsed = true

            override fun onStateChanged(sheet: View, newState: Int) {
                if (newState == SheetBehavior.STATE_COLLAPSED) {
                    hide()

                    wasCollapsed = true
                    container.intercept(SheetCoordinator.ALL)
                } else if (newState == SheetBehavior.STATE_EXPANDED || newState == SheetBehavior.STATE_SETTLING) {
                    container.intercept(SheetCoordinator.OWN)

                    if (newState == SheetBehavior.STATE_EXPANDED) {
                        shouldDispatchMotionEventsToParent = false
                        dispatchMotionEventsToParent = false
                    }

                    window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                }
            }

            override fun onSettleToState(sheet: View, stateToSettle: Int) {
                shouldDispatchMotionEventsToParent = stateToSettle == SheetBehavior.STATE_COLLAPSED
            }

            override fun onSlide(sheet: View, slideOffset: Float) {
                if (slideOffset >= 0.5f) {
                    if (wasCollapsed) {
                        Vibrations.getInstance().vibrate()
                    }

                    wasCollapsed = false
                }

                slideListener?.onSlide(slideOffset)

                if (slideOffset != 0f && !isShowing) {
                    if (showDialog()) {
                        behavior.invalidate()
                    }
                } else {
                    setDimmingFactor(Utils.getGenericInterpolatedValue(slideOffset.toDouble()).toFloat())
                    val alpha = slideOffset * 2 - 1f

                    if (alpha > 0) {
                        sheet.alpha = alpha
                        sheet.visibility = View.VISIBLE
                    } else {
                        if (shouldDispatchMotionEventsToParent) {
                            dispatchMotionEventsToParent = true
                        }

                        sheet.visibility = View.INVISIBLE
                    }
                }
            }
        })

        return container
    }

    fun onMotionEvent(event: MotionEvent): Boolean {
        val coordinator = getContainer()

        try {
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                if (!receivedMoveEvent) {
                    hide()
                }

                val result = coordinator.dispatchTouchEvent(event)

                dispatchedMotionEventToCoordinator = false
                dispatchMotionEventsToParent = false
                receivedMoveEvent = false

                return result
            } else if (!dispatchedMotionEventToCoordinator) {
                if (!isShowing) {
                    showDialog()

                    return false
                }

                event.action = MotionEvent.ACTION_DOWN
            }

            if (event.action == MotionEvent.ACTION_MOVE) {
                receivedMoveEvent = true
            }

            dispatchedMotionEventToCoordinator = behavior.isDragHelperInstantiated() && coordinator.dispatchTouchEvent(event)

            return dispatchedMotionEventToCoordinator
        } catch (exception: IllegalArgumentException) {
            Log.e(TAG, "onMotionEvent: " + exception.message)

            dispatchedMotionEventToCoordinator = false
            dispatchMotionEventsToParent = false
            receivedMoveEvent = false
        }

        return false
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (dispatchMotionEventsToParent) {
            val newEvent = MotionEvent.obtain(ev)
            newEvent.setLocation(ev.rawX, ev.rawY)

            ownerActivity?.dispatchTouchEvent(newEvent)

            newEvent.recycle()

            return true
        }

        return try {
            super.dispatchTouchEvent(ev)
        } catch (exception: RuntimeException) {
            Log.e(TAG, "dispatchTouchEvent: " + exception.message)
            false
        }
    }

    fun setOnSlideListener(listener: OnSlideListener?) {
        slideListener = listener
    }

    fun getBehavior(): SheetBehavior<ConstraintLayout> = behavior

    fun getSheetContainer(): SheetCoordinator = getContainer()

    fun interface OnSlideListener {
        fun onSlide(slideOffset: Float)
    }

    protected abstract fun getContainer(): SheetCoordinator

    abstract fun getType(): SheetType

    companion object {
        private const val TAG = "SheetDialog"
    }
}
