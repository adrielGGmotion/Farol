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

package com.stario.launcher.activities.settings.dialogs.theme

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.stario.launcher.R
import com.stario.launcher.preferences.Entry
import com.stario.launcher.themes.Theme
import com.stario.launcher.themes.ThemedActivity

open class ThemeRecyclerAdapter(
    private val activity: ThemedActivity,
    private val clickListener: View.OnClickListener?
) : RecyclerView.Adapter<ThemeRecyclerAdapter.ViewHolder>() {
    private val themes: Array<Theme> = Theme.values()

    open class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        internal val theme: TextView = itemView.findViewById(R.id.theme)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val theme = themes[position]

        viewHolder.theme.setBackgroundColor(
            activity.getAttributeData(theme, com.google.android.material.R.attr.colorPrimaryContainer)
        )
        viewHolder.theme.setTextColor(
            activity.getAttributeData(theme, com.google.android.material.R.attr.colorOnPrimaryContainer)
        )
        viewHolder.theme.setText(theme.getDisplayName())

        viewHolder.itemView.setOnClickListener { view ->
            activity.getApplicationContext()
                .getSharedPreferences(Entry.THEME).edit()
                .putString(ThemedActivity.THEME, theme.toString())
                .apply()
            clickListener?.onClick(view)
        }
    }

    override fun getItemCount(): Int = themes.size

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(activity).inflate(R.layout.theme_item, container, false))
    }
}
