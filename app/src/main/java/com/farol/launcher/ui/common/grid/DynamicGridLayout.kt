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

package com.farol.launcher.ui.common.grid

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import com.farol.launcher.R
import com.farol.launcher.Farol
import com.farol.launcher.ui.Measurements
import com.farol.launcher.ui.utils.animation.Animation
import java.util.LinkedList
import java.util.Queue
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

open class DynamicGridLayout(context: Context, attrs: AttributeSet?) : ViewGroup(context, attrs) {
    private val templateManager: GridTemplateManager
    private val preAnimVisualPos: MutableMap<View, Point> = HashMap()
    private val hintedViews: MutableList<View> = ArrayList()
    private var isRearrangeable = false
    private var cellWidth = 0
    private var cellHeight = 0
    private var colCount = MIN_COLS_PORTRAIT
    private var rowCount = MIN_ROWS_PORTRAIT
    private var warningMessage: String
    private var activeItem: DraggableGridItem? = null
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isResizing = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private lateinit var originalParams: LayoutParams
    private var reorderRunnable: Runnable? = null
    private var currentHoverTarget: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pendingTargetCol = 0
    private var pendingTargetRow = 0
    private var lastMeasuredCols = -1
    private var lastMeasuredRows = -1
    private var lastReorderCol = -1
    private var lastReorderRow = -1
    private var runningAnimations = 0
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    // While a resize steps between cell sizes, the item eases from its drawn size to the new one.
    private var sizeAnimator: ValueAnimator? = null
    private var sizeItem: DraggableGridItem? = null
    private var sizeFromW = 0f
    private var sizeFromH = 0f
    private var sizeToW = 0f
    private var sizeToH = 0f
    private var sizeFraction = 1f

    /** Called when a tap lands on empty space while rearranging, i.e. the user is done editing. */
    var onEmptyTap: (() -> Unit)? = null

    init {
        val key: String?
        val templateResourceId: Int
        val array = context.theme.obtainStyledAttributes(attrs, R.styleable.DynamicGridLayout, 0, 0)
        try {
            key = array.getString(R.styleable.DynamicGridLayout_grid_preference_key)
            templateResourceId = array.getResourceId(R.styleable.DynamicGridLayout_grid_layout_template, 0)
            warningMessage = array.getString(R.styleable.DynamicGridLayout_grid_no_space_warning) ?: "No room available."
            if (key.isNullOrEmpty()) throw RuntimeException("'grid_preference_key' attribute is required")
        } finally {
            array.recycle()
        }
        templateManager = GridTemplateManager(context.applicationContext as Farol, key!!, templateResourceId)
        clipChildren = false
        clipToPadding = false
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        if (child !is DraggableGridItem) {
            throw IllegalStateException("DraggableGridLayout can host only DraggableGridItem children.")
        }
    }

    private fun isAnimationRunning(): Boolean = runningAnimations > 0

    open fun setRearrangeable(rearrangeable: Boolean) {
        if (isRearrangeable == rearrangeable) return
        isRearrangeable = rearrangeable
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child != null) (child as DraggableGridItem).setResizingActive(rearrangeable)
        }
    }

    fun getCellWidth(): Int = cellWidth
    fun getCellHeight(): Int = cellHeight
    fun isGridMeasured(): Boolean = cellWidth > 0 && cellHeight > 0

    /**
     * @noinspection ReplaceNullCheck
     */
    open fun addItem(view: DraggableGridItem, defaultTemplateData: ItemLayoutData?) {
        val id = view.itemId!!
        val saved = templateManager.getLayoutForSize(colCount, rowCount)[id]
        val data = saved ?: defaultTemplateData ?: ItemLayoutData(id, 0, 0, 1, 1)
        if (defaultTemplateData != null) applyItemLimits(data, defaultTemplateData)
        applyItemLimits(view, data)
        data.colSpan = clampColumnSpan(view, 0, data.colSpan)
        data.rowSpan = clampRowSpan(view, 0, data.rowSpan)
        data.col = maxOf(0, minOf(data.col, colCount - data.colSpan))
        data.row = maxOf(0, minOf(data.row, rowCount - data.rowSpan))
        view.setResizingActive(isRearrangeable)
        val layoutParams = LayoutParams(data.col, data.row, data.colSpan, data.rowSpan)
        view.layoutParams = layoutParams
        val currentState = buildCurrentState()
        val targetRect = Rect(layoutParams.col, layoutParams.row,
            layoutParams.col + layoutParams.colSpan, layoutParams.row + layoutParams.rowSpan)
        if (!currentState.isOccupied(targetRect, null)) {
            addView(view)
            saveLayoutState()
            return
        }
        val rearranged = attemptGlobalRearrange(currentState, view,
            layoutParams.colSpan, layoutParams.rowSpan, false)
        if (rearranged != null) {
            commitState(rearranged)
            addView(view)
            saveLayoutState()
            return
        }
        val shrunken = attemptGlobalRearrange(currentState, view,
            layoutParams.colSpan, layoutParams.rowSpan, true)
        if (shrunken != null) {
            commitState(shrunken)
            addView(view)
            saveLayoutState()
            return
        }
        val firstFree = findClosestFreeSpotInState(currentState,
            layoutParams.colSpan, layoutParams.rowSpan, 0, 0, null)
        if (firstFree != null) {
            layoutParams.col = firstFree.left
            layoutParams.row = firstFree.top
            addView(view)
            saveLayoutState()
            return
        }
        Toast.makeText(context, warningMessage, Toast.LENGTH_SHORT).show()
    }

    fun updateItemLayoutData(item: DraggableGridItem, data: ItemLayoutData): Boolean {
        if (item.parent !== this) return false
        applyItemLimits(item, data)
        val params = item.layoutParams as LayoutParams
        val colSpan = clampColumnSpan(item, 0, params.colSpan)
        val rowSpan = clampRowSpan(item, 0, params.rowSpan)
        val col = maxOf(0, minOf(params.col, colCount - colSpan))
        val row = maxOf(0, minOf(params.row, rowCount - rowSpan))
        if (params.col == col && params.row == row && params.colSpan == colSpan && params.rowSpan == rowSpan) {
            return false
        }
        val currentState = buildCurrentState()
        val nextState = simulateMove(currentState, item, col, row, colSpan, rowSpan)
            ?: findClosestFreeSpotInState(currentState, colSpan, rowSpan, col, row, item)?.let {
                simulateMove(currentState, item, it.left, it.top, colSpan, rowSpan)
            }
        if (nextState == null) {
            Toast.makeText(context, warningMessage, Toast.LENGTH_SHORT).show()
            return false
        }
        commitState(nextState)
        return true
    }

    private fun applyItemLimits(target: ItemLayoutData, source: ItemLayoutData) {
        target.minColSpan = if (source.minColSpan > 0) source.minColSpan else 1
        target.minWidth = if (source.minWidth > 0) source.minWidth else -1
        target.maxColSpan = if (source.maxColSpan > 0) source.maxColSpan else -1
        target.maxWidth = if (source.maxWidth > 0) source.maxWidth else -1
        target.minRowSpan = if (source.minRowSpan > 0) source.minRowSpan else 1
        target.minHeight = if (source.minHeight > 0) source.minHeight else -1
        target.maxRowSpan = if (source.maxRowSpan > 0) source.maxRowSpan else -1
        target.maxHeight = if (source.maxHeight > 0) source.maxHeight else -1
    }

    private fun applyItemLimits(view: DraggableGridItem, data: ItemLayoutData) {
        view.minColSpan = if (data.minColSpan > 0) data.minColSpan else 1
        view.minWidth = if (data.minWidth > 0) data.minWidth else -1
        view.maxColSpan = if (data.maxColSpan > 0) data.maxColSpan else -1
        view.maxWidth = if (data.maxWidth > 0) data.maxWidth else -1
        view.minRowSpan = if (data.minRowSpan > 0) data.minRowSpan else 1
        view.minHeight = if (data.minHeight > 0) data.minHeight else -1
        view.maxRowSpan = if (data.maxRowSpan > 0) data.maxRowSpan else -1
        view.maxHeight = if (data.maxHeight > 0) data.maxHeight else -1
    }

    private fun findClosestFreeSpotInState(state: GridState, spanX: Int, spanY: Int,
                                           preferredCol: Int, preferredRow: Int, ignoreView: View?): Rect? {
        var bestDistance = Double.MAX_VALUE
        var bestRect: Rect? = null
        for (row in 0..state.rows - spanY) {
            for (col in 0..state.cols - spanX) {
                val candidate = Rect(col, row, col + spanX, row + spanY)
                if (!state.isOccupied(candidate, ignoreView)) {
                    val distance = sqrt((col - preferredCol).toDouble().pow(2.0) +
                        (row - preferredRow).toDouble().pow(2.0))
                    if (distance < bestDistance) {
                        bestDistance = distance
                        bestRect = candidate
                    }
                }
            }
        }
        return bestRect
    }

    private fun attemptGlobalRearrange(currentState: GridState, newItem: View,
                                       newSpanX: Int, newSpanY: Int, allowShrink: Boolean): GridState? {
        val newDrag = newItem as DraggableGridItem
        for (spanX in newSpanX downTo newDrag.minColSpan) {
            for (spanY in newSpanY downTo newDrag.minRowSpan) {
                for (row in 0..rowCount - spanY) {
                    for (col in 0..colCount - spanX) {
                        val state = currentState.cloneState()
                        state.placements[newItem] = Rect(col, row, col + spanX, row + spanY)
                        val solved = resolveAllCollisions(state, newItem, allowShrink, rowCount, colCount)
                        if (solved != null) return solved
                    }
                }
            }
        }
        return null
    }

    private fun resolveAllCollisions(state: GridState, newItem: View, allowShrink: Boolean,
                                     rowCount: Int, colCount: Int): GridState? {
        val queue: Queue<GridState> = LinkedList()
        queue.add(state)
        while (queue.isNotEmpty()) {
            val current = queue.poll() ?: break
            var collisionFound = false
            var stopProcessing = false
            for ((a, ra) in current.placements) {
                if (stopProcessing) break
                for ((b, rb) in current.placements) {
                    if (a !== b && Rect.intersects(ra, rb)) {
                        collisionFound = true
                        val victim = b
                        if (victim !== newItem) {
                            if (allowShrink) {
                                val d = victim as DraggableGridItem
                                if (rb.width() > d.minColSpan) {
                                    val copy = current.cloneState()
                                    val victimRect = copy.placements[victim]
                                    if (victimRect != null) {
                                        victimRect.right -= 1
                                        if (reflowItem(copy, victim, rowCount, colCount)) queue.add(copy)
                                    }
                                }
                                if (rb.height() > d.minRowSpan) {
                                    val copy = current.cloneState()
                                    val victimRect = copy.placements[victim]
                                    if (victimRect != null) {
                                        victimRect.bottom -= 1
                                        if (reflowItem(copy, victim, rowCount, colCount)) queue.add(copy)
                                    }
                                }
                            }
                            val movedCopy = current.cloneState()
                            if (reflowItem(movedCopy, victim, rowCount, colCount)) queue.add(movedCopy)
                        }
                        stopProcessing = true
                    }
                }
            }
            if (!collisionFound) return current
        }
        return null
    }

    private fun reflowItem(state: GridState, item: View, rowCount: Int, colCount: Int): Boolean {
        val rect = state.placements.remove(item) ?: return false
        val spanX = rect.width()
        val spanY = rect.height()
        for (row in 0..rowCount - spanY) {
            for (col in 0..colCount - spanX) {
                val candidate = Rect(col, row, col + spanX, row + spanY)
                var fits = true
                for (other in state.placements.values) {
                    if (Rect.intersects(candidate, other)) {
                        fits = false
                        break
                    }
                }
                if (fits) {
                    state.placements[item] = candidate
                    return true
                }
            }
        }
        state.placements[item] = rect
        return false
    }

    open fun removeItem(view: DraggableGridItem?, forget: Boolean = false) {
        if (view == null) return
        val id = view.itemId
        removeView(view)
        preAnimVisualPos.remove(view)
        if (activeItem === view) {
            activeItem = null
            resetHoverState()
        }
        if (currentHoverTarget === view) currentHoverTarget = null
        if (sizeItem === view) clearSizeAnimation()
        if (forget && id != null) templateManager.removeUserLayoutItem(id)
        saveLayoutState()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val totalWidth = MeasureSpec.getSize(widthMeasureSpec)
        val totalHeight = MeasureSpec.getSize(heightMeasureSpec)
        val horizontalPadding = paddingLeft + paddingRight
        val verticalPadding = paddingTop + paddingBottom
        val availableWidth = totalWidth - horizontalPadding
        val availableHeight = totalHeight - verticalPadding
        val density = resources.displayMetrics.density
        val minCellSizePx = (MIN_CELL_SIZE_DP * density).toInt()
        colCount = maxOf(if (Measurements.isLandscape()) MIN_COLS_LANDSCAPE else MIN_COLS_PORTRAIT,
            availableWidth / minCellSizePx)
        while (availableWidth / colCount > minCellSizePx * 2) colCount++
        cellWidth = availableWidth / colCount
        rowCount = maxOf(if (Measurements.isLandscape()) MIN_ROWS_LANDSCAPE else MIN_ROWS_PORTRAIT,
            availableHeight / minCellSizePx)
        while (availableHeight / rowCount > minCellSizePx * 2) rowCount++
        cellHeight = availableHeight / rowCount
        if (colCount != lastMeasuredCols || rowCount != lastMeasuredRows) {
            lastMeasuredCols = colCount
            lastMeasuredRows = rowCount
            reloadLayoutForCurrentSize()
        }
        val measuredWidth = colCount * cellWidth + horizontalPadding
        val measuredHeight = rowCount * cellHeight + verticalPadding
        setMeasuredDimension(measuredWidth, measuredHeight)
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val params = child.layoutParams as LayoutParams
            if (params.col + params.colSpan > colCount) params.col = maxOf(0, colCount - params.colSpan)
            if (params.row + params.rowSpan > rowCount) params.row = maxOf(0, rowCount - params.rowSpan)
            var childWidth = params.colSpan * cellWidth
            var childHeight = params.rowSpan * cellHeight
            if (child === sizeItem) {
                childWidth = (sizeFromW + (sizeToW - sizeFromW) * sizeFraction).toInt()
                childHeight = (sizeFromH + (sizeToH - sizeFromH) * sizeFraction).toInt()
            }
            child.measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY))
        }
    }

    private fun reloadLayoutForCurrentSize() {
        val newConfig = templateManager.getLayoutForSize(colCount, rowCount)
        var changed = false
        var newState = GridState(colCount, rowCount)
        val configuredItems: MutableList<View> = ArrayList()
        val unconfiguredItems: MutableList<View> = ArrayList()
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child != null) {
                val item = child as DraggableGridItem
                if (newConfig.containsKey(item.itemId!!)) configuredItems.add(child)
                else unconfiguredItems.add(child)
            }
        }
        for (child in configuredItems) {
            val item = child as DraggableGridItem
            val data = newConfig[item.itemId!!]
            if (data != null) {
                // Geometry belongs to the template; limits belong to the current component/provider.
                val colSpan = clampColumnSpan(item, 0, data.colSpan)
                val rowSpan = clampRowSpan(item, 0, data.rowSpan)
                val clampedColumn = maxOf(0, minOf(data.col, colCount - colSpan))
                val clampedRow = maxOf(0, minOf(data.row, rowCount - rowSpan))
                val targetRect = Rect(clampedColumn, clampedRow, clampedColumn + colSpan, clampedRow + rowSpan)
                if (!newState.isOccupied(targetRect, null)) newState.placements[child] = targetRect
                else unconfiguredItems.add(child)
            }
        }
        for (child in unconfiguredItems) {
            val item = child as DraggableGridItem
            val params = child.layoutParams as LayoutParams
            val oldCol = maxOf(0, minOf(params.col, colCount - 1))
            val oldRow = maxOf(0, minOf(params.row, rowCount - 1))
            params.colSpan = clampColumnSpan(item, 0, params.colSpan)
            params.rowSpan = clampRowSpan(item, 0, params.rowSpan)
            val freeSpot = findClosestFreeSpotInState(newState, params.colSpan, params.rowSpan, oldCol, oldRow, null)
            if (freeSpot != null) {
                newState.placements[child] = freeSpot
            } else {
                val rearranged = attemptGlobalRearrange(newState, child, params.colSpan, params.rowSpan, true)
                if (rearranged != null) {
                    newState = rearranged
                } else {
                    val clampedCol = maxOf(0, minOf(oldCol, colCount - params.colSpan))
                    val clampedRow = maxOf(0, minOf(oldRow, rowCount - params.rowSpan))
                    newState.placements[child] = Rect(clampedCol, clampedRow,
                        clampedCol + params.colSpan, clampedRow + params.rowSpan)
                }
            }
        }
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val placedRect = newState.placements[child]
            if (placedRect != null) {
                val params = child.layoutParams as LayoutParams
                if (params.col != placedRect.left || params.row != placedRect.top ||
                    params.colSpan != placedRect.width() || params.rowSpan != placedRect.height()) {
                    params.col = placedRect.left
                    params.row = placedRect.top
                    params.colSpan = placedRect.width()
                    params.rowSpan = placedRect.height()
                    changed = true
                }
            }
        }
        if (changed) requestLayout()
    }

    @SuppressLint("DrawAllocation")
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val params = child.layoutParams as LayoutParams
            val left = paddingLeft + params.col * cellWidth
            val top = paddingTop + params.row * cellHeight
            val right = left + child.measuredWidth
            val bottom = top + child.measuredHeight
            if (child === activeItem && !isResizing && child.getTag(R.id.is_dragging_tag) != null) {
                child.layout(child.left, child.top, child.left + child.measuredWidth, child.top + child.measuredHeight)
            } else {
                val oldPos = preAnimVisualPos[child]
                child.layout(left, top, right, bottom)
                if (oldPos != null && child !== activeItem) {
                    val startTransX = oldPos.x - left
                    val startTransY = oldPos.y - top
                    val distance = sqrt(startTransX.toDouble().pow(2.0) + startTransY.toDouble().pow(2.0))
                    if (distance > 2) {
                        child.translationX = startTransX.toFloat()
                        child.translationY = startTransY.toFloat()
                        child.animate().translationX(0f).translationY(0f)
                            .setDuration(minOf(Animation.LONG.getDuration().toDouble(),
                                maxOf(Animation.SHORT.getDuration().toDouble(), distance * 0.8)).toLong())
                            .setInterpolator(DecelerateInterpolator(1.2f)).start()
                    } else {
                        child.translationX = 0f
                        child.translationY = 0f
                    }
                } else if (child !== activeItem) {
                    child.translationX = 0f
                    child.translationY = 0f
                }
            }
        }
        preAnimVisualPos.clear()
    }

    fun getColumnCount(): Int = colCount
    fun getRowCount(): Int = rowCount

    /**
     * Pass col 0 to fit the span to the whole grid and let the position shift to make room, or the item's
     * real column to cap growth at the grid edge (resizing).
     */
    private fun clampColumnSpan(item: DraggableGridItem, col: Int, requested: Int): Int {
        val maxGridSpan = maxOf(1, colCount - col)
        var minSpan = maxOf(1, item.minColSpan)
        if (item.minWidth > 0 && cellWidth > 0) {
            minSpan = maxOf(minSpan, ceil(item.minWidth.toDouble() / cellWidth).toInt())
        }
        var maxSpan = if (item.maxColSpan > 0) minOf(maxGridSpan, item.maxColSpan) else maxGridSpan
        if (item.maxWidth > 0 && cellWidth > 0) {
            maxSpan = minOf(maxSpan, maxOf(1, floor(item.maxWidth.toDouble() / cellWidth).toInt()))
        }
        minSpan = minOf(minSpan, maxSpan)
        return maxOf(minSpan, minOf(requested, maxSpan))
    }

    private fun clampRowSpan(item: DraggableGridItem, row: Int, requested: Int): Int {
        val maxGridSpan = maxOf(1, rowCount - row)
        var minSpan = maxOf(1, item.minRowSpan)
        if (item.minHeight > 0 && cellHeight > 0) {
            minSpan = maxOf(minSpan, ceil(item.minHeight.toDouble() / cellHeight).toInt())
        }
        var maxSpan = if (item.maxRowSpan > 0) minOf(maxGridSpan, item.maxRowSpan) else maxGridSpan
        if (item.maxHeight > 0 && cellHeight > 0) {
            maxSpan = minOf(maxSpan, maxOf(1, floor(item.maxHeight.toDouble() / cellHeight).toInt()))
        }
        minSpan = minOf(minSpan, maxSpan)
        return maxOf(minSpan, minOf(requested, maxSpan))
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = isRearrangeable || super.onInterceptTouchEvent(ev)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isRearrangeable) return super.onTouchEvent(event)
        return handleRearrangeTouch(event, allowResize = true)
    }

    fun onHeldTouchEvent(event: MotionEvent) {
        if (isRearrangeable) handleRearrangeTouch(event, allowResize = false)
    }

    private fun handleRearrangeTouch(event: MotionEvent, allowResize: Boolean): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val item = getChildAtPos(x, y)
                activeItem = item
                initialTouchX = x
                initialTouchY = y
                if (item != null) {
                    captureLayoutState()
                    lastTouchX = x
                    lastTouchY = y
                    isResizing = allowResize && item.isResizeHandleTouched(x - item.left, y - item.top)
                    val params = item.layoutParams as LayoutParams
                    originalParams = LayoutParams(params.col, params.row, params.colSpan, params.rowSpan)
                    lastReorderCol = originalParams.col
                    lastReorderRow = originalParams.row
                    pendingTargetCol = originalParams.col
                    pendingTargetRow = originalParams.row
                    item.bringToFront()
                    if (!isResizing) item.setTag(R.id.is_dragging_tag, true)
                    for (index in 0 until childCount) {
                        val child = getChildAt(index)
                        if (child != null) {
                            val gridItem = child as DraggableGridItem
                            if (gridItem === item) gridItem.animateToState(DraggableGridItem.STATE_ACTIVE)
                            else gridItem.animateToState(DraggableGridItem.STATE_INACTIVE)
                        }
                    }
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeItem != null) {
                    val dx = x - lastTouchX
                    val dy = y - lastTouchY
                    if (isResizing) handleResize(x, y) else handleDrag(dx, dy)
                    lastTouchX = x
                    lastTouchY = y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeItem == null && event.action == MotionEvent.ACTION_UP &&
                    hypot(x - initialTouchX, y - initialTouchY) <= touchSlop
                ) {
                    onEmptyTap?.invoke()
                }
                if (activeItem != null) {
                    reorderRunnable?.let {
                        handler.removeCallbacks(it)
                        it.run()
                        reorderRunnable = null
                    }
                    if (isResizing) finishResize() else finishDrag()
                    for (index in 0 until childCount) {
                        val child = getChildAt(index)
                        if (child != null) (child as DraggableGridItem).animateToState(DraggableGridItem.STATE_IDLE)
                    }
                }
                clearCurrentHints()
                resetHoverState()
            }
        }
        return true
    }

    private fun handleResize(currX: Float, currY: Float) {
        val item = activeItem ?: return
        val col = originalParams.col
        val row = originalParams.row
        // The frame follows the finger anywhere the widget can legally go; the content catches up cell by cell.
        val visualW = (originalParams.colSpan * cellWidth + (currX - initialTouchX)).coerceIn(
            (clampColumnSpan(item, col, 1) * cellWidth).toFloat(),
            (clampColumnSpan(item, col, colCount) * cellWidth).toFloat()
        )
        val visualH = (originalParams.rowSpan * cellHeight + (currY - initialTouchY)).coerceIn(
            (clampRowSpan(item, row, 1) * cellHeight).toFloat(),
            (clampRowSpan(item, row, rowCount) * cellHeight).toFloat()
        )
        item.setVisualResizeBounds(visualW, visualH)
        val params = item.layoutParams as LayoutParams
        val spanX = clampColumnSpan(item, col, calculateGridSpan(visualW, cellWidth))
        val spanY = clampRowSpan(item, row, calculateGridSpan(visualH, cellHeight))
        // Neighbours in the way are pushed aside when there is room for them; if not, grow along whichever axis fits.
        for ((newX, newY) in listOf(spanX to spanY, spanX to params.rowSpan, params.colSpan to spanY)) {
            if (newX == params.colSpan && newY == params.rowSpan) continue
            val pushed = simulateMove(buildCurrentState(), item, col, row, newX, newY) ?: continue
            commitState(pushed)
            animateItemSize(item, newX * cellWidth, newY * cellHeight)
            return
        }
    }

    private fun animateItemSize(item: DraggableGridItem, toW: Int, toH: Int) {
        if (item === sizeItem) {
            sizeFromW += (sizeToW - sizeFromW) * sizeFraction
            sizeFromH += (sizeToH - sizeFromH) * sizeFraction
        } else {
            sizeFromW = item.width.toFloat()
            sizeFromH = item.height.toFloat()
        }
        sizeItem = item
        sizeToW = toW.toFloat()
        sizeToH = toH.toFloat()
        sizeFraction = 0f
        sizeAnimator?.cancel()
        sizeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Animation.SHORT.getDuration().toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                sizeFraction = it.animatedValue as Float
                requestLayout()
            }
            start()
        }
    }

    private fun clearSizeAnimation() {
        sizeAnimator?.cancel()
        sizeAnimator = null
        sizeItem = null
        sizeFraction = 1f
    }

    private fun finishResize() {
        val itemToResize = activeItem
        activeItem = null
        isResizing = false
        if (itemToResize == null) return
        val params = itemToResize.layoutParams as LayoutParams
        val targetW = (params.colSpan * cellWidth).toFloat()
        val targetH = (params.rowSpan * cellHeight).toFloat()
        itemToResize.animateVisualResize(targetW, targetH, Runnable {
            clearSizeAnimation()
            itemToResize.setResizingActive(isRearrangeable)
            saveLayoutState()
            requestLayout()
        })
    }

    private fun handleDrag(dx: Float, dy: Float) {
        val item = activeItem ?: return
        val parentLeft = paddingLeft
        val parentTop = paddingTop
        val parentRight = width - paddingRight
        val parentBottom = height - paddingBottom
        val clampedX = maxOf(parentLeft, minOf(item.left + dx.toInt(), parentRight - item.width))
        val clampedY = maxOf(parentTop, minOf(item.top + dy.toInt(), parentBottom - item.height))
        item.offsetLeftAndRight(clampedX - item.left)
        item.offsetTopAndBottom(clampedY - item.top)
        val params = item.layoutParams as LayoutParams
        val relativeLeft = (item.left - parentLeft).toFloat()
        val relativeTop = (item.top - parentTop).toFloat()
        val targetCol = maxOf(0, minOf(((relativeLeft + cellWidth / 2f) / cellWidth).toInt(),
            colCount - params.colSpan))
        val targetRow = maxOf(0, minOf(((relativeTop + cellHeight / 2f) / cellHeight).toInt(),
            rowCount - params.rowSpan))
        if (targetCol != pendingTargetCol || targetRow != pendingTargetRow) {
            reorderRunnable?.let { handler.removeCallbacks(it) }
            reorderRunnable = null
            clearCurrentHints()
            pendingTargetCol = targetCol
            pendingTargetRow = targetRow
            if (targetCol == lastReorderCol && targetRow == lastReorderRow) return
            val currentState = buildCurrentState()
            val targetRect = Rect(targetCol, targetRow, targetCol + params.colSpan, targetRow + params.rowSpan)
            val isSpotOccupied = currentState.isOccupied(targetRect, item)
            if (isSpotOccupied) applyHint(targetCol, targetRow, params.colSpan, params.rowSpan)
            reorderRunnable = Runnable {
                if (isAnimationRunning()) {
                    clearCurrentHints()
                    return@Runnable
                }
                val simulated = simulateMove(currentState, activeItem, targetCol, targetRow,
                    params.colSpan, params.rowSpan)
                if (simulated != null) {
                    clearCurrentHints()
                    if (!isSpotOccupied) clearVisualNudges() else applySimulatedStateVisually(simulated)
                    commitState(simulated)
                    lastReorderCol = targetCol
                    lastReorderRow = targetRow
                }
            }
            handler.postDelayed(reorderRunnable!!, REORDER_DELAY_MS)
        }
    }

    private fun applyHint(targetCol: Int, targetRow: Int, colSpan: Int, rowSpan: Int) {
        clearCurrentHints()
        val targetRect = Rect(targetCol, targetRow, targetCol + colSpan, targetRow + rowSpan)
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child !== activeItem) {
                val params = child.layoutParams as LayoutParams
                val childRect = Rect(params.col, params.row,
                    params.col + params.colSpan, params.row + params.rowSpan)
                if (Rect.intersects(targetRect, childRect)) {
                    hintedViews.add(child)
                    child.animate().scaleX(HINT_SCALE).scaleY(HINT_SCALE).alpha(HINT_ALPHA)
                        .setDuration(Animation.MEDIUM.getDuration().toLong())
                        .setInterpolator(DecelerateInterpolator()).start()
                }
            }
        }
    }

    private fun clearCurrentHints() {
        for (view in hintedViews) {
            view.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(Animation.EXTENDED.getDuration().toLong()).start()
        }
        hintedViews.clear()
    }

    private fun clearVisualNudges() {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child !== activeItem && (child.translationX != 0f || child.translationY != 0f)) {
                child.animate().translationX(0f).translationY(0f)
                    .setDuration(Animation.SHORT.getDuration().toLong())
                    .setInterpolator(DecelerateInterpolator()).start()
            }
        }
    }

    private fun applySimulatedStateVisually(state: GridState) {
        for ((view, targetRect) in state.placements) {
            if (view !== activeItem) {
                val targetLeft = paddingLeft + targetRect.left * cellWidth
                val targetTop = paddingTop + targetRect.top * cellHeight
                val dx = targetLeft - view.left
                val dy = targetTop - view.top
                runningAnimations++
                view.animate().translationX(dx.toFloat()).translationY(dy.toFloat())
                    .setDuration(Animation.MEDIUM.getDuration().toLong())
                    .setInterpolator(DecelerateInterpolator())
                    .setListener(object : AnimatorListenerAdapter() {
                        private var handled = false
                        private fun finish() {
                            if (!handled) {
                                handled = true
                                runningAnimations--
                            }
                        }
                        override fun onAnimationEnd(animation: Animator) = finish()
                        override fun onAnimationCancel(animation: Animator) = finish()
                    }).start()
            }
        }
    }

    private fun commitState(state: GridState) {
        captureLayoutState()
        for ((view, rect) in state.placements) {
            val params = view.layoutParams as LayoutParams
            params.col = rect.left
            params.row = rect.top
            params.colSpan = rect.width()
            params.rowSpan = rect.height()
        }
        requestLayout()
        saveLayoutState()
    }

    private fun finishDrag() {
        val item = activeItem ?: return
        val params = item.layoutParams as LayoutParams
        val parentLeft = paddingLeft
        val parentTop = paddingTop
        val relativeLeft = (item.left - parentLeft).toFloat()
        val relativeTop = (item.top - parentTop).toFloat()
        val rawCol = ((relativeLeft + cellWidth / 2f) / cellWidth).toInt()
        val rawRow = ((relativeTop + cellHeight / 2f) / cellHeight).toInt()
        val targetCol = maxOf(0, minOf(rawCol, colCount - params.colSpan))
        val targetRow = maxOf(0, minOf(rawRow, rowCount - params.rowSpan))
        val currentState = buildCurrentState()
        val simulated = simulateMove(currentState, item, targetCol, targetRow, params.colSpan, params.rowSpan)
        if (simulated != null) commitState(simulated)
        item.setTag(R.id.is_dragging_tag, null)
        captureLayoutState()
        activeItem = null
        requestLayout()
        saveLayoutState()
    }

    private fun resetHoverState() {
        reorderRunnable?.let { handler.removeCallbacks(it) }
        reorderRunnable = null
        currentHoverTarget?.let {
            it.animate().translationX(0f).translationY(0f)
                .setDuration(Animation.MEDIUM.getDuration().toLong()).start()
        }
        currentHoverTarget = null
    }

    private fun calculateGridSpan(visualSize: Float, cellSize: Int): Int =
        if (cellSize > 0) maxOf(1, Math.round(visualSize / cellSize)) else 1

    private fun captureLayoutState() {
        preAnimVisualPos.clear()
        for (index in 0 until childCount) {
            val view = getChildAt(index)
            preAnimVisualPos[view] = Point(view.x.toInt(), view.y.toInt())
        }
    }

    private fun getChildAtPos(x: Float, y: Float): DraggableGridItem? {
        for (index in childCount - 1 downTo 0) {
            val child = getChildAt(index)
            if (x >= child.left && x <= child.right && y >= child.top && y <= child.bottom) {
                return child as DraggableGridItem
            }
        }
        return null
    }

    private fun saveLayoutState() {
        val attached: MutableMap<String, ItemLayoutData> = HashMap()
        for (index in 0 until childCount) {
            val childItem = getChildAt(index) as DraggableGridItem
            val params = childItem.layoutParams as LayoutParams
            val id = childItem.itemId!!
            val data = ItemLayoutData(id, params.col, params.row, params.colSpan, params.rowSpan)
            data.minColSpan = childItem.minColSpan
            data.minWidth = childItem.minWidth
            data.maxColSpan = childItem.maxColSpan
            data.maxWidth = childItem.maxWidth
            data.minRowSpan = childItem.minRowSpan
            data.minHeight = childItem.minHeight
            data.maxRowSpan = childItem.maxRowSpan
            data.maxHeight = childItem.maxHeight
            attached[id] = data
        }
        templateManager.saveUserLayout(
            colCount,
            rowCount,
            GridTemplateManager.mergeLayout(templateManager.getLayoutForSize(colCount, rowCount), attached)
        )
    }

    private fun simulateMove(initialState: GridState, activeItem: View?, targetCol: Int,
                             targetRow: Int, spanX: Int, spanY: Int): GridState? {
        val item = activeItem ?: return null
        val state = initialState.cloneState()
        val targetRect = Rect(targetCol, targetRow, targetCol + spanX, targetRow + spanY)
        if (targetRect.right > state.cols || targetRect.bottom > state.rows) return null
        state.placements.remove(item)
        val collisions = state.getCollisions(targetRect, null)
        state.placements[item] = targetRect
        if (collisions.isEmpty()) return state
        for (victim in collisions) {
            if (!resolveCollision(state, victim, targetCol, targetRow)) return null
        }
        return state
    }

    private fun resolveCollision(state: GridState, victim: View, activeCol: Int, activeRow: Int): Boolean {
        val victimRect = state.placements[victim] ?: return false
        val pushX = Integer.compare(activeCol - victimRect.left, 0)
        val pushY = Integer.compare(activeRow - victimRect.top, 0)
        val directions = if (Math.abs(pushX) > Math.abs(pushY)) {
            arrayOf(intArrayOf(-1, 0), intArrayOf(pushX, 0), intArrayOf(0, pushY),
                intArrayOf(0, -pushY), intArrayOf(-pushX, 0))
        } else {
            arrayOf(intArrayOf(0, 1), intArrayOf(0, pushY), intArrayOf(pushX, 0),
                intArrayOf(-pushX, 0), intArrayOf(0, -pushY))
        }
        for (dir in directions) {
            if (dir[0] != 0 || dir[1] != 0) {
                val testRect = Rect(victimRect.left + dir[0], victimRect.top + dir[1],
                    victimRect.right + dir[0], victimRect.bottom + dir[1])
                if (!state.isOccupied(testRect, victim)) {
                    state.placements[victim] = testRect
                    return true
                }
            }
        }
        val closest = findClosestFreeSpotInState(state, victimRect.width(), victimRect.height(),
            victimRect.left, victimRect.top, victim)
        if (closest != null) {
            state.placements[victim] = closest
            return true
        }
        return false
    }

    private fun buildCurrentState(): GridState {
        val state = GridState(colCount, rowCount)
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val params = child.layoutParams as LayoutParams
            state.placements[child] = Rect(params.col, params.row,
                params.col + params.colSpan, params.row + params.rowSpan)
        }
        return state
    }

    private class GridState(val cols: Int, val rows: Int) {
        val placements: MutableMap<View, Rect> = HashMap()

        fun cloneState(): GridState {
            val copy = GridState(cols, rows)
            copy.placements.putAll(placements)
            return copy
        }

        fun getCollisions(targetRect: Rect, ignoreView: View?): MutableList<View> {
            val collisions: MutableList<View> = ArrayList()
            for ((view, rect) in placements) {
                if (view !== ignoreView && Rect.intersects(targetRect, rect)) collisions.add(view)
            }
            return collisions
        }

        fun isOccupied(rect: Rect, ignoreView: View?): Boolean {
            if (rect.left < 0 || rect.top < 0 || rect.right > cols || rect.bottom > rows) return true
            return getCollisions(rect, ignoreView).isNotEmpty()
        }
    }

    private class LayoutParams(var col: Int, var row: Int, var colSpan: Int, var rowSpan: Int) :
        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    open class ItemLayoutData(
        @JvmField var id: String,
        @JvmField var col: Int,
        @JvmField var row: Int,
        @JvmField var colSpan: Int,
        @JvmField var rowSpan: Int
    ) {
        @JvmField var minColSpan = 1
        @JvmField var minHeight = -1
        @JvmField var maxColSpan = -1
        @JvmField var maxHeight = -1
        @JvmField var minRowSpan = 1
        @JvmField var minWidth = -1
        @JvmField var maxRowSpan = -1
        @JvmField var maxWidth = -1
    }

    companion object {
        const val TAG = "DynamicGridLayout"
        private const val MIN_CELL_SIZE_DP = 110
        private const val MIN_COLS_PORTRAIT = 4
        private const val MIN_COLS_LANDSCAPE = 5
        private const val MIN_ROWS_PORTRAIT = 5
        private const val MIN_ROWS_LANDSCAPE = 3
        private const val REORDER_DELAY_MS = 300L
        private const val HINT_SCALE = 0.85f
        private const val HINT_ALPHA = 0.85f
    }
}
