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

package com.stario.launcher.sheet.drawer.search.recyclers.adapters.suggestions

import android.annotation.SuppressLint
import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.stario.launcher.R
import com.stario.launcher.sheet.drawer.search.recyclers.adapters.AbstractSearchListAdapter
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.icons.AdaptiveIconView

abstract class SuggestionSearchAdapter(activity: ThemedActivity, private val hasLinkArrow: Boolean) :
    AbstractSearchListAdapter<SuggestionSearchAdapter.ViewHolder>() {
    private val activity: Activity = activity

    open inner class ViewHolder @SuppressLint("ClickableViewAccessibility") constructor(item: ViewGroup) : RecyclerView.ViewHolder(item) {
        val label: TextView = item.findViewById(R.id.textView)
        val icon: AdaptiveIconView = item.findViewById(R.id.icon)

        init {
            if (!hasLinkArrow) item.findViewById<View>(R.id.target_arrow).visibility = View.GONE
            item.isHapticFeedbackEnabled = false
        }
    }

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(activity).inflate(R.layout.suggestion_item, container, false) as ViewGroup)
}
