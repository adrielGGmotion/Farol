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

package com.stario.launcher.ui.common.glance

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import androidx.annotation.FloatRange
import com.stario.launcher.R
import com.stario.launcher.ui.Measurements
import carbon.widget.ConstraintLayout
import kotlin.math.min
import androidx.core.graphics.withClip

@SuppressLint("AppCompatCustomView")
@Suppress("CONFLICTING_INHERITED_JVM_DECLARATIONS")
open class GlanceConstraintLayout : ConstraintLayout {
    @JvmField val RADIUS_AUTO = -1f
    private lateinit var strokePaint: Paint
    private var borderStrokeWidth = 0f
    private var minRadius = 0f
    private var maxRadius = 0f
    private var fraction = 0f
    private lateinit var clipPath: Path

    constructor(context: Context) : super(context) { init(null, 0) }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { init(attrs, 0) }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { init(attrs, defStyleAttr) }

    private fun init(attrs: AttributeSet?, defStyleAttr: Int) {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        minRadius = Measurements.dpToPx(30f).toFloat()
        maxRadius = RADIUS_AUTO
        fraction = 1f
        clipPath = Path()
        strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        borderStrokeWidth = 0f
        var strokeColor = Color.TRANSPARENT
        if (attrs != null) {
            val a = context.theme.obtainStyledAttributes(attrs, R.styleable.GlanceConstraintLayout, defStyleAttr, 0)
            try {
                borderStrokeWidth = a.getDimensionPixelSize(R.styleable.GlanceConstraintLayout_strokeWidth, 0).toFloat()
                strokeColor = a.getColor(R.styleable.GlanceConstraintLayout_strokeColor, Color.TRANSPARENT)
            } finally {
                a.recycle()
            }
        }
        strokePaint.strokeWidth = borderStrokeWidth * 2f
        strokePaint.color = strokeColor
    }

    open fun setMaxRadius(@FloatRange(from = 0.0) radius: Float) {
        maxRadius = radius
        invalidate()
    }

    override fun setScaleX(scaleX: Float) = Unit

    override fun setScaleY(scaleY: Float) {
        super.setScaleY(scaleY)
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        buildClipPath()
        canvas.withClip(clipPath) {
            super.draw(canvas)
            if (borderStrokeWidth > 0f) canvas.drawPath(clipPath, strokePaint)
        }
    }

    open fun setRadiusPercentage(@FloatRange(from = 0.0, to = 1.0) radius: Float) {
        fraction = radius
        invalidate()
    }

    private fun buildClipPath() {
        clipPath.reset()
        val maxRadius = if (this.maxRadius == RADIUS_AUTO) min(measuredHeight, measuredWidth) * scaleY * 0.5f else this.maxRadius
        var radius = fraction * maxRadius + (1 - fraction) * minRadius
        radius = min(radius, width / 2f)
        radius = min(radius, height / 2f * scaleY)
        val radiusY = radius / scaleY
        clipPath.addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()),
            floatArrayOf(radius, radiusY, radius, radiusY, radius, radiusY, radius, radiusY), Path.Direction.CW)
    }
}
