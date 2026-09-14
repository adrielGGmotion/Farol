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

package com.stario.launcher.ui.common.text

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatTextView
import com.stario.launcher.ui.Measurements
import com.stario.launcher.ui.utils.animation.Animation

class PulsingTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatTextView(context, attrs, defStyleAttr) {
    private var animator: ValueAnimator? = null
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private var pulsating = true
    private var evenColors: IntArray? = null
    private var oddColors: IntArray? = null
    private var offset = 0f

    init {
        animator = ValueAnimator.ofFloat(0f, 0f).apply {
            duration = Animation.SUSTAINED.getDuration().toLong()
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
        }
    }

    fun setPulsating(pulsating: Boolean) {
        this.pulsating = pulsating
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator?.start()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        post(::calculateGradients)
    }

    override fun invalidate() {
        offset = (animator?.animatedFraction ?: 0f) * 2f
        super.invalidate()
    }

    private fun calculateGradients() {
        val colors = ArrayList<Int>()
        val fadeLength = Measurements.dpToPx(FADE_LENGTH)
        var measuredWidth = width
        while (measuredWidth > -fadeLength) {
            colors.add(Color.BLACK)
            colors.add(Color.argb(0.5f, 0, 0, 0))
            measuredWidth -= fadeLength
        }

        evenColors = IntArray(colors.size)
        oddColors = IntArray(colors.size)
        for (index in 0 until colors.size - 1) {
            val color = colors[index]
            evenColors!![index] = color
            oddColors!![index + 1] = color
        }
        val lastColor = colors[colors.size - 1]
        evenColors!![colors.size - 1] = lastColor
        oddColors!![0] = lastColor
    }

    override fun onDraw(canvas: Canvas) {
        if (!pulsating || visibility != VISIBLE) {
            super.onDraw(canvas)
            return
        }
        if (evenColors != null) {
            initGradient()
            val count = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
            super.onDraw(canvas)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gradientPaint)
            canvas.restoreToCount(count)
        }
        invalidate()
    }

    private fun initGradient() {
        val colors = if (offset.toInt() % 2 == 0) evenColors!! else oddColors!!
        gradientPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height * 0.3f,
            colors,
            generatePositions(),
            Shader.TileMode.CLAMP,
        )
    }

    private fun generatePositions(): FloatArray {
        val fadeLength = Measurements.dpToPx(FADE_LENGTH).toFloat()
        val positions = FloatArray(evenColors!!.size)
        val interval = (1f + fadeLength / width) / positions.size
        for (index in positions.indices) {
            positions[index] = interval * ((index - 1) + offset % 1f)
        }
        return positions
    }

    companion object {
        private const val FADE_LENGTH = 50
    }
}
