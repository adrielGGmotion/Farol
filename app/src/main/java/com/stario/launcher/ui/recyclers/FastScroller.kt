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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

// reference https://github.com/quiph/RecyclerView-FastScroller/blob/master/recyclerviewfastscroller/src/main/java/com/qtalk/recyclerviewfastscroller/RecyclerViewFastScroller.kt

package com.stario.launcher.ui.recyclers

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.stario.launcher.R
import com.stario.launcher.ui.utils.animation.Animation

open class FastScroller : RelativeLayout {
    private var recyclerView: RecyclerView? = null
    private lateinit var popup: TextView
    private lateinit var trackRight: View
    private lateinit var trackLeft: View
    private var x: Float? = null
    private var y: Float? = null
    private var alphaLeft = 0f
    private var alphaRight = 0f
    private var currentPosition = -1
    private var trackLength = 0
    private var popupHeight = 0
    private var swipeSlop = 0
    private var isEngaged = false
    private var lastPositionScrolled = -1

    constructor(context: Context) : super(context) {
        initialize(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initialize(context)
    }

    private fun initialize(context: Context) {
        x = null
        y = null
        alphaLeft = 0f
        alphaRight = 0f
        currentPosition = -1
        isEngaged = false
        swipeSlop = ViewConfiguration.get(context).getScaledTouchSlop()
        lastPositionScrolled = -1
        addPopupLayout()
        addTrack()
    }

    protected open override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        trackLength = trackRight.getHeight()
        popupHeight = popup.getHeight()
    }

    protected open override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        updateTrackWidth()
    }

    open fun setTopOffset(topOffset: Int) {
        (trackRight.getLayoutParams() as ViewGroup.MarginLayoutParams).topMargin = topOffset
        (trackLeft.getLayoutParams() as ViewGroup.MarginLayoutParams).topMargin = topOffset
    }

    open fun setBottomOffset(bottomOffset: Int) {
        (trackRight.getLayoutParams() as ViewGroup.MarginLayoutParams).bottomMargin = bottomOffset
        (trackLeft.getLayoutParams() as ViewGroup.MarginLayoutParams).bottomMargin = bottomOffset
    }

    private fun addPopupLayout() {
        View.inflate(getContext(), R.layout.fastscroller_popup, this)
        popup = findViewById(R.id.fast_scroller_pop_up)
    }

    private fun addTrack() {
        View.inflate(getContext(), R.layout.fastscroller_track_thumb, this)
        trackRight = findViewById(R.id.track_right)
        trackLeft = findViewById(R.id.track_left)
    }

    protected open override fun onDetachedFromWindow() {
        detachFastScrollerFromRecyclerView()
        super.onDetachedFromWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    open fun detachFastScrollerFromRecyclerView() {
        popup.setOnTouchListener(null)
    }

    @SuppressLint("ClickableViewAccessibility")
    protected open override fun onFinishInflate() {
        super.onFinishInflate()

        // two tracks + popup view which are inflated before everything else
        if (getChildCount() > 3) {
            val child = getChildAt(3)
            removeView(child)
            addView(child, 0)

            recyclerView = findRecycler(child)
            if (recyclerView != null) {
                updateTrackWidth()
            }

            post {
                val listener = View.OnTouchListener { view, motionEvent ->
                    val recycler = recyclerView
                    val adapter = recycler?.getAdapter()
                    if (recycler != null && adapter != null) {
                        if ((x == null && y == null) || motionEvent.action == MotionEvent.ACTION_DOWN) {
                            x = motionEvent.rawX
                            y = motionEvent.rawY
                            (view as ViewGroup).requestDisallowInterceptTouchEvent(true)
                        } else {
                            val initialX = x
                            val initialY = y
                            if (initialX == null || initialY == null) {
                                return@OnTouchListener true
                            }

                            val absDeltaX = Math.abs(motionEvent.rawX - initialX)
                            val absDeltaY = Math.abs(motionEvent.rawY - initialY)

                            if (absDeltaX > absDeltaY && absDeltaX >= swipeSlop.toFloat()) {
                                x = Float.MIN_VALUE

                                if (motionEvent.action == MotionEvent.ACTION_UP ||
                                    motionEvent.action == MotionEvent.ACTION_CANCEL
                                ) {
                                    isEngaged = false
                                    post {
                                        animatePopupVisibility(false, view == trackRight)
                                    }

                                    if (adapter is OnPopupViewReset) {
                                        adapter.onReset(currentPosition)
                                    }

                                    x = null
                                    y = null
                                }

                                return@OnTouchListener false
                            } else if (absDeltaY > absDeltaX && absDeltaY >= swipeSlop.toFloat()) {
                                y = Float.MIN_VALUE
                                recycler.stopScroll()

                                when (motionEvent.action) {
                                    MotionEvent.ACTION_MOVE -> {
                                        if (!isEngaged) {
                                            isEngaged = true
                                            animatePopupVisibility(true, view == trackRight)
                                        }

                                        val currentRelativePos = motionEvent.y
                                        moveViewToRelativePositionWithBounds(
                                            currentRelativePos - popupHeight.toFloat() / 2f
                                        )
                                        val position = minOf(
                                            adapter.getItemCount() - 1,
                                            computePositionForOffsetAndScroll(currentRelativePos)
                                        )
                                        updateTextInPopup(position)
                                        return@OnTouchListener true
                                    }

                                    MotionEvent.ACTION_UP,
                                    MotionEvent.ACTION_CANCEL -> {
                                        isEngaged = false
                                        post {
                                            animatePopupVisibility(false, view == trackRight)
                                        }

                                        if (adapter is OnPopupViewReset) {
                                            adapter.onReset(currentPosition)
                                        }

                                        x = null
                                        y = null
                                        return@OnTouchListener super@FastScroller.onTouchEvent(motionEvent)
                                    }

                                    else -> return@OnTouchListener false
                                }
                            }

                            return@OnTouchListener true
                        }
                    } else if (motionEvent.action == MotionEvent.ACTION_UP ||
                        motionEvent.action == MotionEvent.ACTION_CANCEL
                    ) {
                        x = null
                        y = null
                    }

                    true
                }

                trackLeft.setOnTouchListener(listener)
                trackRight.setOnTouchListener(listener)
                invalidate()
            }
        }
    }

    private fun findRecycler(view: View): RecyclerView? {
        if (view is RecyclerView) {
            return view
        }

        if (view is ViewGroup) {
            for (index in 0 until view.getChildCount()) {
                val recycler = findRecycler(view.getChildAt(index))
                if (recycler != null) {
                    return recycler
                }
            }
        }

        return null
    }

    private fun computePositionForOffsetAndScroll(rawPosition: Float): Int {
        val recycler = recyclerView ?: return 0
        val layoutManager = recycler.getLayoutManager()
        val adapter = recycler.getAdapter()
        val recyclerViewItemCount = adapter?.getItemCount() ?: 0
        var newOffset = rawPosition / trackLength
        newOffset = maxOf(minOf(newOffset, 1f), 0f)

        if (layoutManager is LinearLayoutManager) {
            val last = if (layoutManager.getReverseLayout()) {
                recyclerViewItemCount - (newOffset * recyclerViewItemCount).toInt()
            } else {
                (newOffset * recyclerViewItemCount).toInt()
            }
            val position = minOf(recyclerViewItemCount, maxOf(0, last))
            safeScrollToPosition(minOf(adapter?.getItemCount() ?: 0, position))
            return position
        }

        val position = (newOffset * recyclerViewItemCount).toInt()
        safeScrollToPosition(position)
        return position
    }

    private fun safeScrollToPosition(position: Int) {
        if (position != lastPositionScrolled) {
            val recycler = recyclerView
            if (recycler != null) {
                recycler.scrollToPosition(position)
                lastPositionScrolled = position
            }
        }
    }

    private fun updateTextInPopup(position: Int) {
        val recycler = recyclerView
        if (recycler != null && currentPosition != position) {
            val adapter = recycler.getAdapter()
            if (adapter == null || position < 0 || position >= adapter.getItemCount()) {
                return
            }

            currentPosition = position
            if (adapter is OnPopupViewUpdate) {
                adapter.onUpdate(position, popup)
            } else {
                popup.setVisibility(View.GONE)
            }
        }
    }

    private fun alignPopupLayout(rightSide: Boolean) {
        val params = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        if (rightSide) {
            params.addRule(RelativeLayout.LEFT_OF, trackRight.getId())
            post {
                popup.setX(trackRight.getX() - popup.getWidth())
            }
        } else {
            params.addRule(RelativeLayout.RIGHT_OF, trackLeft.getId())
            post {
                popup.setX(trackLeft.getX() + trackLeft.getWidth())
            }
        }

        popup.setLayoutParams(params)
    }

    open fun animateVisibility(makeVisible: Boolean) {
        animateVisibility(makeVisible, false, 0.2f, 0f)
        animateVisibility(makeVisible, true, 0.2f, 0f)
    }

    private fun animateVisibility(makeVisible: Boolean, rightSide: Boolean, maxAlpha: Float, minAlpha: Float) {
        val alpha = if (makeVisible) maxAlpha else minAlpha
        if (rightSide) {
            if (alphaRight != alpha) {
                trackRight.animate()
                    .alpha(alpha)
                    .setDuration(Animation.SHORT.getDuration().toLong())
                alphaRight = alpha
            }
        } else if (alphaLeft != alpha) {
            trackLeft.animate()
                .alpha(alpha)
                .setDuration(Animation.SHORT.getDuration().toLong())
            alphaLeft = alpha
        }
    }

    private fun animatePopupVisibility(makeVisible: Boolean, rightSide: Boolean) {
        val scaleFactor = if (makeVisible) 1f else 0.5f
        val alpha = if (makeVisible) 1f else 0f
        alignPopupLayout(rightSide)
        popup.setPivotX(popup.getWidth() * if (rightSide) 0.75f else 0.25f)
        popup.animate()
            .scaleX(scaleFactor)
            .scaleY(scaleFactor)
            .alpha(alpha)
            .setDuration(Animation.MEDIUM.getDuration().toLong())
        animateVisibility(makeVisible, rightSide, 0.6f, 0.2f)
    }

    private fun moveViewToRelativePositionWithBounds(offsetValue: Float) {
        var offset = offsetValue
        if (offset.isNaN()) {
            offset = 0f
        }

        popup.setY(
            trackRight.getY() + minOf(
                maxOf(offset, 0f),
                (trackLength - popupHeight).toFloat()
            )
        )
    }

    private fun updateTrackWidth() {
        val recycler = recyclerView
        if (recycler == null || recycler.getMeasuredHeight() < getMeasuredHeight()) {
            trackLeft.setVisibility(View.GONE)
            trackRight.setVisibility(View.GONE)
        } else {
            trackLeft.getLayoutParams().width = recycler.getPaddingLeft()
            trackRight.getLayoutParams().width = recycler.getPaddingRight()
            trackLeft.setVisibility(View.VISIBLE)
            trackRight.setVisibility(View.VISIBLE)
            invalidate()
        }
    }

    fun interface OnPopupViewReset {
        fun onReset(position: Int)
    }

    fun interface OnPopupViewUpdate {
        fun onUpdate(index: Int, textView: TextView)
    }
}
