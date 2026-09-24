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

package com.stario.launcher.ui.recyclers.overscroll

import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.EdgeEffect
import androidx.annotation.IntDef
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.stario.launcher.ui.Measurements
import com.stario.launcher.ui.utils.UiUtils
import java.util.ArrayList

open class OverScrollEffect<V>(
    private val view: V,
    @field:Edge private var edges: Int,
    private var pivot: Byte
) : EdgeEffect(view.getContext()) where V : View, V : OverScroll {
    constructor(view: V, edges: Int) : this(view, edges, PIVOT_UNSPECIFIED)

    private val overScrollListeners = ArrayList<OnOverScrollListener>()
    private var factor = 0f
        set(value) {
            field = value
            notifyOverScrolled(value)
            view.invalidate()
        }
    private val animation: SpringAnimation
    private val maxFlingVelocity: Float
    private val touchSlop: Float
    private val bounds = Rect()
    private var receivedMoveEvent = false
    private var isCanvasCaptured = false
    private var oldState: OverScrollState? = null
    private var state = OverScrollState.IDLE
    private var initialTouchY = -1f

    init {
        val configuration = ViewConfiguration.get(view.getContext())
        touchSlop = configuration.getScaledTouchSlop().toFloat()
        maxFlingVelocity = configuration.getScaledMaximumFlingVelocity().toFloat()

        val spring = SpringForce()
        spring.setFinalPosition(0f)
        spring.setStiffness(SPRING_STIFFNESS)
        spring.setDampingRatio(SPRING_DAMPING_RATIO)

        // Animation behaves better with bigger numbers, so artificially increase the
        // factor by an arbitrary value when animating
        animation = SpringAnimation(Any(), object : FloatPropertyCompat<Any>("") {
            override fun getValue(`object`: Any): Float {
                return factor * SPRING_FACTOR_MULTIPLIER
            }

            override fun setValue(`object`: Any, value: Float) {
                this@OverScrollEffect.factor = value / SPRING_FACTOR_MULTIPLIER
            }
        })
        animation.setSpring(spring)
        animation.addEndListener { _, canceled, _, _ ->
            if (!canceled) {
                view.postOnAnimation { finish() }
            }
        }

        view.addOverScrollContract { canvas ->
            if (state == OverScrollState.IDLE) {
                isCanvasCaptured = false
                return@addOverScrollContract false
            }

            if (!view.tryCaptureOverScroll(this)) {
                isCanvasCaptured = false
                return@addOverScrollContract false
            }

            if (pivot == PIVOT_UNSPECIFIED) {
                if (!view.canScrollVertically(1)) {
                    if (view.canScrollVertically(-1)) {
                        pivot = PIVOT_BOTTOM
                    }
                } else if (!view.canScrollVertically(-1)) {
                    pivot = PIVOT_TOP
                }
            }

            val maxTranslation = minOf(
                (canvas.getHeight() * TRANSLATE_MULTIPLIER).toInt(),
                Measurements.dpToPx(MAX_TRANSLATION_DP)
            )
            val factorValue = minOf(1f, factor)
            val easedFactor = 1f - Math.pow((1f - factorValue).toDouble(), 3.0).toFloat()
            val translationY = easedFactor * maxTranslation
            val scaleY = 1f + Math.pow(factorValue.toDouble(), 0.8).toFloat() * SCALE_MULTIPLIER

            if (pivot == PIVOT_BOTTOM) {
                canvas.translate(0f, -translationY)
                canvas.scale(1f, scaleY, bounds.centerX().toFloat(), bounds.height().toFloat())
            } else if (pivot == PIVOT_TOP) {
                canvas.translate(0f, translationY)
                canvas.scale(1f, scaleY, bounds.centerX().toFloat(), 0f)
            } else {
                isCanvasCaptured = false
                return@addOverScrollContract false
            }

            isCanvasCaptured = true
            true
        }
    }

    open fun setPullEdges(@Edge edges: Int) {
        this.edges = edges
        factor = 0f
    }

    open override fun setSize(width: Int, height: Int) {
        super.setSize(width, height)
        bounds.set(0, 0, width, height)
        factor = 0f
    }

    open override fun isFinished(): Boolean = state == OverScrollState.IDLE

    open override fun finish() {
        if (animation.isRunning()) {
            animation.cancel()
        }

        view.releaseOverScroll(this)
        state = OverScrollState.IDLE
        notifyStateChange(state)
        super.finish()
    }

    open override fun getDistance(): Float {
        if (!receivedMoveEvent || !isPullAllowed()) {
            return 0f
        }

        return factor
    }

    /**
     * @noinspection BooleanMethodIsAlwaysInverted
     */
    private fun isPullAllowed(): Boolean {
        return when (pivot) {
            PIVOT_TOP -> (edges and PULL_EDGE_TOP) == PULL_EDGE_TOP
            PIVOT_BOTTOM -> (edges and PULL_EDGE_BOTTOM) == PULL_EDGE_BOTTOM
            else -> false
        }
    }

    open override fun onPullDistance(deltaDistance: Float, displacement: Float): Float {
        if (!receivedMoveEvent || !isPullAllowed() || !UiUtils.areAnimationsOn()) {
            return 0f
        }

        if (deltaDistance >= 0f &&
            ((pivot == PIVOT_TOP && (edges and PULL_EDGE_TOP) != PULL_EDGE_TOP) ||
                (pivot == PIVOT_BOTTOM && (edges and PULL_EDGE_BOTTOM) != PULL_EDGE_BOTTOM))
        ) {
            return deltaDistance
        }

        val newFactor = deltaDistance + factor
        val delta = maxOf(0f, newFactor) - factor
        onPull(delta)

        if (newFactor <= 0f) {
            finish()
        }

        return delta
    }

    open override fun onPull(deltaDistance: Float) {
        if (!receivedMoveEvent || !isPullAllowed()) {
            return
        }

        if (state == OverScrollState.IDLE && !view.tryCaptureOverScroll(this)) {
            return
        }

        state = OverScrollState.OVER_SCROLLING
        notifyStateChange(state)

        if (animation.isRunning()) {
            animation.cancel()
        }

        factor += deltaDistance
    }

    open override fun onRelease() {
        if (!animation.isRunning()) {
            if (factor == 0f) {
                finish()
            } else {
                state = OverScrollState.SETTLING
                notifyStateChange(state)
                animation.setStartVelocity(0f)
                    .setStartValue(factor * SPRING_FACTOR_MULTIPLIER)
                    .start()
            }
        }
    }

    open override fun onAbsorb(velocity: Int) {
        if (!UiUtils.areAnimationsOn()) {
            return
        }

        if (state == OverScrollState.IDLE && !view.tryCaptureOverScroll(this)) {
            return
        }

        state = OverScrollState.SETTLING
        notifyStateChange(state)

        if (animation.isRunning()) {
            animation.cancel()
        }

        val fraction = velocity / maxFlingVelocity
        animation.setStartVelocity(
            if (factor > 0f) {
                velocity * VELOCITY_MULTIPLIER
            } else {
                velocity * VELOCITY_MULTIPLIER_FLING * (1f - fraction)
            }
        ).setStartValue(
            if (factor > 0f) {
                factor * SPRING_FACTOR_MULTIPLIER
            } else {
                Math.pow(fraction.toDouble(), 1.7).toFloat() * SPRING_FACTOR_MULTIPLIER / 2f
            }
        ).start()
    }

    open override fun draw(canvas: Canvas): Boolean = isCanvasCaptured && !isFinished()

    private fun notifyStateChange(state: OverScrollState) {
        if (oldState == state || !isPullAllowed()) {
            return
        }

        if (pivot == PIVOT_TOP && (edges and PULL_EDGE_TOP) == PULL_EDGE_TOP) {
            for (listener in overScrollListeners) {
                listener.onOverScrollStateChanged(PULL_EDGE_TOP, state)
            }
        } else if (pivot == PIVOT_BOTTOM && (edges and PULL_EDGE_BOTTOM) == PULL_EDGE_BOTTOM) {
            for (listener in overScrollListeners) {
                listener.onOverScrollStateChanged(PULL_EDGE_BOTTOM, state)
            }
        }

        oldState = state
    }

    private fun notifyOverScrolled(factor: Float) {
        if (pivot == PIVOT_UNSPECIFIED) {
            return
        }

        val edge = if (pivot == PIVOT_TOP) PULL_EDGE_TOP else PULL_EDGE_BOTTOM
        for (listener in overScrollListeners) {
            listener.onOverScrolled(edge, factor * factor)
        }
    }

    open fun addOnOverScrollListener(listener: OnOverScrollListener?) {
        if (listener != null) {
            overScrollListeners.add(listener)
        }
    }

    open fun removeOnOverScrollListener(listener: OnOverScrollListener?) {
        if (listener != null) {
            overScrollListeners.remove(listener)
        }
    }

    internal fun onTouchEvent(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> initialTouchY = event.rawY

            MotionEvent.ACTION_MOVE -> {
                receivedMoveEvent = true
                if (pivot == PIVOT_TOP || pivot == PIVOT_BOTTOM) {
                    return
                }

                if (initialTouchY == -1f) {
                    initialTouchY = event.rawY
                    return
                }

                val dy = event.rawY - initialTouchY
                if (Math.abs(dy) >= touchSlop && !view.canScrollVertically(if (dy > 0f) 1 else -1)) {
                    initialTouchY = -1f
                    pivot = if (dy > 0f) PIVOT_TOP else PIVOT_BOTTOM
                }
            }

            MotionEvent.ACTION_UP -> {
                receivedMoveEvent = false
                initialTouchY = -1f
            }

            MotionEvent.ACTION_CANCEL -> {
                view.releaseOverScroll(this)
                receivedMoveEvent = false
                initialTouchY = -1f
            }
        }
    }

    enum class OverScrollState {
        IDLE,
        OVER_SCROLLING,
        SETTLING
    }

    interface OnOverScrollListener {
        fun onOverScrollStateChanged(@Edge edge: Int, state: OverScrollState) {
        }

        fun onOverScrolled(@Edge edge: Int, factor: Float) {
        }
    }

    @IntDef(flag = true, value = [PULL_EDGE_TOP, PULL_EDGE_BOTTOM])
    @Retention(AnnotationRetention.SOURCE)
    annotation class Edge

    companion object {
        const val PULL_EDGE_TOP = 0b01
        const val PULL_EDGE_BOTTOM = 0b10
        private const val PIVOT_UNSPECIFIED: Byte = 0b00
        internal const val PIVOT_TOP: Byte = 0b10
        internal const val PIVOT_BOTTOM: Byte = 0b01
        private const val VELOCITY_MULTIPLIER = 3f
        private const val VELOCITY_MULTIPLIER_FLING = 1.2f
        private const val SPRING_FACTOR_MULTIPLIER = 1000f
        private const val SPRING_STIFFNESS = 300f
        private const val SPRING_DAMPING_RATIO = 1f
        private const val SCALE_MULTIPLIER = 0.05f
        private const val TRANSLATE_MULTIPLIER = 0.1f
        private const val MAX_TRANSLATION_DP = 100f
    }
}
