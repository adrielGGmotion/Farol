package com.stario.launcher.ui.common.text

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.Gravity
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatTextView
import com.stario.launcher.R
import androidx.core.view.isEmpty

open class WaveUnderlineTextView : AppCompatTextView {
    private var pulsationAnimator: ValueAnimator? = null
    private var currentSpeedMultiplier = Constants.PULSATE_SPEED_MIN_FACTOR
    private var waveAnimator: ValueAnimator? = null
    private var lastWaveUpdate = 0L
    private var animationOffset = 0f
    private var waveColorValue = 0
    private lateinit var paint: Paint
    private lateinit var path: Path

    constructor(context: Context) : super(context) {
        init(null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(attrs)
    }

    private fun init(attrs: AttributeSet?) {
        path = Path()
        animationOffset = 0f
        currentSpeedMultiplier = Constants.PULSATE_SPEED_MIN_FACTOR
        paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = Constants.WAVE_STROKE_LENGTH_PIXELS.toFloat()
        paint.pathEffect = null

        waveColorValue = Color.WHITE
        if (attrs != null) {
            //noinspection resource
            val attributes: TypedArray = context.obtainStyledAttributes(attrs, R.styleable.WaveUnderlineTextView)
            try {
                waveColorValue = attributes.getColor(
                    R.styleable.WaveUnderlineTextView_waveColor,
                    Color.WHITE,
                )
            } finally {
                attributes.recycle()
            }
        }

        paint.color = Color.argb(
            Constants.PULSATE_ALPHA_MIN,
            Color.red(waveColorValue),
            Color.green(waveColorValue),
            Color.blue(waveColorValue),
        )
    }

    protected override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val textPaint = getPaint()
        val text = getText().toString()
        if (text.isEmpty()) {
            return
        }

        val textWidth = textPaint.measureText(text)
        val viewWidth = width.toFloat()
        val gravity = getGravity()
        val xStart = when {
            (gravity and Gravity.LEFT) == Gravity.LEFT || (gravity and Gravity.START) == Gravity.START -> {
                paddingLeft.toFloat()
            }

            (gravity and Gravity.RIGHT) == Gravity.RIGHT || (gravity and Gravity.END) == Gravity.END -> {
                viewWidth - paddingRight - textWidth
            }

            (gravity and Gravity.CENTER_HORIZONTAL) == Gravity.CENTER_HORIZONTAL -> {
                (viewWidth - textWidth) / 2f
            }

            else -> paddingLeft.toFloat()
        }
        val xEnd = xStart + textWidth
        val y = height - paddingBottom + Constants.BOTTOM_OFFSET

        path.reset()

        if (xStart >= xEnd) { // still, draw something
            val currentWaveX = xStart - animationOffset
            val angle = currentWaveX / Constants.WAVE_LENGTH_PIXELS * (2 * Math.PI).toFloat()
            val yOffset = Math.sin(angle.toDouble()).toFloat() * Constants.WAVE_AMPLITUDE_PIXELS

            path.moveTo(xStart, y + yOffset)
            path.lineTo(xStart + 1, y + yOffset)
        } else { // draw every sine point
            var currentXDraw = xStart
            while (currentXDraw <= xEnd) {
                val currentWaveX = currentXDraw - animationOffset
                val angle = currentWaveX / Constants.WAVE_LENGTH_PIXELS * (2 * Math.PI).toFloat()
                val yOffset = Math.sin(angle.toDouble()).toFloat() * Constants.WAVE_AMPLITUDE_PIXELS

                if (currentXDraw == xStart) {
                    path.moveTo(currentXDraw, y + yOffset)
                } else {
                    path.lineTo(currentXDraw, y + yOffset)
                }
                currentXDraw += 1f
            }
        }
        canvas.drawPath(path, paint)
    }

    protected override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        lastWaveUpdate = 0

        // Wave Animator
        val waveAnimator = ValueAnimator.ofInt(0, 1)
        this.waveAnimator = waveAnimator
        waveAnimator.setDuration(1000L)
        waveAnimator.repeatCount = ValueAnimator.INFINITE
        waveAnimator.setInterpolator(LinearInterpolator())
        waveAnimator.addUpdateListener {
            val currentTimeNanos = System.nanoTime()
            if (lastWaveUpdate == 0L) {
                lastWaveUpdate = currentTimeNanos
                invalidate()
                return@addUpdateListener
            }

            var deltaTimeSeconds = (currentTimeNanos - lastWaveUpdate) / 1_000_000_000.0f
            lastWaveUpdate = currentTimeNanos

            // cap deltaTime
            if (deltaTimeSeconds > 0.1f) {
                deltaTimeSeconds = 0.1f
            }

            val actualSpeed = Constants.WAVE_SPEED_PIXELS_PER_SECOND * currentSpeedMultiplier
            animationOffset += actualSpeed * deltaTimeSeconds

            if (Constants.WAVE_LENGTH_PIXELS > 0) {
                animationOffset %= Constants.WAVE_LENGTH_PIXELS.toFloat()
            }
            invalidate()
        }
        waveAnimator.start()

        // Pulsation Animator
        val pulsationAnimator = ValueAnimator.ofFloat(0f, 1f)
        this.pulsationAnimator = pulsationAnimator
        pulsationAnimator.setDuration(Constants.PULSATE_DURATION_MS)
        pulsationAnimator.repeatCount = ValueAnimator.INFINITE
        pulsationAnimator.repeatMode = ValueAnimator.REVERSE
        pulsationAnimator.setInterpolator(AccelerateDecelerateInterpolator())
        pulsationAnimator.addUpdateListener { animation ->
            val interpolatedFraction = animation.animatedValue as Float

            paint.color = Color.argb(
                (Constants.PULSATE_ALPHA_MIN + interpolatedFraction * (Constants.PULSATE_ALPHA_MAX - Constants.PULSATE_ALPHA_MIN)).toInt(),
                Color.red(waveColorValue),
                Color.green(waveColorValue),
                Color.blue(waveColorValue),
            )

            //noinspection PointlessArithmeticExpression
            currentSpeedMultiplier = Constants.PULSATE_SPEED_MIN_FACTOR +
                interpolatedFraction * (Constants.PULSATE_SPEED_MAX_FACTOR - Constants.PULSATE_SPEED_MIN_FACTOR)
        }
        pulsationAnimator.start()
    }

    protected override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        waveAnimator?.cancel()
        waveAnimator = null
        pulsationAnimator?.cancel()
        pulsationAnimator = null
        lastWaveUpdate = 0
    }

    open fun setWaveColor(newColor: Int) {
        waveColorValue = newColor
        val animator = pulsationAnimator
        val currentAlphaValue = if (animator != null && animator.isRunning) {
            val interpolatedFraction = animator.animatedValue as Float
            (Constants.PULSATE_ALPHA_MIN + interpolatedFraction * (Constants.PULSATE_ALPHA_MAX - Constants.PULSATE_ALPHA_MIN)).toInt()
        } else {
            Constants.PULSATE_ALPHA_MIN
        }

        paint.color = Color.argb(
            currentAlphaValue,
            Color.red(waveColorValue),
            Color.green(waveColorValue),
            Color.blue(waveColorValue),
        )
        invalidate()
    }

    private object Constants {
        // Wave movement
        const val WAVE_SPEED_PIXELS_PER_SECOND = 30
        const val WAVE_STROKE_LENGTH_PIXELS = 6
        const val WAVE_AMPLITUDE_PIXELS = 8
        const val WAVE_LENGTH_PIXELS = 80
        const val BOTTOM_OFFSET = 10

        // Pulsation parameters
        const val PULSATE_SPEED_MIN_FACTOR = 0.5f
        const val PULSATE_SPEED_MAX_FACTOR = 1.5f
        const val PULSATE_DURATION_MS = 2000L
        const val PULSATE_ALPHA_MAX = 180
        const val PULSATE_ALPHA_MIN = 80
    }
}
