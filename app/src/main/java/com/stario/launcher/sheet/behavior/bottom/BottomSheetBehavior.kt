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

package com.stario.launcher.sheet.behavior.bottom

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
import com.stario.launcher.ui.recyclers.overscroll.OverScrollEffect
import com.stario.launcher.ui.recyclers.overscroll.OverScrollRecyclerView
import kotlin.math.abs
import androidx.core.view.isVisible

open class BottomSheetBehavior<V : View>(context: Context, attrs: AttributeSet?) : SheetBehavior<V>(context, attrs) {
    private var rememberInterceptResult: Boolean? = null
    private var flung = false
    private var initialX = 0

    protected override fun calculateCollapsedOffset() {
        collapsedOffset = expandedOffset + Measurements.dpToPx(SheetBehavior.COLLAPSED_DELTA_DP.toFloat())
    }

    protected override fun calculateExpandedOffset() {
        expandedOffset = 0
    }

    protected override fun dispatchOnSlide(child: V) {
        dispatchOnSlide(child.top)
    }

    protected override fun getPositionInParent(child: V): Int = child.top

    protected override fun offset(child: V, offset: Int) {
        ViewCompat.offsetTopAndBottom(child, offset)
    }

    protected override fun stopNestedScrollLogic(child: V) {
        if (flung) return

        if (child.top == expandedOffset) {
            setStateInternal(STATE_EXPANDED)
            return
        }

        val currentTop = child.top
        val top: Int
        val targetState: Int

        if (lastNestedScroll > 0) {
            top = expandedOffset
            targetState = STATE_EXPANDED
        } else if (lastNestedScroll == 0) {
            if (currentTop < collapsedOffset * 0.5 + expandedOffset * 0.5) {
                top = expandedOffset
                targetState = STATE_EXPANDED
            } else {
                top = collapsedOffset
                targetState = STATE_COLLAPSED
            }
        } else {
            top = collapsedOffset
            targetState = STATE_COLLAPSED
        }

        settleChildTo(child, targetState, child.left, top)
    }

    protected override fun nestedPreFlingLogic(child: V, xvel: Float, yvel: Float): Boolean {
        val currentTop = child.top

        if (state == STATE_DRAGGING) {
            if (yvel > 0 && currentTop > expandedOffset) {
                settleChildTo(child, STATE_EXPANDED, child.left, expandedOffset, 0, yvel.toInt())
                flung = true
            } else if (yvel < 0 && currentTop < collapsedOffset) {
                settleChildTo(child, STATE_COLLAPSED, child.left, collapsedOffset, 0, yvel.toInt())
                flung = true
            }
        }

        return flung
    }

    protected override fun nestedPreScrollLogic(child: V, target: View, dx: Int, dy: Int, consumed: IntArray) {
        val currentTop = child.top
        val newTop = currentTop - dy

        if (dy > 0) {
            if (newTop < expandedOffset) {
                consumed[1] = currentTop - expandedOffset
                ViewCompat.offsetTopAndBottom(child, -consumed[1])
                setStateInternal(STATE_EXPANDED)
            } else {
                if (!draggable) return

                consumed[1] = dy
                ViewCompat.offsetTopAndBottom(child, -dy)
                setStateInternal(STATE_DRAGGING)
            }
        } else if (dy < 0) {
            if (!target.canScrollVertically(-1)) {
                if (newTop <= collapsedOffset) {
                    if (!draggable) return

                    consumed[1] = dy
                    ViewCompat.offsetTopAndBottom(child, -dy)
                    setStateInternal(STATE_DRAGGING)
                } else {
                    consumed[1] = currentTop - collapsedOffset
                    ViewCompat.offsetTopAndBottom(child, -consumed[1])
                    setStateInternal(STATE_COLLAPSED)
                }
            }
        }

        lastNestedScroll = dy
    }

    protected override fun startNestedScrollLogic(axes: Int): Boolean {
        flung = false
        return (axes and ViewCompat.SCROLL_AXIS_VERTICAL) != 0
    }

    protected override fun touchEventLogic(child: V, event: MotionEvent) {
        val helper = dragHelper
        if (helper != null && abs(initial - event.y) > helper.getTouchSlop().toFloat()) {
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
                initialX = event.x.toInt()
                initial = event.y.toInt()
                rememberInterceptResult = null

                if (state != STATE_SETTLING) {
                    val scroll = nestedScrollingChildRef?.get()
                    if (scroll == null || parent.isPointInChildBounds(scroll, initialX, initial)) {
                        activePointerId = event.getPointerId(event.actionIndex)
                    }
                }
                ignoreEvents = activePointerId == MotionEvent.INVALID_POINTER_ID &&
                    !parent.isPointInChildBounds(child, initialX, initial)
            }
        }

        val absX = abs(initialX - event.x)
        val absY = abs(initial - event.y)
        val helper = dragHelper

        if (helper != null) {
            if (rememberInterceptResult == null && (absX > helper.getTouchSlop().toFloat() || absY > helper.getTouchSlop().toFloat())) {
                rememberInterceptResult = absX < absY
            }

            return rememberInterceptResult == true && initial - event.y < 0 && absY > helper.getTouchSlop().toFloat()
        }

        return false
    }

    protected override fun instantiateDragCallback(): SheetDragHelper.Callback = object : SheetDragHelper.Callback() {
        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            if (capture) return true
            if (state == STATE_DRAGGING) return false

            if (state == STATE_EXPANDED && activePointerId == pointerId) {
                val scroll = nestedScrollingChildRef?.get()

                if (scroll is OverScrollRecyclerView) {
                    val pullEdges = scroll.getOverscrollPullEdges()
                    if ((pullEdges and OverScrollEffect.PULL_EDGE_TOP) == OverScrollEffect.PULL_EDGE_TOP) {
                        return false
                    }
                }

                if (scroll != null && scroll.isVisible && scroll.canScrollVertically(-1)) {
                    return false
                }
            }

            return viewRef?.get() === child
        }

        override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) {
            dispatchOnSlide(top)
        }

        override fun onViewDragStateChanged(state: Int) {
            if (state == ViewDragHelper.STATE_DRAGGING && draggable) {
                setStateInternal(STATE_DRAGGING)
            }
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            val top: Int
            val targetState: Int

            if (yvel == 0f || Measurements.dpToPx(
                    ViewConfiguration.get(releasedChild.context).scaledMinimumFlingVelocity +
                        (ViewConfiguration.get(releasedChild.context).scaledMaximumFlingVelocity - ViewConfiguration.get(releasedChild.context).scaledMinimumFlingVelocity) / 10f
                ).toFloat() > abs(yvel)
            ) {
                val currentTop = releasedChild.top
                if (currentTop < (expandedOffset + collapsedOffset) / 2) {
                    top = expandedOffset
                    targetState = STATE_EXPANDED
                } else {
                    top = collapsedOffset
                    targetState = STATE_COLLAPSED
                }
            } else if (yvel > 0) {
                top = collapsedOffset
                targetState = STATE_COLLAPSED
            } else {
                top = expandedOffset
                targetState = STATE_EXPANDED
            }

            settleChildTo(releasedChild, targetState, releasedChild.left, top)
        }

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int =
            MathUtils.clamp(top, expandedOffset, collapsedOffset)

        override fun getViewVerticalDragRange(child: View): Int = collapsedOffset
    }

    protected override fun isFullySettled(sheet: V, state: Int): Boolean {
        if (state == STATE_COLLAPSED && sheet.top == collapsedOffset) return true
        if (state == STATE_EXPANDED && sheet.top == expandedOffset) return true
        return false
    }

    protected override fun settleToState(child: View, state: Int, animate: Boolean) {
        val top = if (state == STATE_COLLAPSED) {
            collapsedOffset
        } else if (state == STATE_EXPANDED) {
            expandedOffset
        } else {
            throw IllegalArgumentException("Illegal state argument: $state")
        }

        if (animate) {
            settleChildTo(child, state, child.left, top)
        } else {
            moveChildTo(child, state, child.left, top)
        }
    }
}
