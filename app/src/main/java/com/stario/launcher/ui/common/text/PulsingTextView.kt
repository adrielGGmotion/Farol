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
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatTextView
import com.stario.launcher.ui.Measurements
import com.stario.launcher.ui.utils.animation.Animation
import java.util.ArrayList

open class PulsingTextView : AppCompatTextView {
    private lateinit var animator: ValueAnimator
    private lateinit var gradientPaint: Paint
    private var pulsatingState = false
    private var evenColors: IntArray? = null
    private var oddColors: IntArray? = null
    private var offset = 0f

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        offset = 0f
        pulsatingState = true

        gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        gradientPaint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.DST_IN))

        animator = ValueAnimator.ofFloat(0f, 0f).setDuration(Animation.SUSTAINED.getDuration().toLong())
        animator.setInterpolator(LinearInterpolator())
        animator.repeatCount = ValueAnimator.INFINITE
    }

    open fun setPulsating(pulsating: Boolean) {
        pulsatingState = pulsating
        invalidate()
    }

    protected override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    protected override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    protected override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        post { calculateGradients() }
    }

    override fun invalidate() {
        if (this::animator.isInitialized) {
            offset = animator.animatedFraction * 2f
        }
        super.invalidate()
    }

    private fun calculateGradients() {
        val colors = ArrayList<Int>()
        val fadeLength = Measurements.dpToPx(Constants.FADE_LENGTH.toFloat())
        var width = getWidth()

        while (width > -fadeLength) {
            colors.add(Color.BLACK)
            colors.add(Color.argb(0.5f, 0f, 0f, 0f))
            width -= fadeLength
        }

        evenColors = IntArray(colors.size)
        oddColors = IntArray(colors.size)

        for (index in 0 until (colors.size - 1)) {
            val color = colors[index]
            evenColors!![index] = color
            oddColors!![index + 1] = color
        }

        val lastColor = colors[colors.size - 1]
        evenColors!![colors.size - 1] = lastColor
        oddColors!![0] = lastColor
    }

    protected override fun onDraw(canvas: Canvas) {
        if (pulsatingState) {
            if (visibility != View.VISIBLE) {
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
        } else {
            super.onDraw(canvas)
        }
    }

    private fun initGradient() {
        val gradient = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height * 0.3f,
            if (offset.toInt() % 2 == 0) evenColors!! else oddColors!!,
            generatePositions(),
            Shader.TileMode.CLAMP,
        )
        gradientPaint.setShader(gradient)
    }

    private fun generatePositions(): FloatArray {
        val fadeLength = Measurements.dpToPx(Constants.FADE_LENGTH.toFloat())
        val positions = FloatArray(evenColors!!.size)
        val interval = (1f + fadeLength.toFloat() / width) / positions.size

        for (index in positions.indices) {
            positions[index] = interval * ((index - 1) + offset % 1)
        }

        return positions
    }

    private object Constants {
        const val FADE_LENGTH = 50
    }
}
