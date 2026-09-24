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

package com.stario.launcher.sheet.drawer.search.recyclers.adapters

import android.annotation.SuppressLint
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.stario.launcher.sheet.drawer.search.Searchable
import com.stario.launcher.sheet.drawer.search.recyclers.OnVisibilityChangeListener

abstract class AbstractSearchListAdapter<V : RecyclerView.ViewHolder> : RecyclerView.Adapter<V>(), Searchable {
    private var listener: OnVisibilityChangeListener? = null
    private var recyclerView: RecyclerView? = null

    fun invalidateRecyclerVisibility() {
        val recycler = recyclerView ?: return
        val visibility = when {
            itemCount == 0 && recycler.visibility != View.GONE -> View.GONE
            itemCount > 0 && recycler.visibility != View.VISIBLE -> View.VISIBLE
            else -> return
        }
        listener?.onPreChange(recycler, visibility)
        recycler.visibility = visibility
        recycler.post { listener?.onChange(recycler, visibility) }
    }

    fun setOnVisibilityChangeListener(listener: OnVisibilityChangeListener?) {
        this.listener = listener
    }

    @SuppressLint("NotifyDataSetChanged")
    fun notifyInternal() {
        val runnable = Runnable {
            notifyDataSetChanged()
            invalidateRecyclerVisibility()
        }
        val recycler = recyclerView
        if (recycler != null && recycler.isAnimating) recycler.post(runnable) else runnable.run()
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
        invalidateRecyclerVisibility()
        super.onAttachedToRecyclerView(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        invalidateRecyclerVisibility()
        this.recyclerView = null
        super.onDetachedFromRecyclerView(recyclerView)
    }
}
