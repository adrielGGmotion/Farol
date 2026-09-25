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

package com.farol.launcher.sheet.behavior

import android.content.Context
import android.graphics.Rect
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IntDef
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager.widget.ViewPager
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.min
import androidx.core.view.isNotEmpty

/**
 * An interaction behavior plugin for a child view of [CoordinatorLayout] to make it work as a sheet.
 */
abstract class SheetBehavior<V : View> : CoordinatorLayout.Behavior<V> {
    /**
     * Callback for monitoring events about sheets.
     */
    interface SheetCallback {
        /**
         * Called when the sheet changes its state.
         *
         * @param sheet    The sheet view.
         * @param newState The new state. This will be one of [STATE_DRAGGING], [STATE_SETTLING],
         * [STATE_EXPANDED] or [STATE_COLLAPSED].
         */
        fun onStateChanged(sheet: View, @State newState: Int) {
        }

        /**
         * Called when the sheet changes its state.
         *
         * @param sheet         The sheet view.
         * @param stateToSettle The state to settle to. This will be one of [STATE_EXPANDED]
         * or [STATE_COLLAPSED].
         */
        fun onSettleToState(sheet: View, @State stateToSettle: Int) {
        }

        /**
         * Called when the sheet is being dragged.
         *
         * @param sheet       The sheet view.
         * @param slideOffset The new offset of this sheet within [-1,1] range. Offset increases
         * as this sheet is moving. From 0 to 1 the sheet is between collapsed and
         * expanded states and from -1 to 0 it is between hidden and collapsed states.
         */
        fun onSlide(sheet: View, slideOffset: Float) {
        }
    }

    @IntDef(
        STATE_EXPANDED,
        STATE_COLLAPSED,
        STATE_DRAGGING,
        STATE_SETTLING
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class State

    private var settleRunnable: SettleRunnable? = null
    @JvmField protected var expandedOffset = 0
    @JvmField protected var collapsedOffset = 0
    @JvmField @field:State protected var state = STATE_COLLAPSED
    @JvmField protected var dragHelper: SheetDragHelper? = null
    @JvmField protected var ignoreEvents = false
    @JvmField protected var lastNestedScroll = 0
    private var nestedScrolled = false
    @JvmField protected var parentWidth = 0
    @JvmField protected var parentHeight = 0
    @JvmField protected var viewRef: WeakReference<V>? = null
    @JvmField protected var pagerRef: WeakReference<ViewPager>? = null
    @JvmField protected var nestedScrollingChildRef: WeakReference<View>? = null
    @JvmField protected val callbacks = ArrayList<SheetCallback>()
    @JvmField protected var draggable = false
    @JvmField protected var capture = false
    @JvmField protected var activePointerId = 0
    @JvmField protected var initial = 0

    constructor() : super() {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    private fun init() {
        capture = false
        draggable = true
        settleRunnable = null
    }

    override fun onRestoreInstanceState(parent: CoordinatorLayout, child: V, state: Parcelable) {
        super.onRestoreInstanceState(parent, child, state)
        this.state = STATE_COLLAPSED
    }

    override fun onAttachedToLayoutParams(layoutParams: LayoutParams) {
        super.onAttachedToLayoutParams(layoutParams)

        // These may already be null, but just be safe, explicitly assign them. This lets us know the
        // first time we layout with this behavior by checking (viewRef == null).
        viewRef = null
        dragHelper = null
    }

    override fun onDetachedFromLayoutParams() {
        super.onDetachedFromLayoutParams()

        // Release references so we don't run unnecessary code paths while not attached to a view.
        viewRef = null
        dragHelper = null
    }

    override fun onLayoutChild(parent: CoordinatorLayout, child: V, layoutDirection: Int): Boolean {
        if (parent.fitsSystemWindows && !child.fitsSystemWindows) {
            child.fitsSystemWindows = true
        }

        if (viewRef == null) {
            // First layout with this behavior.
            viewRef = WeakReference(child)

            if (child.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
                child.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
        }

        // look for view pager switching
        if (pagerRef == null || pagerRef?.get() == null) {
            val pager = findPager(child)

            if (pager != null) {
                pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
                    override fun onPageScrollStateChanged(state: Int) {
                        if (state == ViewPager.SCROLL_STATE_IDLE) {
                            val target = findNestedScrollingChild(pager)

                            if (target != null) {
                                nestedScrollingChildRef = WeakReference(target)
                            }
                        }
                    }
                })

                pagerRef = WeakReference(pager)
            }
        }

        val target = if (nestedScrollingChildRef == null ||
            nestedScrollingChildRef?.get() == null ||
            nestedScrollingChildRef?.get()?.isShown != true
        ) {
            findNestedScrollingChild(child)
        } else {
            null
        }

        if (target != null) {
            nestedScrollingChildRef = WeakReference(target)
        }

        if (dragHelper == null) {
            dragHelper = SheetDragHelper.create(parent, instantiateDragCallback())
        }

        val saved = getPositionInParent(child)

        // First let the parent lay it out
        parent.onLayoutChild(child, layoutDirection)

        // Offset the sheet
        parentWidth = parent.width
        parentHeight = parent.height

        calculateExpandedOffset()
        calculateCollapsedOffset()

        if (this.state == STATE_EXPANDED) {
            offset(child, expandedOffset)
        } else if (this.state == STATE_COLLAPSED) {
            offset(child, collapsedOffset)
        } else if (this.state == STATE_DRAGGING || this.state == STATE_SETTLING) {
            offset(child, saved - getPositionInParent(child))
        }

        return true
    }

    override fun onInterceptTouchEvent(parent: CoordinatorLayout, child: V, event: MotionEvent): Boolean {
        if (!draggable) {
            ignoreEvents = true
            return false
        }

        val action = event.actionMasked

        if (action == MotionEvent.ACTION_DOWN) {
            reset()
        }

        val shouldIntercept = interceptTouchEventLogic(parent, child, event)
        val helper = dragHelper

        if (helper != null) {
            val viewDragHelperIntercept = helper.shouldInterceptTouchEvent(event)

            if (!ignoreEvents && shouldIntercept && viewDragHelperIntercept) {
                return true
            }
        }

        if (this.state != STATE_COLLAPSED && this.state != STATE_EXPANDED) {
            return true
        }

        // We have to handle cases that the ViewDragHelper does not capture the sheet because
        // it is not the top most view of its parent. This is not necessary when the touch event is
        // happening over the scrolling content as nested scrolling logic handles that case.
        val scroll = nestedScrollingChildRef?.get()

        return action == MotionEvent.ACTION_MOVE &&
            scroll != null &&
            !ignoreEvents &&
            this.state != STATE_DRAGGING &&
            !parent.isPointInChildBounds(scroll, event.x.toInt(), event.y.toInt()) &&
            helper != null &&
            shouldIntercept
    }

    override fun onTouchEvent(parent: CoordinatorLayout, child: V, event: MotionEvent): Boolean {
        val action = event.actionMasked

        if (this.state == STATE_DRAGGING && action == MotionEvent.ACTION_DOWN) {
            return true
        }

        dragHelper?.processTouchEvent(event)

        if (action == MotionEvent.ACTION_DOWN) {
            reset()
        }

        // The ViewDragHelper tries to capture only the top-most View. We have to explicitly tell it
        // to capture the sheet in case it is not captured and the touch slop is passed.
        if (action == MotionEvent.ACTION_MOVE && !ignoreEvents) {
            touchEventLogic(child, event)
        }

        return !ignoreEvents
    }

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ): Boolean {
        lastNestedScroll = 0
        nestedScrolled = false

        return startNestedScrollLogic(axes)
    }

    override fun onNestedPreScroll(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int
    ) {
        if (type == ViewCompat.TYPE_NON_TOUCH) {
            // Ignore fling here. The ViewDragHelper handles it.
            return
        }

        val scrollingChild = nestedScrollingChildRef?.get()
        if (target !== scrollingChild) {
            return
        }

        nestedPreScrollLogic(child, target, dx, dy, consumed)

        dispatchOnSlide(child)
        nestedScrolled = true
    }

    override fun onStopNestedScroll(coordinatorLayout: CoordinatorLayout, child: V, target: View, type: Int) {
        if (nestedScrollingChildRef == null ||
            target !== nestedScrollingChildRef?.get() ||
            !nestedScrolled
        ) {
            return
        }

        stopNestedScrollLogic(child)

        nestedScrolled = false
    }

    override fun onNestedPreFling(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        target: View,
        velocityX: Float,
        velocityY: Float
    ): Boolean = nestedScrollingChildRef != null &&
        target === nestedScrollingChildRef?.get() &&
        nestedPreFlingLogic(child, velocityX, velocityY)

    override fun onNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray
    ) {
        // Overridden to prevent the default consumption of the entire scroll distance.
    }

    /**
     * Adds a callback to be notified of sheet events.
     *
     * @param callback The callback to notify when sheet events occur.
     */
    fun addSheetCallback(callback: SheetCallback) {
        if (!callbacks.contains(callback)) {
            callbacks.add(callback)
        }
    }

    /**
     * Removes a previously added callback.
     *
     * @param callback The callback to remove.
     */
    fun removeSheetCallback(callback: SheetCallback) {
        callbacks.remove(callback)
    }

    /**
     * Sets the state of the sheet. The sheet will transition to that state with
     * animation.
     *
     * @param state [STATE_COLLAPSED] or [STATE_EXPANDED].
     */
    fun setState(@State state: Int) {
        setState(state, true)
    }

    /**
     * Sets the state of the sheet. The sheet will transition to that state with
     * or without an animation.
     *
     * @param state [STATE_COLLAPSED] or [STATE_EXPANDED].
     */
    fun setState(@State state: Int, animate: Boolean) {
        if (viewRef == null) {
            // The view is not laid out yet; modify mState and let onLayoutChild handle it later
            if (state == STATE_COLLAPSED || state == STATE_EXPANDED) {
                this.state = state
            }

            return
        }

        settleToStatePendingLayout(state, animate)
    }

    private fun settleToStatePendingLayout(@State state: Int, animate: Boolean) {
        val child = viewRef?.get() ?: return

        // Start the animation; wait until a pending layout if there is one.
        val parent = child.parent

        if (parent != null && parent.isLayoutRequested && child.isAttachedToWindow) {
            child.post { settleToState(child, state, animate) }
        } else {
            settleToState(child, state, animate)
        }
    }

    private fun notifySettleToState(@State state: Int) {
        val sheet = viewRef?.get() ?: return

        for (i in callbacks.indices) {
            callbacks[i].onSettleToState(sheet, state)
        }
    }

    /**
     * Gets the current state of the sheet.
     *
     * @return One of [STATE_EXPANDED], [STATE_COLLAPSED],
     * [STATE_DRAGGING] or [STATE_SETTLING].
     */
    @State
    fun getState(): Int = this.state

    protected fun setStateInternal(@State state: Int) {
        if (this.state == state) {
            return
        }

        if (viewRef == null) {
            this.state = state
            return
        }

        val sheet = viewRef?.get()
        if (sheet == null) {
            this.state = state
            return
        }

        // dragging the sheet while settling will still fire the callback
        // even if the sheet is not fully settled
        if (state == STATE_SETTLING || state == STATE_DRAGGING || isFullySettled(sheet, state)) {
            this.state = state

            for (i in callbacks.indices) {
                callbacks[i].onStateChanged(sheet, state)
            }
        }
    }

    private fun reset() {
        activePointerId = ViewDragHelper.INVALID_POINTER
    }

    private fun findNestedScrollingChild(view: View?): View? {
        if (view == null) {
            return null
        }

        if (ViewCompat.isNestedScrollingEnabled(view) &&
            view !is SwipeRefreshLayout &&
            view.isAttachedToWindow &&
            view.isShown
        ) {
            val testRect = Rect(0, 0, 0, 0)

            if (view.getLocalVisibleRect(testRect)) {
                return view
            }
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val scrollingChild = findNestedScrollingChild(view.getChildAt(index))

                if (scrollingChild != null) {
                    return scrollingChild
                }
            }
        }

        return null
    }

    private fun findPager(view: View): ViewPager? {
        if (view is ViewPager) {
            return view
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val scrollingChild = findPager(view.getChildAt(index))

                if (scrollingChild != null) {
                    return scrollingChild
                }
            }
        }

        return null
    }

    fun isDragHelperInstantiated(): Boolean = dragHelper != null

    protected fun dispatchOnSlide(value: Int) {
        val sheet = viewRef?.get()

        if (sheet != null && callbacks.isNotEmpty()) {
            val slideOffset = max(0f, min(1f, (value - collapsedOffset) * 1f / (expandedOffset - collapsedOffset)))

            for (callback in callbacks) {
                callback.onSlide(sheet, slideOffset)
            }
        }
    }

    /**
     * Move a specific child into place.
     *
     * @param child Child targeted by move
     * @param state Target state
     * @param left  Final left offset of child
     * @param top   Final top offset of child
     */
    protected fun moveChildTo(child: View, @State state: Int, left: Int, top: Int) {
        val helper = dragHelper
        if (helper != null) {
            helper.abort()

            val dx = left - child.left
            val dy = top - child.top

            if (dx != 0) {
                ViewCompat.offsetLeftAndRight(child, dx)
            } else if (dy != 0) {
                ViewCompat.offsetTopAndBottom(child, dy)
            }
        }

        val sheet = viewRef?.get()

        if (sheet != null) {
            for (callback in callbacks) {
                if (state == STATE_COLLAPSED) {
                    callback.onSlide(sheet, 0f)
                } else if (state == STATE_EXPANDED) {
                    callback.onSlide(sheet, 1f)
                }
            }
        }

        setStateInternal(state)
    }

    protected fun settleChildTo(child: View, @State state: Int, left: Int, top: Int) {
        settleChildTo(child, state, left, top, null, null)
    }

    /**
     * Settle a specific child into place.
     *
     * @param child Child targeted by the settle animation
     * @param state Target state
     * @param left  Final left offset of child
     * @param top   Final top offset of child
     * @param xvel  Horizontal velocity to calculate animation duration, or null for base duration.
     * @param yvel  Vertical velocity to calculate animation duration, or null for base duration.
     */
    protected fun settleChildTo(child: View, @State state: Int, left: Int, top: Int, xvel: Int?, yvel: Int?) {
        val helper = dragHelper
        if (helper != null) {
            val startedSettling = if (xvel != null || yvel != null) {
                helper.smoothSlideViewTo(child, left, top, xvel ?: 0, yvel ?: 0)
            } else {
                helper.smoothSlideViewTo(child, left, top)
            }

            if (startedSettling) {
                setStateInternal(STATE_SETTLING)
                if (settleRunnable == null) {
                    // If the singleton SettleRunnable instance has not been instantiated, create it.
                    settleRunnable = SettleRunnable(child, state)
                }

                val runnable = settleRunnable ?: return
                // If the SettleRunnable has not been posted, post it with the correct state.
                if (!runnable.isPosted) {
                    runnable.targetState = state

                    child.postOnAnimation(runnable)
                    runnable.isPosted = true
                } else {
                    // Otherwise, if it has been posted, just update the target state.
                    runnable.targetState = state
                }

                notifySettleToState(state)
            } else {
                setStateInternal(state)
            }
        }
    }

    /**
     * Force the sheet to capture the event disregarding all other logic.
     */
    fun interceptTouches(capture: Boolean) {
        this.capture = capture
    }

    fun setDraggable(draggable: Boolean) {
        this.draggable = draggable
    }

    fun isDraggable(draggable: Boolean): Boolean = draggable

    private inner class SettleRunnable(private val view: View, @State targetState: Int) : Runnable {
        var isPosted = false
        @State
        var targetState = targetState

        override fun run() {
            if (dragHelper != null && dragHelper?.continueSettling(true) == true) {
                view.postOnAnimation(this)
            } else {
                setStateInternal(targetState)
            }

            isPosted = false
        }
    }

    fun invalidate() {
        val sheet = viewRef?.get() ?: return
        dispatchOnSlide(sheet)
    }

    protected abstract fun calculateCollapsedOffset()

    protected abstract fun calculateExpandedOffset()

    protected abstract fun dispatchOnSlide(child: V)

    protected abstract fun getPositionInParent(child: V): Int

    protected abstract fun offset(child: V, offset: Int)

    /**
     * Check if a view is fully settled to a state.
     *
     * @param sheet The sheet instance.
     * @param state The state to check. This will be one of [STATE_EXPANDED]
     * or [STATE_COLLAPSED].
     */
    protected abstract fun isFullySettled(sheet: V, @State state: Int): Boolean

    protected abstract fun stopNestedScrollLogic(child: V)

    protected abstract fun nestedPreFlingLogic(child: V, xvel: Float, yvel: Float): Boolean

    protected abstract fun nestedPreScrollLogic(child: V, target: View, dx: Int, dy: Int, consumed: IntArray)

    protected abstract fun startNestedScrollLogic(axes: Int): Boolean

    protected abstract fun touchEventLogic(child: V, event: MotionEvent)

    protected abstract fun interceptTouchEventLogic(parent: CoordinatorLayout, child: V, event: MotionEvent): Boolean

    protected abstract fun instantiateDragCallback(): SheetDragHelper.Callback

    protected abstract fun settleToState(child: View, @State state: Int, animate: Boolean)

    companion object {
        /**
         * The sheet is dragging.
         */
        const val STATE_DRAGGING = 1

        /**
         * The sheet is settling.
         */
        const val STATE_SETTLING = 2

        /**
         * The sheet is expanded.
         */
        const val STATE_EXPANDED = 3

        /**
         * The sheet is collapsed.
         */
        const val STATE_COLLAPSED = 4

        /**
         * Offset difference between EXPANDED and COLLAPSED state in dp
         */
        const val COLLAPSED_DELTA_DP = 350

        /**
         * A utility function to get the [SheetBehavior] associated with the `view`.
         *
         * @param view The [View] with [SheetBehavior].
         * @return The [SheetBehavior] associated with the `view`.
         */
        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun <V : View> from(view: V): SheetBehavior<V> {
            val params = view.layoutParams
            if (params !is LayoutParams) {
                throw IllegalArgumentException("The view is not a child of CoordinatorLayout")
            }
            val behavior = params.behavior
            if (behavior !is SheetBehavior<*>) {
                throw IllegalArgumentException("The view is not associated with SheetBehavior")
            }
            return behavior as SheetBehavior<V>
        }
    }
}
