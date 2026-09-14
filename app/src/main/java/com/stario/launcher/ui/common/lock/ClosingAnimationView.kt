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

package com.stario.launcher.ui.common.lock

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.util.AttributeSet
import android.view.WindowManager
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import carbon.widget.ConstraintLayout
import com.stario.launcher.ui.utils.animation.Animation

class ClosingAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {
    private lateinit var activity: Activity
    private lateinit var paint: Paint
    private var closed = false
    private var animator: ValueAnimator? = null
    private var x = 0f
    private var y = 0f

    init {
        init(context)
    }

    fun init(context: Context) {
        activity = context as Activity
        closed = false
        paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!closed) return

        val value = animator!!.animatedValue as Float
        if (value <= 0f) {
            canvas.drawColor(Color.BLACK)
        } else {
            val abs = maxOf(measuredWidth - x, x)
            val ord = maxOf(measuredHeight - y, y)
            val radius = kotlin.math.sqrt(abs * abs + ord * ord)
            val gradient = RadialGradient(
                x,
                y,
                radius * value,
                Color.argb(1f - value, 0f, 0f, 0f),
                Color.BLACK,
                android.graphics.Shader.TileMode.CLAMP,
            )
            paint.shader = gradient
            paint.alpha = minOf(255, (500f * (1f - value)).toInt())
            canvas.drawCircle(x, y, radius, paint)
        }
    }

    fun reset(): Boolean {
        val window = activity.window
        if (window != null) window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        if (!closed) return false
        closed = false
        return true
    }

    fun interface OnAnimationEnd {
        fun animationEnd()
    }

    fun closeTo(x: Float, y: Float, listener: OnAnimationEnd?) {
        this.x = x
        this.y = y
        val window = activity.window
        if (window != null) window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

        animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = Animation.EXTENDED.getDuration().toLong()
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    listener?.animationEnd()
                }

                override fun onAnimationStart(animation: Animator) {
                    super.onAnimationStart(animation)
                    closed = true
                }
            })
            start()
        }
    }
}
