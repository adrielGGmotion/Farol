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

// Modification of ViewDragHelper

package com.stario.launcher.sheet.behavior

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.Interpolator
import android.widget.OverScroller
import androidx.annotation.Px
import androidx.core.view.ViewCompat
import java.util.Arrays
import kotlin.math.sin

/**
 * ViewDragHelper is a utility class for writing custom ViewGroups. It offers a number
 * of useful operations and state tracking for allowing a user to drag and reposition
 * views within their parent ViewGroup.
 */
open class SheetDragHelper private constructor(context: Context, private val mParentView: ViewGroup, private val mCallback: Callback) {
    private var mDragState = 0
    private val mTouchSlop: Int
    private var mActivePointerId = INVALID_POINTER
    private var mInitialMotionX: FloatArray? = null
    private var mInitialMotionY: FloatArray? = null
    private var mLastMotionX: FloatArray? = null
    private var mLastMotionY: FloatArray? = null
    private var mPointersDown = 0
    private var mVelocityTracker: VelocityTracker? = null
    private val mMaxVelocity: Float
    private val mMinVelocity: Float
    private val mScroller: OverScroller
    private var mCapturedView: View? = null

    /**
     * A Callback is used as a communication channel with the ViewDragHelper back to the
     * parent view using it. <code>on*</code>methods are invoked on siginficant events and several
     * accessor methods are expected to provide the ViewDragHelper with more information
     * about the state of the parent view upon request. The callback also makes decisions
     * governing the range and draggability of child views.
     */
    abstract class Callback {
        /**
         * Called when the drag state changes. See the <code>STATE_*</code> constants
         * for more information.
         *
         * @param state The new drag state
         * @see STATE_IDLE
         * @see STATE_DRAGGING
         * @see STATE_SETTLING
         */
        open fun onViewDragStateChanged(state: Int) = Unit

        /**
         * Called when the captured view's position changes as the result of a drag or settle.
         *
         * @param changedView View whose position changed
         * @param left        New X coordinate of the left edge of the view
         * @param top         New Y coordinate of the top edge of the view
         * @param dx          Change in X position from the last call
         * @param dy          Change in Y position from the last call
         */
        open fun onViewPositionChanged(changedView: View, left: Int, top: Int, @Px dx: Int, @Px dy: Int) = Unit

        /**
         * Called when a child view is captured for dragging or settling. The ID of the pointer
         * currently dragging the captured view is supplied. If activePointerId is
         * identified as [INVALID_POINTER] the capture is programmatic instead of
         * pointer-initiated.
         *
         * @param capturedChild   Child view that was captured
         * @param activePointerId Pointer id tracking the child capture
         */
        open fun onViewCaptured(capturedChild: View, activePointerId: Int) = Unit

        /**
         * Called when the child view is no longer being actively dragged.
         * The fling velocity is also supplied, if relevant. The velocity values may
         * be clamped to system minimums or maximums.
         *
         * <p>Calling code may decide to fling or otherwise release the view to let it
         * settle into place. If the Callback invokes
         * one of these methods, the ViewDragHelper will enter [STATE_SETTLING]
         * and the view capture will not fully end until it comes to a complete stop.
         * If neither of these methods is invoked before <code>onViewReleased</code> returns,
         * the view will stop in place and the ViewDragHelper will return to
         * [STATE_IDLE].</p>
         *
         * @param releasedChild The captured child view now being released
         * @param xvel          X velocity of the pointer as it left the screen in pixels per second.
         * @param yvel          Y velocity of the pointer as it left the screen in pixels per second.
         */
        open fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) = Unit

        /**
         * Called to determine the Z-order of child views.
         *
         * @param index the ordered position to query for
         * @return index of the view that should be ordered at position <code>index</code>
         */
        open fun getOrderedChildIndex(index: Int): Int = index

        /**
         * Return the magnitude of a draggable child view's horizontal range of motion in pixels.
         * This method should return 0 for views that cannot move horizontally.
         *
         * @param child Child view to check
         * @return range of horizontal motion in pixels
         */
        open fun getViewHorizontalDragRange(child: View): Int = 0

        /**
         * Return the magnitude of a draggable child view's vertical range of motion in pixels.
         * This method should return 0 for views that cannot move vertically.
         *
         * @param child Child view to check
         * @return range of vertical motion in pixels
         */
        open fun getViewVerticalDragRange(child: View): Int = 0

        /**
         * Called when the user's input indicates that they want to capture the given child view
         * with the pointer indicated by pointerId. The callback should return true if the user
         * is permitted to drag the given view with the indicated pointer.
         *
         * <p>ViewDragHelper may call this method multiple times for the same view even if
         * the view is already captured; this indicates that a new pointer is trying to take
         * control of the view.</p>
         *
         * <p>If this method returns true, a call to [onViewCaptured]
         * will follow if the capture is successful.</p>
         *
         * @param child     Child the user is attempting to capture
         * @param pointerId ID of the pointer attempting the capture
         * @return true if capture should be allowed, false otherwise
         */
        abstract fun tryCaptureView(child: View, pointerId: Int): Boolean

        /**
         * Restrict the motion of the dragged child view along the horizontal axis.
         * The default implementation does not allow horizontal motion; the extending
         * class must override this method and provide the desired clamping.
         *
         * @param child Child view being dragged
         * @param left  Attempted motion along the X axis
         * @param dx    Proposed change in position for left
         * @return The new clamped position for left
         */
        open fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int = 0

        /**
         * Restrict the motion of the dragged child view along the vertical axis.
         * The default implementation does not allow vertical motion; the extending
         * class must override this method and provide the desired clamping.
         *
         * @param child Child view being dragged
         * @param top   Attempted motion along the Y axis
         * @param dy    Proposed change in position for top
         * @return The new clamped position for top
         */
        open fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int = 0
    }

    private val mSetIdleRunnable = Runnable { setDragState(STATE_IDLE) }

    init {
        val vc = ViewConfiguration.get(context)
        mTouchSlop = vc.scaledTouchSlop
        mMaxVelocity = vc.scaledMaximumFlingVelocity.toFloat()
        mMinVelocity = vc.scaledMinimumFlingVelocity.toFloat()
        mScroller = OverScroller(context, sInterpolator)
    }

    /**
     * Capture a specific child view for dragging within the parent. The callback will be notified
     * but [Callback.tryCaptureView] will not be asked permission to
     * capture this view.
     *
     * @param childView       Child view to capture
     * @param activePointerId ID of the pointer that is dragging the captured child view
     */
    fun captureChildView(childView: View, activePointerId: Int) {
        if (childView.parent !== mParentView) {
            throw IllegalArgumentException(
                "captureChildView: parameter must be a descendant " +
                    "of the ViewDragHelper's tracked parent view ($mParentView)"
            )
        }

        mCapturedView = childView
        mActivePointerId = activePointerId
        mCallback.onViewCaptured(childView, activePointerId)
        setDragState(STATE_DRAGGING)
    }

    /**
     * @return The minimum distance in pixels that the user must travel to initiate a drag
     */
    @Px
    fun getTouchSlop(): Int = mTouchSlop

    /**
     * The result of a call to this method is equivalent to
     * [processTouchEvent] receiving an ACTION_CANCEL event.
     */
    fun cancel() {
        mActivePointerId = INVALID_POINTER
        clearMotionHistory()

        mVelocityTracker?.recycle()
        mVelocityTracker = null
    }

    /**
     * [cancel], but also abort all motion in progress and snap to the end of any
     * animation.
     */
    fun abort() {
        cancel()
        if (mDragState == STATE_SETTLING) {
            val oldX = mScroller.currX
            val oldY = mScroller.currY
            mScroller.abortAnimation()
            val newX = mScroller.currX
            val newY = mScroller.currY
            mCallback.onViewPositionChanged(mCapturedView!!, newX, newY, newX - oldX, newY - oldY)
        }
        setDragState(STATE_IDLE)
    }

    /**
     * Animate the view <code>child</code> to the given (left, top) position.
     * If this method returns true, the caller should invoke [continueSettling]
     * on each subsequent frame to continue the motion until it returns false. If this method
     * returns false there is no further work to do to complete the movement.
     *
     * @param child     Child view to capture and animate
     * @param finalLeft Final left position of child
     * @param finalTop  Final top position of child
     * @return true if animation should continue through [continueSettling] calls
     */
    fun smoothSlideViewTo(child: View, finalLeft: Int, finalTop: Int): Boolean {
        val velocityTracker = mVelocityTracker
        if (velocityTracker != null) {
            return smoothSlideViewTo(
                child,
                finalLeft,
                finalTop,
                velocityTracker.getXVelocity(mActivePointerId).toInt(),
                velocityTracker.getYVelocity(mActivePointerId).toInt()
            )
        }

        return smoothSlideViewTo(child, finalLeft, finalTop, 0, 0)
    }

    /**
     * Animate the view <code>child</code> to the given (left, top) position.
     * If this method returns true, the caller should invoke [continueSettling]
     * on each subsequent frame to continue the motion until it returns false. If this method
     * returns false there is no further work to do to complete the movement.
     *
     * @param child     Child view to capture and animate
     * @param finalLeft Final left position of child
     * @param finalTop  Final top position of child
     * @param xvel      Horizontal velocity
     * @param yvel      Vertical velocity
     * @return true if animation should continue through [continueSettling] calls
     */
    fun smoothSlideViewTo(child: View, finalLeft: Int, finalTop: Int, xvel: Int, yvel: Int): Boolean {
        mCapturedView = child
        mActivePointerId = INVALID_POINTER

        val continueSliding = forceSettleCapturedViewAt(finalLeft, finalTop, xvel, yvel)
        if (!continueSliding && mDragState == STATE_IDLE && mCapturedView != null) {
            mCapturedView = null
        }

        return continueSliding
    }

    private fun forceSettleCapturedViewAt(finalLeft: Int, finalTop: Int, xvel: Int, yvel: Int): Boolean {
        val capturedView = mCapturedView!!
        val startLeft = capturedView.left
        val startTop = capturedView.top
        val dx = finalLeft - startLeft
        val dy = finalTop - startTop

        if (dx == 0 && dy == 0) {
            mScroller.abortAnimation()
            setDragState(STATE_IDLE)
            return false
        }

        val duration = computeSettleDuration(capturedView, dx, dy, xvel, yvel)
        mScroller.startScroll(startLeft, startTop, dx, dy, duration)

        setDragState(STATE_SETTLING)
        return true
    }

    private fun computeSettleDuration(child: View, dx: Int, dy: Int, xvelIn: Int, yvelIn: Int): Int {
        val xvel = clampMag(xvelIn, mMinVelocity.toInt(), mMaxVelocity.toInt())
        val yvel = clampMag(yvelIn, mMinVelocity.toInt(), mMaxVelocity.toInt())
        val absDx = Math.abs(dx)
        val absDy = Math.abs(dy)
        val absXVel = Math.abs(xvel)
        val absYVel = Math.abs(yvel)
        val addedVel = absXVel + absYVel
        val addedDistance = absDx + absDy

        val xweight = if (xvel != 0) absXVel.toFloat() / addedVel else absDx.toFloat() / addedDistance
        val yweight = if (yvel != 0) absYVel.toFloat() / addedVel else absDy.toFloat() / addedDistance

        val xduration = computeAxisDuration(dx, xvel, mCallback.getViewHorizontalDragRange(child))
        val yduration = computeAxisDuration(dy, yvel, mCallback.getViewVerticalDragRange(child))

        return (xduration * xweight + yduration * yweight).toInt()
    }

    private fun computeAxisDuration(delta: Int, velocityIn: Int, motionRange: Int): Int {
        if (delta == 0) {
            return 0
        }

        val width = mParentView.width
        val halfWidth = width / 2
        val distanceRatio = Math.min(1f, Math.abs(delta).toFloat() / width)
        val distance = halfWidth + halfWidth * distanceInfluenceForSnapDuration(distanceRatio)

        val velocity = Math.abs(velocityIn)
        val duration = if (velocity != 0) {
            4 * Math.round(1000 * Math.abs(distance / velocity))
        } else {
            val range = Math.abs(delta).toFloat() / motionRange
            ((range + 1) * BASE_SETTLE_DURATION).toInt()
        }
        return Math.min(duration, MAX_SETTLE_DURATION)
    }

    private fun clampMag(value: Int, absMin: Int, absMax: Int): Int {
        val absValue = Math.abs(value)
        if (absValue < absMin) return 0
        if (absValue > absMax) return if (value > 0) absMax else -absMax
        return value
    }

    private fun clampMag(value: Float, absMin: Float, absMax: Float): Float {
        val absValue = Math.abs(value)
        if (absValue < absMin) return 0f
        if (absValue > absMax) return if (value > 0) absMax else -absMax
        return value
    }

    private fun distanceInfluenceForSnapDuration(value: Float): Float {
        var f = value
        f -= 0.5f
        f *= 0.3f * Math.PI.toFloat() / 2.0f
        return sin(f)
    }

    /**
     * Move the captured settling view by the appropriate amount for the current time.
     * If <code>continueSettling</code> returns true, the caller should call it again
     * on the next frame to continue.
     *
     * @param deferCallbacks true if state callbacks should be deferred via posted message.
     *                       Set this to true if you are calling this method from
     *                       [android.view.View.computeScroll] or similar methods
     *                       invoked as part of layout or drawing.
     * @return true if settle is still in progress
     */
    fun continueSettling(deferCallbacks: Boolean): Boolean {
        if (mDragState == STATE_SETTLING) {
            var keepGoing = mScroller.computeScrollOffset()
            val x = mScroller.currX
            val y = mScroller.currY
            val capturedView = mCapturedView!!
            val dx = x - capturedView.left
            val dy = y - capturedView.top

            if (dx != 0) {
                ViewCompat.offsetLeftAndRight(capturedView, dx)
            }
            if (dy != 0) {
                ViewCompat.offsetTopAndBottom(capturedView, dy)
            }

            if (dx != 0 || dy != 0) {
                mCallback.onViewPositionChanged(capturedView, x, y, dx, dy)
            }

            if (keepGoing && x == mScroller.finalX && y == mScroller.finalY) {
                mScroller.abortAnimation()
                keepGoing = false
            }

            if (!keepGoing) {
                if (deferCallbacks) {
                    mParentView.post(mSetIdleRunnable)
                } else {
                    setDragState(STATE_IDLE)
                }
            }
        }

        return mDragState == STATE_SETTLING
    }

    private fun dispatchViewReleased(xvel: Float, yvel: Float) {
        mCallback.onViewReleased(mCapturedView!!, xvel, yvel)

        if (mDragState == STATE_DRAGGING) {
            setDragState(STATE_IDLE)
        }
    }

    private fun clearMotionHistory() {
        val initialMotionX = mInitialMotionX ?: return
        Arrays.fill(initialMotionX, 0f)
        Arrays.fill(mInitialMotionY!!, 0f)
        Arrays.fill(mLastMotionX!!, 0f)
        Arrays.fill(mLastMotionY!!, 0f)
        mPointersDown = 0
    }

    private fun clearMotionHistory(pointerId: Int) {
        val initialMotionX = mInitialMotionX
        if (initialMotionX == null || !isPointerDown(pointerId)) {
            return
        }
        initialMotionX[pointerId] = 0f
        mInitialMotionY!![pointerId] = 0f
        mLastMotionX!![pointerId] = 0f
        mLastMotionY!![pointerId] = 0f
        mPointersDown = mPointersDown and (1 shl pointerId).inv()
    }

    private fun ensureMotionHistorySizeForId(pointerId: Int) {
        val initialMotionX = mInitialMotionX
        if (initialMotionX == null || initialMotionX.size <= pointerId) {
            val imx = FloatArray(pointerId + 1)
            val imy = FloatArray(pointerId + 1)
            val lmx = FloatArray(pointerId + 1)
            val lmy = FloatArray(pointerId + 1)

            if (initialMotionX != null) {
                System.arraycopy(initialMotionX, 0, imx, 0, initialMotionX.size)
                System.arraycopy(mInitialMotionY!!, 0, imy, 0, mInitialMotionY!!.size)
                System.arraycopy(mLastMotionX!!, 0, lmx, 0, mLastMotionX!!.size)
                System.arraycopy(mLastMotionY!!, 0, lmy, 0, mLastMotionY!!.size)
            }

            mInitialMotionX = imx
            mInitialMotionY = imy
            mLastMotionX = lmx
            mLastMotionY = lmy
        }
    }

    private fun saveInitialMotion(x: Float, y: Float, pointerId: Int) {
        ensureMotionHistorySizeForId(pointerId)
        mInitialMotionX!![pointerId] = x
        mLastMotionX!![pointerId] = x
        mInitialMotionY!![pointerId] = y
        mLastMotionY!![pointerId] = y
        mPointersDown = mPointersDown or (1 shl pointerId)
    }

    private fun saveLastMotion(ev: MotionEvent) {
        val pointerCount = ev.pointerCount
        for (i in 0 until pointerCount) {
            val pointerId = ev.getPointerId(i)
            if (!isValidPointerForActionMove(pointerId)) {
                continue
            }
            val x = ev.getX(i)
            val y = ev.getY(i)
            mLastMotionX!![pointerId] = x
            mLastMotionY!![pointerId] = y
        }
    }

    /**
     * Check if the given pointer ID represents a pointer that is currently down (to the best
     * of the ViewDragHelper's knowledge).
     *
     * <p>The state used to report this information is populated by the methods
     * [shouldInterceptTouchEvent] or
     * [processTouchEvent]. If one of these methods has not
     * been called for all relevant MotionEvents to track, the information reported
     * by this method may be stale or incorrect.</p>
     *
     * @param pointerId pointer ID to check; corresponds to IDs provided by MotionEvent
     * @return true if the pointer with the given ID is still down
     * @noinspection BooleanMethodIsAlwaysInverted
     */
    fun isPointerDown(pointerId: Int): Boolean = (mPointersDown and (1 shl pointerId)) != 0

    internal fun setDragState(state: Int) {
        mParentView.removeCallbacks(mSetIdleRunnable)
        if (mDragState != state) {
            mDragState = state
            mCallback.onViewDragStateChanged(state)
            if (mDragState == STATE_IDLE) {
                mCapturedView = null
            }
        }
    }

    /**
     * Attempt to capture the view with the given pointer ID. The callback will be involved.
     * This will put us into the "dragging" state. If we've already captured this view with
     * this pointer this method will immediately return true without consulting the callback.
     *
     * @param toCapture View to capture
     * @param pointerId Pointer to capture with
     * @return true if capture was successful
     */
    internal fun tryCaptureViewForDrag(toCapture: View?, pointerId: Int): Boolean {
        if (toCapture === mCapturedView && mActivePointerId == pointerId) {
            return true
        }
        if (toCapture != null && mCallback.tryCaptureView(toCapture, pointerId)) {
            mActivePointerId = pointerId
            captureChildView(toCapture, pointerId)
            return true
        }
        return false
    }

    /**
     * Check if this event as provided to the parent view's onInterceptTouchEvent should
     * cause the parent to intercept the touch event stream.
     *
     * @param ev MotionEvent provided to onInterceptTouchEvent
     * @return true if the parent view should return true from onInterceptTouchEvent
     */
    fun shouldInterceptTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked
        val actionIndex = ev.actionIndex

        if (action == MotionEvent.ACTION_DOWN) {
            cancel()
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }
        mVelocityTracker!!.addMovement(ev)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                val x = ev.x
                val y = ev.y
                val pointerId = ev.getPointerId(0)
                saveInitialMotion(x, y, pointerId)

                val toCapture = findTopChildUnder(x.toInt(), y.toInt())

                if (toCapture === mCapturedView && mDragState == STATE_SETTLING) {
                    tryCaptureViewForDrag(toCapture, pointerId)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerId = ev.getPointerId(actionIndex)
                val x = ev.getX(actionIndex)
                val y = ev.getY(actionIndex)

                saveInitialMotion(x, y, pointerId)

                if (mDragState == STATE_SETTLING) {
                    val toCapture = findTopChildUnder(x.toInt(), y.toInt())
                    if (toCapture === mCapturedView) {
                        tryCaptureViewForDrag(toCapture, pointerId)
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (mInitialMotionX != null && mInitialMotionY != null) {
                    val pointerCount = ev.pointerCount
                    for (i in 0 until pointerCount) {
                        val pointerId = ev.getPointerId(i)

                        if (!isValidPointerForActionMove(pointerId)) continue

                        val x = ev.getX(i)
                        val y = ev.getY(i)
                        val dx = x - mInitialMotionX!![pointerId]
                        val dy = y - mInitialMotionY!![pointerId]

                        val toCapture = findTopChildUnder(x.toInt(), y.toInt())
                        val pastSlop = checkTouchSlop(toCapture, dx, dy)
                        if (pastSlop) {
                            val oldLeft = toCapture!!.left
                            val targetLeft = oldLeft + dx.toInt()
                            val newLeft = mCallback.clampViewPositionHorizontal(toCapture, targetLeft, dx.toInt())
                            val oldTop = toCapture.top
                            val targetTop = oldTop + dy.toInt()
                            val newTop = mCallback.clampViewPositionVertical(toCapture, targetTop, dy.toInt())
                            val hDragRange = mCallback.getViewHorizontalDragRange(toCapture)
                            val vDragRange = mCallback.getViewVerticalDragRange(toCapture)
                            if ((hDragRange == 0 || (hDragRange > 0 && newLeft == oldLeft)) &&
                                (vDragRange == 0 || (vDragRange > 0 && newTop == oldTop))
                            ) {
                                break
                            }
                        }

                        if (mDragState == STATE_DRAGGING) {
                            break
                        }

                        if (pastSlop && tryCaptureViewForDrag(toCapture, pointerId)) {
                            break
                        }
                    }
                    saveLastMotion(ev)
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = ev.getPointerId(actionIndex)
                clearMotionHistory(pointerId)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancel()
        }

        return mDragState == STATE_DRAGGING
    }

    /**
     * Process a touch event received by the parent view. This method will dispatch callback events
     * as needed before returning. The parent view's onTouchEvent implementation should call this.
     *
     * @param ev The touch event received by the parent view
     */
    fun processTouchEvent(ev: MotionEvent) {
        val action = ev.actionMasked
        val actionIndex = ev.actionIndex

        if (action == MotionEvent.ACTION_DOWN) {
            cancel()
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }
        mVelocityTracker!!.addMovement(ev)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                val x = ev.x
                val y = ev.y
                val pointerId = ev.getPointerId(0)
                val toCapture = findTopChildUnder(x.toInt(), y.toInt())

                saveInitialMotion(x, y, pointerId)
                tryCaptureViewForDrag(toCapture, pointerId)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerId = ev.getPointerId(actionIndex)
                val x = ev.getX(actionIndex)
                val y = ev.getY(actionIndex)

                saveInitialMotion(x, y, pointerId)

                if (mDragState == STATE_IDLE) {
                    val toCapture = findTopChildUnder(x.toInt(), y.toInt())
                    tryCaptureViewForDrag(toCapture, pointerId)
                } else if (isCapturedViewUnder(x.toInt(), y.toInt())) {
                    tryCaptureViewForDrag(mCapturedView, pointerId)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (mDragState == STATE_DRAGGING) {
                    if (isValidPointerForActionMove(mActivePointerId)) {
                        val index = ev.findPointerIndex(mActivePointerId)
                        val x = ev.getX(index)
                        val y = ev.getY(index)
                        val idx = (x - mLastMotionX!![mActivePointerId]).toInt()
                        val idy = (y - mLastMotionY!![mActivePointerId]).toInt()

                        val capturedView = mCapturedView!!
                        dragTo(capturedView.left + idx, capturedView.top + idy, idx, idy)

                        saveLastMotion(ev)
                    }
                } else {
                    val pointerCount = ev.pointerCount
                    for (i in 0 until pointerCount) {
                        val pointerId = ev.getPointerId(i)

                        if (!isValidPointerForActionMove(pointerId)) continue

                        val x = ev.getX(i)
                        val y = ev.getY(i)
                        val dx = x - mInitialMotionX!![pointerId]
                        val dy = y - mInitialMotionY!![pointerId]

                        if (mDragState == STATE_DRAGGING) {
                            break
                        }

                        val toCapture = findTopChildUnder(x.toInt(), y.toInt())
                        if (checkTouchSlop(toCapture, dx, dy) && tryCaptureViewForDrag(toCapture, pointerId)) {
                            break
                        }
                    }
                    saveLastMotion(ev)
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = ev.getPointerId(actionIndex)
                if (mDragState == STATE_DRAGGING && pointerId == mActivePointerId) {
                    var newActivePointer = INVALID_POINTER
                    val pointerCount = ev.pointerCount
                    for (i in 0 until pointerCount) {
                        val id = ev.getPointerId(i)
                        if (id == mActivePointerId) {
                            continue
                        }

                        val x = ev.getX(i)
                        val y = ev.getY(i)
                        if (findTopChildUnder(x.toInt(), y.toInt()) === mCapturedView &&
                            tryCaptureViewForDrag(mCapturedView, id)
                        ) {
                            newActivePointer = mActivePointerId
                            break
                        }
                    }

                    if (newActivePointer == INVALID_POINTER) {
                        releaseViewForPointerUp()
                    }
                }
                clearMotionHistory(pointerId)
            }

            MotionEvent.ACTION_UP -> {
                if (mDragState == STATE_DRAGGING) {
                    releaseViewForPointerUp()
                }
                cancel()
            }

            MotionEvent.ACTION_CANCEL -> {
                if (mDragState == STATE_DRAGGING) {
                    dispatchViewReleased(0f, 0f)
                }
                cancel()
            }
        }
    }

    private fun checkTouchSlop(child: View?, dx: Float, dy: Float): Boolean {
        if (child == null) {
            return false
        }
        val checkHorizontal = mCallback.getViewHorizontalDragRange(child) > 0
        val checkVertical = mCallback.getViewVerticalDragRange(child) > 0

        if (checkHorizontal && checkVertical) {
            return dx * dx + dy * dy > (mTouchSlop * mTouchSlop).toFloat()
        } else if (checkHorizontal) {
            return Math.abs(dx) > mTouchSlop.toFloat()
        } else if (checkVertical) {
            return Math.abs(dy) > mTouchSlop.toFloat()
        }
        return false
    }

    private fun releaseViewForPointerUp() {
        val velocityTracker = mVelocityTracker!!
        velocityTracker.computeCurrentVelocity(1000, mMaxVelocity)
        val xvel = clampMag(velocityTracker.getXVelocity(mActivePointerId), mMinVelocity, mMaxVelocity)
        val yvel = clampMag(velocityTracker.getYVelocity(mActivePointerId), mMinVelocity, mMaxVelocity)
        dispatchViewReleased(xvel, yvel)
    }

    private fun dragTo(left: Int, top: Int, dx: Int, dy: Int) {
        var clampedX = left
        var clampedY = top
        val capturedView = mCapturedView!!
        val oldLeft = capturedView.left
        val oldTop = capturedView.top
        if (dx != 0) {
            clampedX = mCallback.clampViewPositionHorizontal(capturedView, left, dx)
            ViewCompat.offsetLeftAndRight(capturedView, clampedX - oldLeft)
        }
        if (dy != 0) {
            clampedY = mCallback.clampViewPositionVertical(capturedView, top, dy)
            ViewCompat.offsetTopAndBottom(capturedView, clampedY - oldTop)
        }

        if (dx != 0 || dy != 0) {
            val clampedDx = clampedX - oldLeft
            val clampedDy = clampedY - oldTop
            mCallback.onViewPositionChanged(capturedView, clampedX, clampedY, clampedDx, clampedDy)
        }
    }

    /**
     * Determine if the currently captured view is under the given point in the
     * parent view's coordinate system. If there is no captured view this method
     * will return false.
     *
     * @param x X position to test in the parent's coordinate system
     * @param y Y position to test in the parent's coordinate system
     * @return true if the captured view is under the given point, false otherwise
     */
    fun isCapturedViewUnder(x: Int, y: Int): Boolean = isViewUnder(mCapturedView, x, y)

    /**
     * Determine if the supplied view is under the given point in the
     * parent view's coordinate system.
     *
     * @param view Child view of the parent to hit test
     * @param x    X position to test in the parent's coordinate system
     * @param y    Y position to test in the parent's coordinate system
     * @return true if the supplied view is under the given point, false otherwise
     */
    fun isViewUnder(view: View?, x: Int, y: Int): Boolean {
        if (view == null) {
            return false
        }
        return x >= view.left && x < view.right && y >= view.top && y < view.bottom
    }

    /**
     * Find the topmost child under the given point within the parent view's coordinate system.
     * The child order is determined using [Callback.getOrderedChildIndex].
     *
     * @param x X position to test in the parent's coordinate system
     * @param y Y position to test in the parent's coordinate system
     * @return The topmost child view under (x, y) or null if none found.
     */
    fun findTopChildUnder(x: Int, y: Int): View? {
        val childCount = mParentView.childCount
        for (i in childCount - 1 downTo 0) {
            val child = mParentView.getChildAt(mCallback.getOrderedChildIndex(i))
            if (x >= child.left && x < child.right && y >= child.top && y < child.bottom) {
                return child
            }
        }
        return null
    }

    /** @noinspection BooleanMethodIsAlwaysInverted */
    private fun isValidPointerForActionMove(pointerId: Int): Boolean {
        if (!isPointerDown(pointerId)) {
            Log.e(
                TAG,
                "Ignoring pointerId=$pointerId because ACTION_DOWN was not received " +
                    "for this pointer before ACTION_MOVE. It likely happened because " +
                    " ViewDragHelper did not receive all the events in the event stream."
            )
            return false
        }
        return true
    }

    companion object {
        private const val TAG = "ViewDragHelper"

        /**
         * A null/invalid pointer ID.
         */
        const val INVALID_POINTER = -1

        /**
         * A view is not currently being dragged or animating as a result of a fling/snap.
         */
        const val STATE_IDLE = 0

        /**
         * A view is currently being dragged. The position is currently changing as a result
         * of user input or simulated user input.
         */
        const val STATE_DRAGGING = 1

        /**
         * A view is currently settling into place as a result of a fling or
         * predefined non-interactive motion.
         */
        const val STATE_SETTLING = 2

        private const val BASE_SETTLE_DURATION = 256
        private const val MAX_SETTLE_DURATION = 600

        private val sInterpolator = Interpolator { t ->
            var value = t
            value -= 1.0f
            value * value * value * value * value + 1.0f
        }

        /**
         * Factory method to create a new ViewDragHelper.
         *
         * @param forParent Parent view to monitor
         * @param cb        Callback to provide information and receive events
         * @return a new ViewDragHelper instance
         */
        @JvmStatic
        fun create(forParent: ViewGroup, cb: Callback): SheetDragHelper = SheetDragHelper(forParent.context, forParent, cb)
    }
}
