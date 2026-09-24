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

package com.stario.launcher.sheet.drawer.category.list

import android.annotation.SuppressLint
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.stario.launcher.R
import com.stario.launcher.apps.Category
import com.stario.launcher.apps.LauncherApplication
import com.stario.launcher.sheet.drawer.RecyclerApplicationAdapter
import com.stario.launcher.themes.ThemedActivity
import java.util.function.Supplier

open class FolderListItemAdapter(activity: ThemedActivity) : RecyclerApplicationAdapter(activity) {
    companion object {
        const val SOFT_LIMIT = 3
        const val HARD_LIMIT = 5
    }

    private var listener: Category.CategoryItemListener? = null
    private var recyclerView: RecyclerView? = null
    private var category: Category? = null

    @SuppressLint("NotifyDataSetChanged")
    open fun setCategory(category: Category) {
        if (this.category !== category) {
            listener?.let { this.category?.removeCategoryItemListener(it) }
            this.category = category
            notifyDataSetChanged()
            listener = object : Category.CategoryItemListener {
                override fun onInserted(application: LauncherApplication) {
                    recyclerView?.post { notifyDataSetChanged() }
                }
                override fun onRemoved(application: LauncherApplication) {
                    recyclerView?.post { notifyDataSetChanged() }
                }
                override fun onUpdated(application: LauncherApplication) {
                    recyclerView?.post { notifyDataSetChanged() }
                }
                override fun onSwapped(index1: Int, index2: Int) {
                    recyclerView?.post { notifyDataSetChanged() }
                }
            }
            category.addCategoryItemListener(listener)
        }
    }

    protected inner class FolderItemViewHolder : ApplicationViewHolder() {
        override fun getOnClickListener(): View.OnClickListener = View.OnClickListener { view ->
            val position = absoluteAdapterPosition
            if (position == RecyclerView.NO_POSITION) return@OnClickListener
            if (position >= SOFT_LIMIT && requireNotNull(category).getSize() >= HARD_LIMIT) {
                var folder = itemView.parent as View
                while (folder.parent !is RecyclerView) folder = folder.parent as View
                folder.callOnClick()
            } else {
                super.getOnClickListener().onClick(view)
            }
        }

        override fun getOnLongClickListener(): View.OnLongClickListener? = null
    }

    override fun getHolderSupplier(viewType: Int): Supplier<ApplicationViewHolder> = Supplier { FolderItemViewHolder() }
    override fun getLayout(viewType: Int): Int = R.layout.folder_item
    override fun getApplication(index: Int): LauncherApplication? = category?.get(index) ?: LauncherApplication.FALLBACK_APP
    override fun allowApplicationStateEditing(): Boolean = false
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        if (category != null && listener != null) category?.removeCategoryItemListener(listener)
        this.recyclerView = null
    }
    override fun getTotalItemCount(): Int = category?.let { minOf(it.getSize(), HARD_LIMIT) } ?: 0
}
