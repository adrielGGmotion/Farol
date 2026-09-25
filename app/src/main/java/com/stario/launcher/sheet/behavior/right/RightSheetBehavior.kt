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

package com.stario.launcher.sheet.behavior.right

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.math.MathUtils
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper
import com.stario.launcher.sheet.behavior.SheetBehavior
import com.stario.launcher.sheet.behavior.SheetDragHelper
import com.stario.launcher.ui.Measurements
import kotlin.math.abs
import androidx.core.view.isVisible

open class RightSheetBehavior<V : View>(context: Context, attrs: AttributeSet?) : SheetBehavior<V>(context, attrs) {
    private var rememberInterceptResult: Boolean? = null
    private var flung = false
    private var initialY = 0

    protected override fun calculateCollapsedOffset() {
        collapsedOffset = expandedOffset + Measurements.dpToPx(SheetBehavior.COLLAPSED_DELTA_DP.toFloat())
    }

    protected override fun calculateExpandedOffset() {
        viewRef?.get()?.let { expandedOffset = parentWidth - it.measuredWidth }
    }

    protected override fun dispatchOnSlide(child: V) {
        dispatchOnSlide(child.left)
    }

    protected override fun getPositionInParent(child: V): Int = child.left

    protected override fun offset(child: V, offset: Int) {
        ViewCompat.offsetLeftAndRight(child, offset)
    }

    protected override fun stopNestedScrollLogic(child: V) {
        if (flung) return
        if (child.left == expandedOffset) {
            setStateInternal(STATE_EXPANDED)
            return
        }

        val currentLeft = child.left
        val left: Int
        val targetState: Int
        if (lastNestedScroll > 0) {
            left = expandedOffset
            targetState = STATE_EXPANDED
        } else if (lastNestedScroll == 0) {
            if (currentLeft < collapsedOffset * 0.5 + expandedOffset * 0.5) {
                left = expandedOffset
                targetState = STATE_EXPANDED
            } else {
                left = collapsedOffset
                targetState = STATE_COLLAPSED
            }
        } else {
            left = collapsedOffset
            targetState = STATE_COLLAPSED
        }

        settleChildTo(child, targetState, left, child.top)
    }

    protected override fun nestedPreFlingLogic(child: V, xvel: Float, yvel: Float): Boolean {
        val currentLeft = child.left
        if (state == STATE_DRAGGING) {
            if (xvel > 0 && currentLeft < expandedOffset) {
                settleChildTo(child, STATE_EXPANDED, child.left, expandedOffset, xvel.toInt(), 0)
                flung = true
            } else if (xvel < 0 && currentLeft > collapsedOffset) {
                settleChildTo(child, STATE_COLLAPSED, child.left, collapsedOffset, xvel.toInt(), 0)
                flung = true
            }
        }
        return flung
    }

    protected override fun nestedPreScrollLogic(child: V, target: View, dx: Int, dy: Int, consumed: IntArray) {
        val currentLeft = child.left
        val newLeft = currentLeft - dx
        if (dx > 0) {
            if (newLeft < expandedOffset) {
                consumed[1] = currentLeft - expandedOffset
                ViewCompat.offsetLeftAndRight(child, -consumed[1])
                setStateInternal(STATE_EXPANDED)
            } else {
                if (!draggable) return
                consumed[1] = dx
                ViewCompat.offsetLeftAndRight(child, -dx)
                setStateInternal(STATE_DRAGGING)
            }
        } else if (dx < 0) {
            if (!target.canScrollHorizontally(-1)) {
                if (newLeft <= collapsedOffset) {
                    if (!draggable) return
                    consumed[1] = dx
                    ViewCompat.offsetLeftAndRight(child, -dx)
                    setStateInternal(STATE_DRAGGING)
                } else {
                    consumed[1] = currentLeft - collapsedOffset
                    ViewCompat.offsetLeftAndRight(child, -consumed[1])
                    setStateInternal(STATE_COLLAPSED)
                }
            }
        }
        lastNestedScroll = dx
    }

    protected override fun startNestedScrollLogic(axes: Int): Boolean {
        flung = true
        return (axes and ViewCompat.SCROLL_AXIS_HORIZONTAL) != 0
    }

    protected override fun touchEventLogic(child: V, event: MotionEvent) {
        val helper = dragHelper
        if (helper != null && abs(initial - event.x) > helper.getTouchSlop().toFloat()) {
            helper.captureChildView(child, event.getPointerId(event.actionIndex))
        }
    }

    protected override fun interceptTouchEventLogic(parent: CoordinatorLayout, child: V, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                if (ignoreEvents) {
                    ignoreEvents = false
                    return false
                }
            }
            MotionEvent.ACTION_DOWN -> {
                initial = event.x.toInt()
                initialY = event.y.toInt()
                rememberInterceptResult = null
                if (state != STATE_SETTLING) {
                    val scroll = nestedScrollingChildRef?.get()
                    if (scroll == null || parent.isPointInChildBounds(scroll, initial, initialY)) {
                        activePointerId = event.getPointerId(event.actionIndex)
                    }
                }
                ignoreEvents = activePointerId == MotionEvent.INVALID_POINTER_ID &&
                    !parent.isPointInChildBounds(child, initial, initialY)
            }
        }

        val absX = abs(initial - event.x)
        val absY = abs(initialY - event.y)
        val helper = dragHelper
        if (helper != null) {
            if (rememberInterceptResult == null && (absX > helper.getTouchSlop().toFloat() || absY > helper.getTouchSlop().toFloat())) {
                rememberInterceptResult = absX > absY
            }
            return rememberInterceptResult == true && initial - event.x < 0 && absX > helper.getTouchSlop().toFloat()
        }
        return false
    }

    protected override fun instantiateDragCallback(): SheetDragHelper.Callback = object : SheetDragHelper.Callback() {
        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            if (capture) return true
            if (state == STATE_DRAGGING) return false
            if (state == STATE_EXPANDED && activePointerId == pointerId) {
                val pager = pagerRef?.get()
                val adapter = pager?.adapter
                if (pager != null && adapter != null && pager.currentItem > 0) return false

                val scroll = nestedScrollingChildRef?.get()
                if (scroll != null && scroll.isVisible && scroll.canScrollHorizontally(-1)) return false
            }
            return viewRef?.get() === child
        }

        override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) {
            dispatchOnSlide(left)
        }

        override fun onViewDragStateChanged(state: Int) {
            if (state == ViewDragHelper.STATE_DRAGGING && draggable) setStateInternal(STATE_DRAGGING)
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            val left: Int
            val targetState: Int
            if (xvel == 0f || Measurements.dpToPx(
                    ViewConfiguration.get(releasedChild.context).scaledMinimumFlingVelocity +
                        (ViewConfiguration.get(releasedChild.context).scaledMaximumFlingVelocity - ViewConfiguration.get(releasedChild.context).scaledMinimumFlingVelocity) / 10f
                ).toFloat() > abs(xvel)
            ) {
                val currentLeft = releasedChild.left
                if (currentLeft < (expandedOffset + collapsedOffset) / 2) {
                    left = expandedOffset
                    targetState = STATE_EXPANDED
                } else {
                    left = collapsedOffset
                    targetState = STATE_COLLAPSED
                }
            } else if (xvel < 0) {
                left = expandedOffset
                targetState = STATE_EXPANDED
            } else {
                left = collapsedOffset
                targetState = STATE_COLLAPSED
            }
            settleChildTo(releasedChild, targetState, left, releasedChild.top)
        }

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int =
            MathUtils.clamp(left, expandedOffset, collapsedOffset)

        override fun getViewHorizontalDragRange(child: View): Int = collapsedOffset
    }

    protected override fun isFullySettled(sheet: V, state: Int): Boolean {
        if (state == STATE_COLLAPSED && sheet.left == collapsedOffset) return true
        if (state == STATE_EXPANDED && sheet.left == expandedOffset) return true
        return false
    }

    protected override fun settleToState(child: View, state: Int, animate: Boolean) {
        val left = if (state == STATE_COLLAPSED) collapsedOffset else if (state == STATE_EXPANDED) expandedOffset else throw IllegalArgumentException("Illegal state argument: $state")
        if (animate) settleChildTo(child, state, left, child.top) else moveChildTo(child, state, left, child.top)
    }
}
