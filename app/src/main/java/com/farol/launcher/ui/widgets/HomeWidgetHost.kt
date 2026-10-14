package com.farol.launcher.ui.widgets

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.SizeF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.constraintlayout.widget.ConstraintLayout
import com.farol.launcher.ui.common.grid.DynamicGridLayout
import kotlin.math.hypot

open class HomeWidgetHost(
    context: Context,
    hostId: Int,
    private val manager: AppWidgetManager,
    private val removeListener: (Int) -> Unit
) : WidgetHost(context, hostId) {
    override fun onAppWidgetRemoved(identifier: Int) {
        removeListener(identifier)
        super.onAppWidgetRemoved(identifier)
    }

    protected override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        val params = RelativeLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )
        return HomeWidgetHostView(context, params, manager, appWidgetId)
    }
}

@SuppressLint("ViewConstructor")
class HomeWidgetHostView(
    context: Context,
    params: RelativeLayout.LayoutParams,
    private val manager: AppWidgetManager,
    private val identifier: Int
) : WidgetHostView(context, params) {
    /** Called once the finger moves after a hold, i.e. the held widget is being dragged. */
    var onHoldDrag: (() -> Unit)? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var lastWidth = -1
    private var lastHeight = -1
    private var longPressed = false
    private var dragging = false
    private var lastX = 0f
    private var lastY = 0f
    private var holdX = 0f
    private var holdY = 0f

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        lastX = event.rawX
        lastY = event.rawY
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            longPressed = false
            dragging = false
            var ancestor = parent
            while (ancestor is View) {
                ancestor.cancelLongPress()
                ancestor = ancestor.parent
            }
            // Launcher swipes (sheets, dock) must start from on top of a widget too, so only content that can
            // actually scroll keeps the gesture to itself.
            if (canScrollAt(this, event.x, event.y)) parent?.requestDisallowInterceptTouchEvent(true)
        }
        val handled = if (longPressed) {
            val cancel = MotionEvent.obtain(event)
            cancel.action = MotionEvent.ACTION_CANCEL
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
            if (!dragging && event.actionMasked == MotionEvent.ACTION_MOVE &&
                hypot(lastX - holdX, lastY - holdY) > slop
            ) {
                dragging = true
                onHoldDrag?.invoke()
            }
            feedGrid(event.actionMasked)
            true
        } else {
            super.dispatchTouchEvent(event)
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return handled
    }

    override fun performLongClick(): Boolean {
        val handled = super.performLongClick()
        if (handled) {
            longPressed = true
            parent?.requestDisallowInterceptTouchEvent(true)
            holdX = lastX
            holdY = lastY
            // The held widget is picked up where the finger is; the rest of the gesture keeps flowing through here.
            feedGrid(MotionEvent.ACTION_DOWN)
        }
        return handled
    }

    private fun canScrollAt(view: View, x: Float, y: Float): Boolean {
        if (view.canScrollVertically(-1) || view.canScrollVertically(1) ||
            view.canScrollHorizontally(-1) || view.canScrollHorizontally(1)
        ) return true
        if (view !is ViewGroup) return false
        for (index in view.childCount - 1 downTo 0) {
            val child = view.getChildAt(index)
            val childX = x + view.scrollX - child.left
            val childY = y + view.scrollY - child.top
            if (child.isShown && childX >= 0 && childY >= 0 && childX < child.width && childY < child.height &&
                canScrollAt(child, childX, childY)
            ) return true
        }
        return false
    }

    // The grid only sees gestures that start on itself, so replay this one into it in the grid's own coordinates.
    private fun feedGrid(action: Int) {
        val grid = parent?.parent as? DynamicGridLayout ?: return
        val origin = IntArray(2)
        grid.getLocationOnScreen(origin)
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, lastX - origin[0], lastY - origin[1], 0)
        grid.onHeldTouchEvent(event)
        event.recycle()
    }

    private val reportSize = Runnable { updateWidgetSize(measuredWidth, measuredHeight) }

    protected override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // The first size goes out at once. While a resize eases through many sizes, only where it settles is
        // reported, so the provider isn't asked to redraw on every frame.
        removeCallbacks(reportSize)
        if (lastWidth < 0) updateWidgetSize(measuredWidth, measuredHeight)
        else if (measuredWidth != lastWidth || measuredHeight != lastHeight) postDelayed(reportSize, SIZE_REPORT_DELAY_MS)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(reportSize)
        super.onDetachedFromWindow()
    }

    private fun updateWidgetSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0 || width == lastWidth && height == lastHeight) return
        lastWidth = width
        lastHeight = height
        val density = resources.displayMetrics.density
        val widthDp = maxOf(1, (width / density).toInt())
        val heightDp = maxOf(1, (height / density).toInt())
        try {
            val options = manager.getAppWidgetOptions(identifier) ?: Bundle()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                updateAppWidgetSize(options, listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
            } else {
                @Suppress("DEPRECATION")
                updateAppWidgetSize(options, widthDp, heightDp, widthDp, heightDp)
            }
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Could not update widget size for $identifier", exception)
        }
    }

    override fun getDescendantFocusability(): Int = ViewGroup.FOCUS_AFTER_DESCENDANTS

    companion object {
        private const val TAG = "HomeWidgetHostView"
        private const val SIZE_REPORT_DELAY_MS = 120L
    }
}
