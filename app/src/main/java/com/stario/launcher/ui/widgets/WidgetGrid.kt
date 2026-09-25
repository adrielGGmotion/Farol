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

package com.stario.launcher.ui.widgets

import android.animation.AnimatorSet
import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import com.stario.launcher.sheet.widgets.Widget
import com.stario.launcher.ui.Measurements
import com.stario.launcher.ui.utils.LayoutSizeObserver
import com.stario.launcher.ui.utils.animation.Animation
import com.stario.launcher.utils.objects.ObservableObject
import java.util.PriorityQueue
import androidx.core.view.isEmpty
import androidx.core.view.size

open class WidgetGrid : GridLayout {
    private var onDispatchDrawReorderRunnable: Runnable? = null
    private lateinit var columnCount: ObservableObject<Int>
    private lateinit var map: WidgetMap

    constructor(context: Context) : super(context) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initialize()
    }

    private fun initialize() {
        map = WidgetMap(2)
        onDispatchDrawReorderRunnable = null
        columnCount = ObservableObject(0) { value ->
            val parentView = parent as View?
            val originalAlpha = parentView?.alpha ?: 1f
            if (parentView != null) {
                parentView.alpha = 0f
            }

            map.setColumnCount(value!!)

            val transition = layoutTransition
            layoutTransition = null

            scheduleReorder()

            if (parentView != null && originalAlpha > 0f) {
                post {
                    parentView.animate().alpha(originalAlpha)
                        .setDuration(Animation.MEDIUM.getDuration().toLong())
                }
            }

            layoutTransition = transition
        }

        setRotation(180f)

        val set = AnimatorSet()
        set.playTogether(
            ObjectAnimator.ofFloat(null, "alpha", 0f, 1f),
            ObjectAnimator.ofFloat(null, "scaleX", 0.8f, 1f),
            ObjectAnimator.ofFloat(null, "scaleY", 0.8f, 1f)
        )

        val layoutTransition = LayoutTransition()
        layoutTransition.setAnimator(LayoutTransition.APPEARING, set)
        layoutTransition.setDuration(LayoutTransition.APPEARING, Animation.MEDIUM.getDuration().toLong())
        layoutTransition.setDuration(LayoutTransition.CHANGE_APPEARING, Animation.MEDIUM.getDuration().toLong())
        layoutTransition.setDuration(LayoutTransition.CHANGE_DISAPPEARING, Animation.MEDIUM.getDuration().toLong())
        layoutTransition.setDuration(LayoutTransition.DISAPPEARING, Animation.MEDIUM.getDuration().toLong())
        layoutTransition.setStartDelay(LayoutTransition.APPEARING, 0L)
        layoutTransition.disableTransitionType(LayoutTransition.CHANGING)

        setLayoutTransition(layoutTransition)

        LayoutSizeObserver.attach(this, LayoutSizeObserver.WIDTH, object : LayoutSizeObserver.OnChange {
            override fun onChange(view: View, watchFlags: Int, rect: Rect) {
                columnCount.updateObject(maxOf(2, rect.width() / Measurements.dpToPx(160f) / 2 * 2))
            }
        })
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        if (child is WidgetContainer) {
            super.addView(child, index, params)
        } else {
            throw RuntimeException("WidgetGrid can only have WidgetContainer children.")
        }
    }

    override fun removeView(view: View) {
        super.removeView(view)
        scheduleReorder()
    }

    private fun scheduleReorder() {
        if (onDispatchDrawReorderRunnable != null) {
            return
        }

        onDispatchDrawReorderRunnable = Runnable {
            onDispatchDrawReorderRunnable = null
            reorder()
        }
    }

    protected override fun dispatchDraw(canvas: Canvas) {
        val reorderRunnable = onDispatchDrawReorderRunnable
        if (reorderRunnable != null) {
            reorderRunnable.run()
            invalidate()
        } else {
            super.dispatchDraw(canvas)
        }
    }

    internal open fun getCellSize(): Int = measuredWidth / columnCount.getObject()!!

    open fun attach(host: AppWidgetHostView, widget: Widget) {
        val cell = map.getAvailableOrigin(widget.size)

        super.addView(WidgetContainer(context, host, widget, cell))
        map.add(cell, widget.size)
    }

    open fun reorder() {
        map.clear()

        val containers = PriorityQueue<WidgetContainer>()

        for (index in 0 until childCount) {
            containers.add(getChildAt(index) as WidgetContainer)
        }

        while (!containers.isEmpty()) {
            val container: WidgetContainer? = containers.poll()

            if (container != null) {
                val cell = map.getAvailableOrigin(container.getSize())

                container.updateOrigin(cell)
                map.add(cell, container.getSize())
            }
        }
    }

    open fun allocatePosition(): Int {
        var max = -1

        for (index in 0 until childCount) {
            val position = (getChildAt(index) as WidgetContainer).getPosition()
            if (position > max) {
                max = position
            }
        }

        return max + 1
    }

    open fun computeCellSize(): Int = width / columnCount.getObject()!!

    private companion object {
        const val TAG = "com.stario.WidgetGrid"
    }
}
