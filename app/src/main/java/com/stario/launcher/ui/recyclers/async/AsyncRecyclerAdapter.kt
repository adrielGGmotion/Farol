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

package com.stario.launcher.ui.recyclers.async

import android.annotation.SuppressLint
import android.app.Activity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.asynclayoutinflater.view.AsyncLayoutInflater
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.stario.launcher.ui.Measurements
import com.stario.launcher.ui.common.LimitingTranslationFrameLayout
import java.util.function.Supplier

/**
 * Recycler adapter that handles data loading and inflation in different threads.
 * Applies height estimations to account for fast scrolling.
 * Use only with fixed height AsyncViewHolder root views.
 */
abstract class AsyncRecyclerAdapter<AVH : RecyclerView.ViewHolder> : RecyclerView.Adapter<AVH> {
    private val layoutInflater: AsyncLayoutInflater
    private val activity: Activity
    private val type: InflationType
    private var listener: RecyclerHeightApproximationListener? = null
    private var approximatedRecyclerHeight = UNMEASURED_HEIGHT
    private var recyclerView: RecyclerView? = null
    private var holderHeight = UNMEASURED_HEIGHT
    private var limit: Int

    constructor(activity: Activity) : this(activity, InflationType.ASYNC)

    constructor(activity: Activity, type: InflationType) {
        limit = if (type == InflationType.SYNCED) NO_LIMIT else 1
        layoutInflater = AsyncLayoutInflater(activity)
        this.activity = activity
        this.type = type
    }

    abstract inner class AsyncViewHolder : RecyclerView.ViewHolder {
        @JvmField
        val HEIGHT_UNMEASURED = UNMEASURED_HEIGHT

        private var inflationListener: InflationListener? = null
        private var inflated = false

        constructor() : this(DEFAULT_VIEW_TYPE)

        constructor(viewType: Int) : super(createHolderRoot()) {
            if (type == InflationType.ASYNC) {
                layoutInflater.inflate(getLayout(viewType), itemView as ViewGroup) { view, _, _ ->
                    val params = itemView.getLayoutParams()
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    itemView.setLayoutParams(params)
                    (itemView as ViewGroup).addView(view)
                    postInflate()
                }
            } else if (type == InflationType.SYNCED) {
                val params = itemView.getLayoutParams()
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                itemView.setLayoutParams(params)
                itemView.requestLayout()
                LayoutInflater.from(activity).inflate(getLayout(viewType), itemView as ViewGroup, true)
                postInflate()
            }
        }

        private fun postInflate() {
            onInflated()

            itemView.getViewTreeObserver().addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    itemView.getViewTreeObserver().removeOnGlobalLayoutListener(this)

                    if (holderHeight == UNMEASURED_HEIGHT) {
                        holderHeight = itemView.getMeasuredHeight()
                        approximateRecyclerHeight()
                    } else if (holderHeight != itemView.getMeasuredHeight()) {
                        holderHeight = itemView.getMeasuredHeight()
                        Log.w(
                            TAG,
                            "Holder height estimation for " + this@AsyncRecyclerAdapter.javaClass +
                                "async holders changed. New estimation: " + holderHeight
                        )
                        approximateRecyclerHeight()
                    }
                }
            })

            inflationListener?.onInflated()
            inflationListener = null
            inflated = true
        }

        internal fun setOnInflatedInternal(listener: InflationListener) {
            if (inflated) {
                listener.onInflated()
            } else {
                inflationListener = listener
            }
        }

        protected abstract fun onInflated()
    }

    open fun approximateRecyclerHeight(): Int {
        if (holderHeight == UNMEASURED_HEIGHT) {
            return UNMEASURED_HEIGHT
        }

        val recycler = recyclerView ?: return approximatedRecyclerHeight
        val size = getTotalItemCount()
        val manager = recycler.getLayoutManager()
        val newApproximation = if (manager is GridLayoutManager) {
            val spanCount = manager.getSpanCount()
            holderHeight * (size / spanCount + if (size % spanCount == 0) 0 else 1)
        } else {
            holderHeight * size
        }

        if (approximatedRecyclerHeight == UNMEASURED_HEIGHT ||
            approximatedRecyclerHeight != newApproximation
        ) {
            approximatedRecyclerHeight = newApproximation
            listener?.onNewApproximation(newApproximation)
        }

        return approximatedRecyclerHeight
    }

    private fun createHolderRoot(): View {
        val root: ViewGroup = LimitingTranslationFrameLayout(activity)
        root.setClipChildren(false)
        root.setClipToPadding(false)
        root.setLayoutTransition(null)
        root.setLayoutParams(
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                if (holderHeight != UNMEASURED_HEIGHT) holderHeight else Measurements.dpToPx(50f)
            )
        )
        return root
    }

    @SuppressLint("ClickableViewAccessibility")
    open override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
        recyclerView.getRecycledViewPool().setMaxRecycledViews(DEFAULT_VIEW_TYPE, MAX_POOL_SIZE)
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                removeLimit()
                recyclerView.removeOnScrollListener(this)
            }
        })
    }

    open override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = null
    }

    protected open fun removeLimit() {
        if (limit != NO_LIMIT) {
            val oldLimit = limit
            limit = NO_LIMIT
            val runnable = Runnable {
                notifyItemRangeInserted(oldLimit, getTotalItemCount() - oldLimit)
            }
            val recycler = recyclerView
            if (recycler != null) {
                recycler.post(runnable)
            } else {
                runnable.run()
            }
        }
    }

    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AVH {
        return getHolderSupplier(viewType).get()
    }

    open override fun getItemViewType(position: Int): Int = DEFAULT_VIEW_TYPE

    final override fun onBindViewHolder(holder: AVH, position: Int) {
        (holder as AsyncRecyclerAdapter<*>.AsyncViewHolder).setOnInflatedInternal {
            if (type == InflationType.ASYNC && limit != NO_LIMIT) {
                limit++
                if (limit <= getTotalItemCount()) {
                    val recycler = recyclerView
                    if (recycler != null) {
                        if (recycler.isComputingLayout()) {
                            recycler.post { notifyItemInserted(limit) }
                        } else {
                            notifyItemInserted(limit)
                        }
                    }
                } else {
                    limit = NO_LIMIT
                }
            }

            onBind(holder, position)
        }
    }

    final override fun getItemCount(): Int {
        if (type == InflationType.SYNCED) {
            return getTotalItemCount()
        }

        return if (limit > 0) minOf(limit, getTotalItemCount()) else getTotalItemCount()
    }

    open fun setRecyclerHeightApproximationListener(listener: RecyclerHeightApproximationListener?) {
        this.listener = listener
        approximateRecyclerHeight()
    }

    abstract fun getTotalItemCount(): Int

    protected abstract fun onBind(holder: AVH, position: Int)

    protected abstract fun getLayout(viewType: Int): Int

    protected abstract fun getHolderSupplier(viewType: Int): Supplier<AVH>

    internal fun interface InflationListener {
        fun onInflated()
    }

    fun interface RecyclerHeightApproximationListener {
        fun onNewApproximation(height: Int)
    }

    companion object {
        protected const val DEFAULT_VIEW_TYPE = 0
        private const val NO_LIMIT = -1
        private const val MAX_POOL_SIZE = 20
        private const val UNMEASURED_HEIGHT = -1
        private const val TAG = "AsyncViewHolder"
    }
}
