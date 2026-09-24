/*
 * Copyright (C) 2026 Răzvan Albu
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

package com.stario.launcher.ui.common.grid

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.Measurements

open class DraggableGridItem(context: Context) : FrameLayout(context) {
    private val borderPaint: Paint
    private val handlePaint: Paint
    private var isVisualResizeEnabled = false
    private var alphaAnimator: ValueAnimator? = null
    private var isResizingActive = false
    private var visualHeight = 0f
    private var visualWidth = 0f
    private var borderAlpha = 0f

    @JvmField var itemId: String? = null
    @JvmField var minColSpan = 1
    @JvmField var minWidth = -1
    @JvmField var maxColSpan = -1
    @JvmField var maxWidth = -1
    @JvmField var minRowSpan = 1
    @JvmField var minHeight = -1
    @JvmField var maxRowSpan = -1
    @JvmField var maxHeight = -1

    init {
        if (context !is ThemedActivity) {
            throw RuntimeException("Parent activity is not of type ThemedActivity.")
        }
        val color = context.getAttributeData(com.google.android.material.R.attr.colorPrimaryFixed)
        borderPaint = Paint().apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = Measurements.dpToPx(STROKE_WIDTH_DP.toFloat()).toFloat()
        }
        handlePaint = Paint().apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = Measurements.dpToPx(HANDLE_STROKE_WIDTH_DP.toFloat()).toFloat()
        }
        setWillNotDraw(false)
        val padding = Measurements.dpToPx(PADDING_DP.toFloat())
        setPadding(padding, padding, padding, padding)
    }

    internal fun setResizingActive(active: Boolean) {
        isResizingActive = active
        animateBorderAlpha(if (active) IDLE_ALPHA else 0f)
    }

    internal fun animateToState(state: Byte) {
        if (!isResizingActive) return
        when (state) {
            STATE_IDLE -> animateBorderAlpha(IDLE_ALPHA)
            STATE_ACTIVE -> animateBorderAlpha(ACTIVE_ALPHA)
            STATE_INACTIVE -> animateBorderAlpha(0f)
        }
    }

    private fun animateBorderAlpha(targetAlpha: Float) {
        alphaAnimator?.cancel()
        alphaAnimator = ValueAnimator.ofFloat(borderAlpha, targetAlpha).apply {
            duration = ALPHA_TRANSITION_DURATION
            addUpdateListener { animation ->
                borderAlpha = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    open fun setVisualResizeBounds(width: Float, height: Float) {
        isVisualResizeEnabled = true
        visualWidth = width
        visualHeight = height
        invalidate()
    }

    open fun animateVisualResize(targetW: Float, targetH: Float, onAnimationEnd: Runnable?) {
        val anim = ValueAnimator.ofFloat(0f, 1f)
        val startW = if (isVisualResizeEnabled) visualWidth else width.toFloat()
        val startH = if (isVisualResizeEnabled) visualHeight else height.toFloat()
        isVisualResizeEnabled = true
        anim.duration = 200
        anim.addUpdateListener { animation ->
            val frac = animation.animatedFraction
            visualWidth = startW + (targetW - startW) * frac
            visualHeight = startH + (targetH - startH) * frac
            invalidate()
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                isVisualResizeEnabled = false
                onAnimationEnd?.run()
            }
        })
        anim.start()
    }

    internal fun isResizeHandleTouched(x: Float, y: Float): Boolean {
        if (!isResizingActive) return false
        val handleSize = Measurements.dpToPx(HANDLE_SIZE_DP.toFloat())
        val width = if (isVisualResizeEnabled) visualWidth else width.toFloat()
        val height = if (isVisualResizeEnabled) visualHeight else height.toFloat()
        val resizeW = canResizeWidth()
        val resizeH = canResizeHeight()
        if (resizeW && resizeH) return x > width - handleSize && y > height - handleSize
        if (resizeW) return x > width - handleSize &&
            y > height / 2f - handleSize / 2f && y < height / 2f + handleSize / 2f
        if (resizeH) return y > height - handleSize &&
            x > width / 2f - handleSize / 2f && x < width / 2f + handleSize / 2f
        return false
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        if (childCount > 1) throw IllegalStateException("DraggableGridItem can host only one direct child.")
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (parent !is DynamicGridLayout) {
            throw IllegalStateException("DraggableGridItem can only be added to a DynamicGridLayout.")
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (borderAlpha > 0) {
            val cornerSize = Measurements.dpToPx(CORNER_RADIUS_DP.toFloat())
            val strokeWidth = Measurements.dpToPx(HANDLE_STROKE_WIDTH_DP.toFloat())
            val currentWidth = if (isVisualResizeEnabled) visualWidth else width.toFloat()
            val currentHeight = if (isVisualResizeEnabled) visualHeight else height.toFloat()
            val baseAlpha = 255
            borderPaint.alpha = (baseAlpha * borderAlpha).toInt()
            handlePaint.alpha = (baseAlpha * minOf(1f, borderAlpha / IDLE_ALPHA)).toInt()
            val rect = RectF(strokeWidth / 2f, strokeWidth / 2f,
                currentWidth - strokeWidth / 2f, currentHeight - strokeWidth / 2f)
            canvas.drawRoundRect(rect, cornerSize.toFloat(), cornerSize.toFloat(), borderPaint)
            val resizeW = canResizeWidth()
            val resizeH = canResizeHeight()
            if (resizeW || resizeH) {
                if (resizeW && resizeH) {
                    val handleRect = RectF(currentWidth - 2 * cornerSize - strokeWidth / 2f,
                        currentHeight - 2 * cornerSize - strokeWidth / 2f,
                        currentWidth - strokeWidth / 2f, currentHeight - strokeWidth / 2f)
                    canvas.drawArc(handleRect, 20f, 50f, false, handlePaint)
                } else if (resizeW) {
                    val left = currentWidth - strokeWidth / 2f
                    canvas.drawLine(left, currentHeight / 2f - cornerSize / 2f,
                        left, currentHeight / 2f + cornerSize / 2f, handlePaint)
                } else {
                    val top = currentHeight - strokeWidth / 2f
                    canvas.drawLine(currentWidth / 2f - cornerSize / 2f, top,
                        currentWidth / 2f + cornerSize / 2f, top, handlePaint)
                }
            }
        }
    }

    private fun canResizeWidth(): Boolean {
        val parent = parent as DynamicGridLayout
        val minComputedWidth = if (minWidth > 0) {
            if (minColSpan > 0) maxOf(minWidth, minColSpan * parent.getCellWidth()) else minWidth
        } else if (minColSpan > 0) minColSpan * parent.getCellWidth() else 0
        val maxComputedWidth = if (maxWidth > 0) {
            if (maxColSpan > 0) minOf(maxWidth, maxColSpan * parent.getCellWidth()) else maxWidth
        } else if (maxColSpan > 0) maxColSpan * parent.getCellWidth() else parent.width
        if (maxComputedWidth - minComputedWidth < parent.getCellWidth()) return false
        if (maxColSpan > 0) return minOf(maxColSpan, parent.getColumnCount()) > minColSpan
        return parent.getColumnCount() > minColSpan
    }

    private fun canResizeHeight(): Boolean {
        val parent = parent as DynamicGridLayout
        val minComputedHeight = if (minHeight > 0) {
            if (minRowSpan > 0) maxOf(minHeight, minRowSpan * parent.getCellHeight()) else minHeight
        } else if (minRowSpan > 0) minRowSpan * parent.getCellHeight() else 0
        val maxComputedHeight = if (maxHeight > 0) {
            if (maxRowSpan > 0) minOf(maxHeight, maxRowSpan * parent.getCellHeight()) else maxHeight
        } else if (maxRowSpan > 0) maxRowSpan * parent.getCellHeight() else parent.height
        if (maxComputedHeight - minComputedHeight < parent.getCellHeight()) return false
        if (maxRowSpan > 0) return minOf(maxRowSpan, parent.getRowCount()) > minRowSpan
        return parent.getRowCount() > minRowSpan
    }

    private fun canResize(): Boolean = canResizeWidth() || canResizeHeight()

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = isResizingActive

    companion object {
        const val STATE_ACTIVE: Byte = 4
        const val STATE_INACTIVE: Byte = 2
        const val STATE_IDLE: Byte = 1
        private const val HANDLE_STROKE_WIDTH_DP = 6
        private const val HANDLE_SIZE_DP = 40
        private const val CORNER_RADIUS_DP = 20
        private const val STROKE_WIDTH_DP = 2
        private const val ALPHA_TRANSITION_DURATION = 200L
        private const val IDLE_ALPHA = 0.4f
        private const val ACTIVE_ALPHA = 1.0f
        private const val PADDING_DP = 5
    }
}
