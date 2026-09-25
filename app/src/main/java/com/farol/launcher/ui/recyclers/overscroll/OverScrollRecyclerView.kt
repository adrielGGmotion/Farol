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

package com.farol.launcher.ui.recyclers.overscroll

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.EdgeEffect
import androidx.recyclerview.widget.RecyclerView
import java.util.ArrayList
import androidx.core.view.isNotEmpty

open class OverScrollRecyclerView : RecyclerView, OverScroll {
    private val overScrollListeners = ArrayList<OverScrollEffect.OnOverScrollListener>()
    private val edgeEffects = ArrayList<OverScrollEffect<OverScrollRecyclerView>>()
    private val scrollListeners = ArrayList<OnScrollListener>()
    private val contracts = ArrayList<OverScroll.OverScrollContract>()
    private var overScrollOwner: OverScrollEffect<*>? = null
    private var layoutPending = false
    private var touching = false
    @field:OverScrollEffect.Edge
    private var pullEdges = OverScrollEffect.PULL_EDGE_BOTTOM or OverScrollEffect.PULL_EDGE_TOP

    constructor(context: Context) : super(context) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        initialize()
    }

    private fun initialize() {
        super.setEdgeEffectFactory(object : EdgeEffectFactory() {
            protected override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
                if (view is OverScroll) {
                    val effect = when (direction) {
                        RecyclerView.EdgeEffectFactory.DIRECTION_TOP -> OverScrollEffect(
                            this@OverScrollRecyclerView,
                            pullEdges,
                            OverScrollEffect.PIVOT_TOP
                        )

                        RecyclerView.EdgeEffectFactory.DIRECTION_BOTTOM -> OverScrollEffect(
                            this@OverScrollRecyclerView,
                            pullEdges,
                            OverScrollEffect.PIVOT_BOTTOM
                        )

                        else -> OverScrollEffect(this@OverScrollRecyclerView, pullEdges)
                    }

                    for (listener in overScrollListeners) {
                        effect.addOnOverScrollListener(listener)
                    }

                    edgeEffects.add(effect)
                    return effect
                }

                return EdgeEffect(view.getContext())
            }
        })

        addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (layoutPending) {
                val sizeChanged = bottom - top != oldBottom - oldTop || right - left != oldRight - oldLeft
                if (sizeChanged) {
                    layoutPending = false
                    if (touching) {
                        stopNestedScrollInternal()
                    }
                }
            }
        }
    }

    private fun stopNestedScrollInternal() {
        super.stopNestedScroll()
    }

    @SuppressLint("ClickableViewAccessibility")
    open override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.action
        touching = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
        return super.onTouchEvent(event)
    }

    /*
     * If the async adapter updates and triggers a layout pass,
     * RecyclerView usually calls stopNestedScroll(). This kills the drag gesture,
     * making the BottomSheet seem "not responsive".
     *
     * A workaround for THIS specific scenario:
     * - Block stopNestedScroll() if a layout is pending.
     * - Use an OnLayoutChangeListener to detect when the dimensions actually change.
     * - Release the scroll lock only after the layout is stable and the user lets go.
     *
     * This is HIGHLY EXPERIMENTAL AND MAY BREAK, revert this if issues occur.
     * Consecutive stopNestedScroll() without calling startNestedScroll() beforehand
     * **SHOULD** be fine (famous last words)
     */
    open override fun stopNestedScroll() {
        if (isLayoutRequested()) {
            layoutPending = true
            return
        }

        super.stopNestedScroll()
    }

    open override fun tryCaptureOverScroll(effect: OverScrollEffect<*>): Boolean {
        if (overScrollOwner == null || overScrollOwner == effect) {
            overScrollOwner = effect
            return true
        }

        return false
    }

    open override fun releaseOverScroll(effect: OverScrollEffect<*>) {
        if (overScrollOwner != null && overScrollOwner == effect) {
            overScrollOwner = null
        }
    }

    open fun addOnOverScrollListener(listener: OverScrollEffect.OnOverScrollListener?) {
        if (listener != null) {
            overScrollListeners.add(listener)
            for (effect in edgeEffects) {
                effect.addOnOverScrollListener(listener)
            }
        }
    }

    open fun removeOnOverScrollListener(listener: OverScrollEffect.OnOverScrollListener?) {
        if (listener != null) {
            overScrollListeners.remove(listener)
            for (effect in edgeEffects) {
                effect.removeOnOverScrollListener(listener)
            }
        }
    }

    open fun setOverscrollPullEdges(@OverScrollEffect.Edge edges: Int) {
        pullEdges = edges
        for (effect in edgeEffects) {
            effect.setPullEdges(edges)
        }
    }

    open fun getOverscrollPullEdges(): Int = pullEdges

    open override fun canScrollHorizontally(direction: Int): Boolean = false

    open override fun addOnScrollListener(listener: OnScrollListener) {
        scrollListeners.add(listener)
        super.addOnScrollListener(listener)
    }

    open override fun removeOnScrollListener(listener: OnScrollListener) {
        scrollListeners.remove(listener)
        super.removeOnScrollListener(listener)
    }

    open override fun scrollToPosition(position: Int) {
        for (listener in scrollListeners) {
            listener.onScrolled(this, 0, 0)
        }

        super.scrollToPosition(position)
    }

    open override fun addOverScrollContract(contract: OverScroll.OverScrollContract) {
        contracts.add(contract)
    }

    open override fun setEdgeEffectFactory(edgeEffectFactory: EdgeEffectFactory) {
    }

    open override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        for (effect in edgeEffects) {
            effect.onTouchEvent(event)
        }

        return super.dispatchTouchEvent(event)
    }

    protected open override fun dispatchDraw(canvas: Canvas) {
        if (contracts.isNotEmpty()) {
            for (contract in contracts) {
                if (contract.prepare(canvas)) {
                    break
                }
            }
        }

        super.dispatchDraw(canvas)
    }
}
