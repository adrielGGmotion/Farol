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

package com.farol.launcher.activities.settings.dialogs.pin

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.farol.launcher.R
import com.farol.launcher.activities.launcher.widgets.pins.PinnedCategory
import com.farol.launcher.apps.CategoryManager
import com.farol.launcher.preferences.Entry
import com.farol.launcher.themes.ThemedActivity
import androidx.core.view.size
import androidx.core.content.edit

internal class PinnedCategoryRecyclerAdapter(
    private val activity: ThemedActivity,
    private val listener: View.OnClickListener?
) : RecyclerView.Adapter<PinnedCategoryRecyclerAdapter.ViewHolder>() {
    private val categoryManager: CategoryManager = CategoryManager.getInstance()
    private val settings: SharedPreferences = activity.getApplicationContext().getSharedPreferences(Entry.PINNED_CATEGORY)
    private val inflater: LayoutInflater = LayoutInflater.from(activity)

    open inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        internal val recyclerView: RecyclerView = itemView.findViewById(R.id.recycler)
        internal val label: TextView = itemView.findViewById(R.id.label)

        init {
            recyclerView.setLayoutManager(LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, true))
            recyclerView.setItemAnimator(null)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val category = categoryManager.get(position)

        viewHolder.label.setText(CategoryManager.getInstance().getCategoryName(category.identifier))
        viewHolder.recyclerView.setAdapter(PinnedCategoryApplicationRecyclerAdapter(activity, category))

        viewHolder.itemView.setOnClickListener { view ->
            settings.edit {
                    putString(PinnedCategory.PINNED_CATEGORY, category.identifier.toString())
                }
            listener?.onClick(view)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.recyclerView.setAdapter(null)
    }

    override fun getItemCount(): Int = categoryManager.size()

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(inflater.inflate(R.layout.category_item, container, false))
    }
}
