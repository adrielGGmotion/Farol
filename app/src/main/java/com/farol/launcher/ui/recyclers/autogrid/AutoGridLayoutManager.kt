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

package com.farol.launcher.ui.recyclers.autogrid

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.farol.launcher.ui.recyclers.async.AsyncRecyclerAdapter
import com.farol.launcher.ui.recyclers.managers.AccurateScrollComputeGridLayoutManager

open class AutoGridLayoutManager(
    context: Context,
    spanCount: Int
) : AccurateScrollComputeGridLayoutManager(context, spanCount) {
    private var observedAdapter: RecyclerView.Adapter<*>? = null
    private var recyclerView: RecyclerView? = null
    private var shouldCenterItems = true
    private var actualSpanCount = spanCount
    private val layoutListener = object : View.OnLayoutChangeListener {
        private var width = 0

        override fun onLayoutChange(
            view: View,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            oldLeft: Int,
            oldTop: Int,
            oldRight: Int,
            oldBottom: Int
        ) {
            if (view.measuredWidth != width) {
                width = view.measuredWidth
                centerItems()
            }
        }
    }
    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() {
            centerItems()
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            centerItems()
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            centerItems()
        }
    }

    open override fun setSpanCount(spanCount: Int) {
        actualSpanCount = maxOf(spanCount, 1)
        centerItems()
    }

    open override fun onAttachedToWindow(view: RecyclerView) {
        recyclerView = view
        view.addOnLayoutChangeListener(layoutListener)
        super.onAttachedToWindow(view)
    }

    open override fun onDetachedFromWindow(view: RecyclerView, recycler: RecyclerView.Recycler) {
        super.onDetachedFromWindow(view, recycler)
        view.removeOnLayoutChangeListener(layoutListener)
        recyclerView = null
        setAdapter(null)
    }

    internal fun setAdapter(adapter: RecyclerView.Adapter<*>?) {
        observedAdapter?.unregisterAdapterDataObserver(observer)
        observedAdapter = adapter

        if (adapter != null) {
            adapter.registerAdapterDataObserver(observer)
            centerItems()
        }
    }

    private fun centerItems() {
        val recycler = recyclerView
        val currentAdapter = observedAdapter
        if (recycler == null || currentAdapter == null) {
            super.setSpanCount(actualSpanCount)
            return
        }

        if (!shouldCenterItems) {
            super.setSpanCount(actualSpanCount)
            val marginLayoutParams = recycler.getLayoutParams() as ViewGroup.MarginLayoutParams
            marginLayoutParams.leftMargin = 0
            marginLayoutParams.rightMargin = 0
            recycler.setLayoutParams(marginLayoutParams)
        } else {
            var itemCount = if (currentAdapter is AsyncRecyclerAdapter<*>) {
                currentAdapter.getTotalItemCount()
            } else {
                currentAdapter.getItemCount()
            }
            itemCount = getBalancedSpanCount(itemCount, actualSpanCount)

            if (itemCount < actualSpanCount) {
                super.setSpanCount(itemCount)
                val marginLayoutParams = recycler.getLayoutParams() as ViewGroup.MarginLayoutParams
                val margin = (
                    (recycler.getMeasuredWidth() + marginLayoutParams.leftMargin + marginLayoutParams.rightMargin -
                        recycler.getPaddingLeft() - recycler.getPaddingRight()) *
                        (1f - itemCount / actualSpanCount.toFloat()) / 2
                    ).toInt()
                marginLayoutParams.leftMargin = maxOf(0, margin)
                marginLayoutParams.rightMargin = maxOf(0, margin)
                recycler.setLayoutParams(marginLayoutParams)
            } else {
                super.setSpanCount(actualSpanCount)
                val marginLayoutParams = recycler.getLayoutParams() as ViewGroup.MarginLayoutParams
                marginLayoutParams.leftMargin = 0
                marginLayoutParams.rightMargin = 0
                recycler.setLayoutParams(marginLayoutParams)
            }
        }

        recycler.post {
            recyclerView?.requestLayout()
        }
    }

    private fun getBalancedSpanCount(itemCount: Int, spanCount: Int): Int {
        if (itemCount <= 0 || spanCount <= 0) {
            return 1
        }

        return minOf(
            Math.ceil(itemCount.toDouble() / Math.ceil(itemCount.toDouble() / spanCount)).toInt(),
            spanCount
        )
    }

    open fun setCenterItems(value: Boolean) {
        shouldCenterItems = value
        centerItems()
    }
}
