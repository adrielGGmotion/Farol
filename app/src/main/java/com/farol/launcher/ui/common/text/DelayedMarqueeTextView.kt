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

package com.farol.launcher.ui.common.text

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import com.farol.launcher.ui.Measurements
import com.farol.launcher.ui.utils.UiUtils
import java.util.concurrent.locks.ReentrantLock
import androidx.core.view.isEmpty

open class DelayedMarqueeTextView : AppCompatTextView {
    private var scrollAnimator: ValueAnimator? = null
    private var originalText: CharSequence? = null
    private var isMarqueeNeeded = false
    private var textLock: ReentrantLock? = null
    private var lastWidth = 0

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        textLock = ReentrantLock()
        originalText = ""
        isMarqueeNeeded = false
        lastWidth = -1

        setSingleLine(true)
        setEllipsize(null)
        setHorizontallyScrolling(true)
        setHorizontalScrollBarEnabled(false)
        setHorizontalFadingEdgeEnabled(true)
        setFadingEdgeLength(Constants.FADING_EDGE_LENGTH)
    }

    override fun setSingleLine(singleLine: Boolean) {
        if (!singleLine) {
            return
        }
        super.setSingleLine(true)
    }

    override fun setText(text: CharSequence?, type: TextView.BufferType?) {
        val lock = textLock ?: ReentrantLock().also { textLock = it }
        lock.lock()

        try {
            originalText = text
            lastWidth = -1
            super.setText(text, type)
        } finally {
            lock.unlock()
            setupMarqueeInternal()
        }
    }

    override fun getText(): CharSequence = originalText ?: ""

    private fun setupMarqueeInternal() {
        val width = getWidth()
        if (width <= 0 || width == lastWidth) {
            return
        }

        lastWidth = width
        stopMarqueeInternal()

        val originalText = originalText
        if (originalText == null || originalText.isEmpty()) {
            return
        }

        val originalTextWidth = paint.measureText(originalText.toString())
        val viewWidth = width - paddingLeft - paddingRight
        isMarqueeNeeded = originalTextWidth > viewWidth && UiUtils.areAnimationsOn()
        if (!isMarqueeNeeded) {
            restoreOriginalText()
            return
        }

        val lock = textLock ?: ReentrantLock().also { textLock = it }
        lock.lock()
        try {
            super.setText(
                TextUtils.concat(originalText, Constants.SPACING, originalText),
                TextView.BufferType.NORMAL,
            )
        } finally {
            lock.unlock()
        }

        val scrollDistance = paint.measureText(originalText.toString() + Constants.SPACING).toInt()
        val duration = ((scrollDistance / Constants.SCROLL_SPEED_PIXELS_PER_SECOND) * 1000).toLong()
        val animator = ValueAnimator.ofInt(0, scrollDistance)
        animator.setInterpolator(LinearInterpolator())
        animator.setInterpolator(PathInterpolator(0.15f, 0.01f, 0.85f, 1f))
        animator.setDuration((duration / Measurements.getAnimatorDurationScale()).toLong())
        animator.startDelay = (Constants.MARQUEE_DELAY / Measurements.getAnimatorDurationScale()).toLong()
        animator.addUpdateListener { animation ->
            scrollTo(animation.animatedValue as Int, 0)
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            private var isCancelled = false

            override fun onAnimationCancel(animation: Animator) {
                isCancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                scrollTo(0, 0)

                if (!isCancelled) {
                    postDelayed({
                        val scrollAnimator = scrollAnimator
                        if (!isCancelled && scrollAnimator != null) {
                            scrollAnimator.setIntValues(0, scrollDistance)
                            scrollAnimator.start()
                        }
                    }, (Constants.MARQUEE_DELAY / Measurements.getAnimatorDurationScale()).toLong())
                } else {
                    isCancelled = false
                }
            }
        })

        scrollAnimator = animator
        animator.start()
    }

    private fun restoreOriginalText() {
        val lock = textLock ?: ReentrantLock().also { textLock = it }
        lock.lock()

        try {
            if (!TextUtils.equals(super.getText(), originalText)) {
                super.setText(originalText, TextView.BufferType.NORMAL)
            }
            scrollTo(0, 0)
        } finally {
            lock.unlock()
        }
    }

    open fun stopMarqueeInternal() {
        scrollAnimator?.cancel()
        scrollAnimator = null
        lastWidth = -1
        scrollTo(0, 0)
    }

    protected override fun getLeftFadingEdgeStrength(): Float {
        val textLayout = getLayout()
        if (!isMarqueeNeeded && textLayout != null) {
            return 0f
        }

        if (layoutDirection == View.LAYOUT_DIRECTION_LTR) {
            val distance = scrollX
            return if (distance > 0 && distance <= textLayout!!.getLineRight(0).toInt() / 2) {
                Math.min(distance.toFloat() / Constants.FADING_EDGE_LENGTH, 1f)
            } else {
                0f
            }
        }
        return 1f
    }

    protected override fun getRightFadingEdgeStrength(): Float {
        val textLayout = getLayout()
        if (!isMarqueeNeeded && textLayout != null) {
            return 0f
        }

        if (layoutDirection == View.LAYOUT_DIRECTION_LTR) {
            return 1f
        }

        val distance = scrollX
        return if (distance > 0 && distance <= textLayout!!.getLineRight(0).toInt() / 2) {
            Math.min(distance.toFloat() / Constants.FADING_EDGE_LENGTH, 1f)
        } else {
            0f
        }
    }

    protected override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)

        if (width != oldWidth) {
            setupMarqueeInternal()
        }
    }

    protected override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setupMarqueeInternal()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)

        if (hasWindowFocus) {
            setupMarqueeInternal()
        } else {
            stopMarqueeInternal()
        }
    }

    private object Constants {
        const val SCROLL_SPEED_PIXELS_PER_SECOND = 100f
        const val MARQUEE_DELAY = 2000L
        const val FADING_EDGE_LENGTH = 20
        const val SPACING = "        "
    }
}
