package com.farol.launcher.activities.settings

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.graphics.withRotation
import com.farol.launcher.R
import com.google.android.material.color.MaterialColors

class FarolBeaconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageButton(context, attrs, 0) {
    private val density = resources.displayMetrics.density
    private val ink = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val beam = Path()
    private val mark = AppCompatResources.getDrawable(context, R.drawable.ic_farol)!!.mutate().apply {
        setTint(MaterialColors.getColor(this@FarolBeaconView, com.google.android.material.R.attr.colorOnPrimary))
    }
    private var progress = 1f
    private var animator: ValueAnimator? = null

    fun illuminate(animate: Boolean) {
        settle()
        if (!animate || !isShown || !ValueAnimator.areAnimatorsEnabled()) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100L
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun settle() {
        animator?.cancel()
        animator = null
        progress = 1f
        invalidate()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (!isVisible) settle()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) settle()
    }

    override fun onDetachedFromWindow() {
        settle()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = minOf(48f * density, height * .28f, width * .23f)
        val x = width * .34f
        val y = height * .5f
        paint.color = ink
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density
        paint.alpha = 42
        canvas.drawCircle(x, y, radius * 1.25f, paint)
        canvas.drawCircle(x, y, radius * 1.5f, paint)
        canvas.drawCircle(x, y, radius * 1.75f, paint)

        paint.style = Paint.Style.FILL
        canvas.withRotation(-18f * (1f - progress), x, y) {
            for (index in 0..2) {
                val top = y + (index - 1.5f) * height * .46f
                beam.rewind()
                beam.moveTo(x, y)
                beam.lineTo(width.toFloat(), top)
                beam.lineTo(width.toFloat(), top + height * .28f)
                beam.close()
                paint.alpha = ((16 + index * 9) * (.35f + .65f * progress)).toInt()
                drawPath(beam, paint)
            }
        }

        paint.alpha = 255
        canvas.drawCircle(x, y, radius, paint)
        val size = radius * 1.2f
        mark.setBounds((x - size).toInt(), (y - size).toInt(), (x + size).toInt(), (y + size).toInt())
        mark.draw(canvas)
        if (isFocused) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * density
            canvas.drawCircle(x, y, radius + 6f * density, paint)
        }
    }
}
