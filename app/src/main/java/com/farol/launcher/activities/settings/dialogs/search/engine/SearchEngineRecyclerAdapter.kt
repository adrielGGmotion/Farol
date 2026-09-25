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

package com.farol.launcher.activities.settings.dialogs.search.engine

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.farol.launcher.R
import com.farol.launcher.sheet.drawer.search.SearchEngine
import com.farol.launcher.themes.ThemedActivity
import androidx.core.view.size

open class SearchEngineRecyclerAdapter(
    private val activity: ThemedActivity,
    private val listener: View.OnClickListener?
) : RecyclerView.Adapter<SearchEngineRecyclerAdapter.ViewHolder>() {
    open class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        internal val icon: ImageView = itemView.findViewById(R.id.icon)
        internal val label: TextView = itemView.findViewById(R.id.label)
        internal val url: TextView = itemView.findViewById(R.id.url)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val engine = SearchEngine.values()[position]

        viewHolder.icon.setImageDrawable(engine.getDrawable(activity))
        viewHolder.label.setText(engine.toString())
        viewHolder.url.setText(engine.getUrl())

        viewHolder.itemView.setOnClickListener { view ->
            SearchEngine.setEngine(activity.getApplicationContext(), engine)
            listener?.onClick(view)
        }
    }

    override fun getItemCount(): Int = SearchEngine.values().size

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(activity).inflate(R.layout.engine_item, container, false))
    }
}
