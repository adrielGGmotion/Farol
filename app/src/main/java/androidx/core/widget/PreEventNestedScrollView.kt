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

package androidx.core.widget

import android.content.Context
import android.hardware.SensorManager
import android.util.AttributeSet
import android.view.View

open class PreEventNestedScrollView : NestedScrollView {
    private var ignore = false
    private var listener: PreEvent? = null
    private var physicalCoeff = 0f

    /**
     * The following are copied from OverScroller to determine how far a fling will go.
     */
    private object Constants {
        const val SCROLL_FRICTION = 0.015f
        const val INFLEXION = 0.35f
        val DECELERATION_RATE = (Math.log(0.78) / Math.log(0.9)).toFloat()
    }

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
    ) : super(context, attrs, defStyleAttr) {
        init(context)
    }

    private fun init(context: Context) {
        val ppi = context.resources.displayMetrics.density * 160.0f
        physicalCoeff = SensorManager.GRAVITY_EARTH * 39.37f * ppi * 0.84f
    }

    override fun onStopNestedScroll(target: View, type: Int) {
        ignore = false
        super.onStopNestedScroll(target, type)
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
    ) {
        if (!ignore) {
            super.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed)
        }
    }

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
        type: Int,
    ): Boolean {
        val listener = listener
        return if (listener == null || !listener.onPreScroll(dy)) {
            ignore = false
            super.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)
        } else {
            ignore = true
            false
        }
    }

    override fun overScrollByCompat(
        deltaX: Int,
        deltaY: Int,
        scrollX: Int,
        scrollY: Int,
        scrollRangeX: Int,
        scrollRangeY: Int,
        maxOverScrollX: Int,
        maxOverScrollY: Int,
        isTouchEvent: Boolean,
    ): Boolean {
        val adjustedDeltaY = if (ignore) 0 else deltaY
        return super.overScrollByCompat(
            deltaX,
            adjustedDeltaY,
            scrollX,
            scrollY,
            scrollRangeX,
            scrollRangeY,
            maxOverScrollX,
            maxOverScrollY,
            isTouchEvent,
        )
    }

    override fun fling(velocityY: Int) {
        val listener = listener
        if (listener == null || !listener.onPreFling(velocityY)) {
            super.fling(velocityY)
        }
    }

    override fun canScrollHorizontally(direction: Int): Boolean = false

    open fun setOnPreScrollListener(listener: PreEvent?) {
        this.listener = listener
    }

    /**
     * Copied from OverScroller, this returns the distance that a fling with the given velocity
     * will go.
     *
     * @param velocity The velocity of the fling
     * @return The distance that will be traveled by a fling of the given velocity.
     */
    open fun getSplineFlingDistance(velocity: Int): Int {
        val decelMinusOne = Constants.DECELERATION_RATE - 1.0
        val coefficient = Constants.SCROLL_FRICTION * physicalCoeff
        return (
            coefficient.toDouble() * Math.exp(
                Constants.DECELERATION_RATE / decelMinusOne * Math.log(
                    (Constants.INFLEXION * Math.abs(velocity) / coefficient).toDouble(),
                ),
            )
        ).toInt()
    }

    /**
     * Reverse of [PreEventNestedScrollView.getSplineFlingDistance]
     *
     * @param distance The distance to be converted
     * @return The fling velocity needed to travel the provided distance.
     */
    open fun getSplineFlingVelocity(distance: Int): Int {
        val decelMinusOne = Constants.DECELERATION_RATE - 1.0
        val coefficient = Constants.SCROLL_FRICTION * physicalCoeff
        return if (Math.abs(distance) > 0) {
            (
                Math.signum(distance.toFloat()).toDouble() *
                    (coefficient / Constants.INFLEXION).toDouble() *
                    Math.exp(
                        decelMinusOne / Constants.DECELERATION_RATE * Math.log(
                            (Math.abs(distance) / coefficient).toDouble(),
                        ),
                    )
            ).toInt()
        } else {
            0
        }
    }

    interface PreEvent {
        /**
         * @return true if event should be consumed by this method
         */
        fun onPreScroll(delta: Int): Boolean = false

        /**
         * @return true if event should be consumed by this method
         */
        fun onPreFling(velocity: Int): Boolean = false
    }
}
