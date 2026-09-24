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

package com.stario.launcher.sheet.drawer.category.folder

import android.annotation.SuppressLint
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.stario.launcher.apps.Category
import com.stario.launcher.apps.CategoryManager
import com.stario.launcher.apps.LauncherApplication
import com.stario.launcher.sheet.drawer.RecyclerApplicationAdapter
import com.stario.launcher.sheet.drawer.category.Categories
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.recyclers.async.InflationType
import java.util.UUID

internal class FolderAdapter(activity: ThemedActivity, categoryID: UUID, itemTouchHelper: ItemTouchHelper) :
    RecyclerApplicationAdapter(activity, itemTouchHelper, InflationType.SYNCED) {
    private val category = CategoryManager.getInstance().get(categoryID)
    private var recyclerView: RecyclerView? = null
    private val listener = object : Category.CategoryItemListener {
        private var preparedRemovalIndex = -1
        override fun onInserted(application: LauncherApplication) {
            val recycler = recyclerView
            if (recycler != null && category != null) {
                recycler.post {
                    val index = category.indexOf(application)
                    if (index >= 0) notifyItemInserted(index)
                }
            }
        }

        override fun onPrepareRemoval(application: LauncherApplication) {
            if (category != null) preparedRemovalIndex = category.indexOf(application)
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onRemoved(application: LauncherApplication) {
            val recycler = recyclerView
            if (recycler != null) {
                if (category != null && category.getSize() == 0) {
                    LocalBroadcastManager.getInstance(activity).sendBroadcastSync(Intent(Categories.FOLDER_STACK_ID))
                }
                recycler.post {
                    if (preparedRemovalIndex >= 0) {
                        notifyItemRemoved(preparedRemovalIndex)
                        preparedRemovalIndex = -1
                    } else notifyDataSetChanged()
                }
            }
        }

        override fun onUpdated(application: LauncherApplication) {
            val recycler = recyclerView
            if (recycler != null && category != null) {
                recycler.post {
                    val index = category.indexOf(application)
                    if (index >= 0) notifyItemChanged(index)
                }
            }
        }
    }

    fun move(viewHolder: RecyclerView.ViewHolder, targetHolder: RecyclerView.ViewHolder): Boolean {
        var position = viewHolder.bindingAdapterPosition
        val target = targetHolder.bindingAdapterPosition
        if (position == target || position == RecyclerView.NO_POSITION || target == RecyclerView.NO_POSITION) return false
        while (position - target != 0) {
            val newTarget = position - if (position - target > 0) 1 else -1
            requireNotNull(category).swap(position, newTarget)
            notifyItemMoved(position, newTarget)
            position = newTarget
        }
        return true
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
        category?.addCategoryItemListener(listener)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        category?.removeCategoryItemListener(listener)
        this.recyclerView = null
    }

    override fun getApplication(index: Int): LauncherApplication? = requireNotNull(category).get(index)
    override fun allowApplicationStateEditing(): Boolean = true
    override fun getTotalItemCount(): Int = requireNotNull(category).getSize()
}
