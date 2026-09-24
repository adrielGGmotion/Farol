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

package com.stario.launcher.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.FrameLayout
import com.stario.launcher.R
import kotlin.math.min

open class FadingEdgeLayout : FrameLayout {
    companion object {
        private const val DEFAULT_GRADIENT_SIZE_DP = 80
        const val FADE_EDGE_TOP = 1
        const val FADE_EDGE_BOTTOM = 2
        const val FADE_EDGE_LEFT = 4
        const val FADE_EDGE_RIGHT = 8
        private const val DIRTY_FLAG_TOP = 1
        private const val DIRTY_FLAG_BOTTOM = 2
        private const val DIRTY_FLAG_LEFT = 4
        private const val DIRTY_FLAG_RIGHT = 8
        private val FADE_COLORS = intArrayOf(Color.TRANSPARENT, Color.BLACK)
        private val FADE_COLORS_REVERSE = intArrayOf(Color.BLACK, Color.TRANSPARENT)
    }

    private var fadeTop = false
    private var fadeBottom = false
    private var fadeLeft = false
    private var fadeRight = false
    private var gradientSizeTop = 0
    private var gradientSizeBottom = 0
    private var gradientSizeLeft = 0
    private var gradientSizeRight = 0
    private var rounded = false
    private lateinit var gradientPaintTop: Paint
    private lateinit var gradientPaintBottom: Paint
    private lateinit var gradientPaintLeft: Paint
    private lateinit var gradientPaintRight: Paint
    private lateinit var cornerPaint: Paint
    private lateinit var gradientRectTop: Rect
    private lateinit var gradientRectBottom: Rect
    private lateinit var gradientRectLeft: Rect
    private lateinit var gradientRectRight: Rect
    private var gradientDirtyFlags = 0

    constructor(context: Context) : super(context) { init(null) }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { init(attrs) }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { init(attrs) }

    private fun init(attrs: AttributeSet?) {
        val defaultSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DEFAULT_GRADIENT_SIZE_DP.toFloat(), resources.displayMetrics).toInt()
        if (attrs != null) {
            val arr = context.obtainStyledAttributes(attrs, R.styleable.FadingEdgeLayout, 0, 0)
            val flags = arr.getInt(R.styleable.FadingEdgeLayout_edge, 0)
            fadeTop = flags and FADE_EDGE_TOP == FADE_EDGE_TOP
            fadeBottom = flags and FADE_EDGE_BOTTOM == FADE_EDGE_BOTTOM
            fadeLeft = flags and FADE_EDGE_LEFT == FADE_EDGE_LEFT
            fadeRight = flags and FADE_EDGE_RIGHT == FADE_EDGE_RIGHT
            gradientSizeTop = arr.getDimensionPixelSize(R.styleable.FadingEdgeLayout_size_top, defaultSize)
            gradientSizeBottom = arr.getDimensionPixelSize(R.styleable.FadingEdgeLayout_size_bottom, defaultSize)
            gradientSizeLeft = arr.getDimensionPixelSize(R.styleable.FadingEdgeLayout_size_left, defaultSize)
            gradientSizeRight = arr.getDimensionPixelSize(R.styleable.FadingEdgeLayout_size_right, defaultSize)
            rounded = arr.getBoolean(R.styleable.FadingEdgeLayout_is_rounded, false)
            if (fadeTop && gradientSizeTop > 0) gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_TOP
            if (fadeLeft && gradientSizeLeft > 0) gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_LEFT
            if (fadeBottom && gradientSizeBottom > 0) gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_BOTTOM
            if (fadeRight && gradientSizeRight > 0) gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_RIGHT
            arr.recycle()
        } else {
            gradientSizeTop = defaultSize
            gradientSizeBottom = defaultSize
            gradientSizeLeft = defaultSize
            gradientSizeRight = defaultSize
            rounded = false
        }
        gradientPaintTop = createFadePaint()
        gradientPaintBottom = createFadePaint()
        gradientPaintLeft = createFadePaint()
        gradientPaintRight = createFadePaint()
        cornerPaint = createFadePaint()
        gradientRectTop = Rect()
        gradientRectLeft = Rect()
        gradientRectBottom = Rect()
        gradientRectRight = Rect()
    }

    private fun createFadePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    open fun isRounded(): Boolean = rounded

    open fun setRounded(rounded: Boolean) {
        if (this.rounded != rounded) {
            this.rounded = rounded
            gradientDirtyFlags = DIRTY_FLAG_TOP or DIRTY_FLAG_LEFT or DIRTY_FLAG_BOTTOM or DIRTY_FLAG_TOP
            invalidate()
        }
    }

    open fun setFadeSizes(top: Int, left: Int, bottom: Int, right: Int) {
        if (gradientSizeTop != top) { gradientSizeTop = top; gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_TOP }
        if (gradientSizeLeft != left) { gradientSizeLeft = left; gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_LEFT }
        if (gradientSizeBottom != bottom) { gradientSizeBottom = bottom; gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_BOTTOM }
        if (gradientSizeRight != right) { gradientSizeRight = right; gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_RIGHT }
        if (gradientDirtyFlags != 0) invalidate()
    }

    open fun setFadeEdges(fadeTop: Boolean, fadeLeft: Boolean, fadeBottom: Boolean, fadeRight: Boolean) {
        if (this.fadeTop != fadeTop) { this.fadeTop = fadeTop; gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_TOP }
        if (this.fadeLeft != fadeLeft) { this.fadeLeft = fadeLeft; gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_LEFT }
        if (this.fadeBottom != fadeBottom) { this.fadeBottom = fadeBottom; gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_BOTTOM }
        if (this.fadeRight != fadeRight) { this.fadeRight = fadeRight; gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_RIGHT }
        if (gradientDirtyFlags != 0) invalidate()
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        if (paddingLeft != left) gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_LEFT
        if (paddingTop != top) gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_TOP
        if (paddingRight != right) gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_RIGHT
        if (paddingBottom != bottom) gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_BOTTOM
        super.setPadding(left, top, right, bottom)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw) gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_LEFT or DIRTY_FLAG_RIGHT
        if (h != oldh) gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_TOP or DIRTY_FLAG_BOTTOM
        invalidate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            gradientDirtyFlags = gradientDirtyFlags or DIRTY_FLAG_TOP or DIRTY_FLAG_BOTTOM or DIRTY_FLAG_LEFT or DIRTY_FLAG_RIGHT
            invalidate()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        val width = width
        val height = height
        if (visibility == GONE || width == 0 || height == 0 || !(fadeTop || fadeBottom || fadeLeft || fadeRight)) {
            super.dispatchDraw(canvas)
            return
        }
        if (gradientDirtyFlags and DIRTY_FLAG_TOP != 0) { gradientDirtyFlags = gradientDirtyFlags and DIRTY_FLAG_TOP.inv(); initTopGradient() }
        if (gradientDirtyFlags and DIRTY_FLAG_LEFT != 0) { gradientDirtyFlags = gradientDirtyFlags and DIRTY_FLAG_LEFT.inv(); initLeftGradient() }
        if (gradientDirtyFlags and DIRTY_FLAG_BOTTOM != 0) { gradientDirtyFlags = gradientDirtyFlags and DIRTY_FLAG_BOTTOM.inv(); initBottomGradient() }
        if (gradientDirtyFlags and DIRTY_FLAG_RIGHT != 0) { gradientDirtyFlags = gradientDirtyFlags and DIRTY_FLAG_RIGHT.inv(); initRightGradient() }
        val count = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.dispatchDraw(canvas)
        var radiusTl = 0
        var radiusTr = 0
        var radiusBl = 0
        var radiusBr = 0
        if (rounded) {
            if (fadeTop && fadeLeft) radiusTl = min(gradientSizeTop, gradientSizeLeft)
            if (fadeTop && fadeRight) radiusTr = min(gradientSizeTop, gradientSizeRight)
            if (fadeBottom && fadeLeft) radiusBl = min(gradientSizeBottom, gradientSizeLeft)
            if (fadeBottom && fadeRight) radiusBr = min(gradientSizeBottom, gradientSizeRight)
        }
        if (fadeTop && gradientSizeTop > 0) {
            val rect = Rect(gradientRectTop)
            rect.left += radiusTl
            rect.right -= radiusTr
            if (rect.left < rect.right) canvas.drawRect(rect, gradientPaintTop)
        }
        if (fadeBottom && gradientSizeBottom > 0) {
            val rect = Rect(gradientRectBottom)
            rect.left += radiusBl
            rect.right -= radiusBr
            if (rect.left < rect.right) canvas.drawRect(rect, gradientPaintBottom)
        }
        if (fadeLeft && gradientSizeLeft > 0) {
            val rect = Rect(gradientRectLeft)
            rect.top += radiusTl
            rect.bottom -= radiusBl
            if (rect.top < rect.bottom) canvas.drawRect(rect, gradientPaintLeft)
        }
        if (fadeRight && gradientSizeRight > 0) {
            val rect = Rect(gradientRectRight)
            rect.top += radiusTr
            rect.bottom -= radiusBr
            if (rect.top < rect.bottom) canvas.drawRect(rect, gradientPaintRight)
        }
        if (rounded) {
            if (radiusTl > 0) {
                val cx = paddingLeft + radiusTl.toFloat()
                val cy = paddingTop + radiusTl.toFloat()
                cornerPaint.shader = RadialGradient(cx, cy, radiusTl.toFloat(), FADE_COLORS_REVERSE, null, Shader.TileMode.CLAMP)
                canvas.drawRect(paddingLeft.toFloat(), paddingTop.toFloat(), cx, cy, cornerPaint)
            }
            if (radiusTr > 0) {
                val cx = width - paddingRight - radiusTr.toFloat()
                val cy = paddingTop + radiusTr.toFloat()
                cornerPaint.shader = RadialGradient(cx, cy, radiusTr.toFloat(), FADE_COLORS_REVERSE, null, Shader.TileMode.CLAMP)
                canvas.drawRect(cx, paddingTop.toFloat(), (width - paddingRight).toFloat(), cy, cornerPaint)
            }
            if (radiusBl > 0) {
                val cx = paddingLeft + radiusBl.toFloat()
                val cy = height - paddingBottom - radiusBl.toFloat()
                cornerPaint.shader = RadialGradient(cx, cy, radiusBl.toFloat(), FADE_COLORS_REVERSE, null, Shader.TileMode.CLAMP)
                canvas.drawRect(paddingLeft.toFloat(), cy, cx, (height - paddingBottom).toFloat(), cornerPaint)
            }
            if (radiusBr > 0) {
                val cx = width - paddingRight - radiusBr.toFloat()
                val cy = height - paddingBottom - radiusBr.toFloat()
                cornerPaint.shader = RadialGradient(cx, cy, radiusBr.toFloat(), FADE_COLORS_REVERSE, null, Shader.TileMode.CLAMP)
                canvas.drawRect(cx, cy, (width - paddingRight).toFloat(), (height - paddingBottom).toFloat(), cornerPaint)
            }
        }
        canvas.restoreToCount(count)
    }

    private fun initTopGradient() {
        val size = min(gradientSizeTop, height - paddingTop - paddingBottom)
        val l = paddingLeft
        val t = paddingTop
        val r = width - paddingRight
        val b = t + size
        gradientRectTop.set(l, t, r, b)
        gradientPaintTop.shader = LinearGradient(l.toFloat(), t.toFloat(), l.toFloat(), b.toFloat(), FADE_COLORS, null, Shader.TileMode.CLAMP)
    }

    private fun initLeftGradient() {
        val size = min(gradientSizeLeft, width - paddingLeft - paddingRight)
        val l = paddingLeft
        val t = paddingTop
        val r = l + size
        val b = height - paddingBottom
        gradientRectLeft.set(l, t, r, b)
        gradientPaintLeft.shader = LinearGradient(l.toFloat(), t.toFloat(), r.toFloat(), t.toFloat(), FADE_COLORS, null, Shader.TileMode.CLAMP)
    }

    private fun initBottomGradient() {
        val actualHeight = height - paddingTop - paddingBottom
        val size = min(gradientSizeBottom, actualHeight)
        val l = paddingLeft
        val t = paddingTop + actualHeight - size
        val r = width - paddingRight
        val b = t + size
        gradientRectBottom.set(l, t, r, b)
        gradientPaintBottom.shader = LinearGradient(l.toFloat(), t.toFloat(), l.toFloat(), b.toFloat(), FADE_COLORS_REVERSE, null, Shader.TileMode.CLAMP)
    }

    private fun initRightGradient() {
        val actualWidth = width - paddingLeft - paddingRight
        val size = min(gradientSizeRight, actualWidth)
        val l = paddingLeft + actualWidth - size
        val t = paddingTop
        val r = l + size
        val b = height - paddingBottom
        gradientRectRight.set(l, t, r, b)
        gradientPaintRight.shader = LinearGradient(l.toFloat(), t.toFloat(), r.toFloat(), t.toFloat(), FADE_COLORS_REVERSE, null, Shader.TileMode.CLAMP)
    }
}
