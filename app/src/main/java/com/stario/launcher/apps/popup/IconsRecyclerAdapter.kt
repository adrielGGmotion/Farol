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

package com.stario.launcher.apps.popup

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.stario.launcher.R
import com.stario.launcher.apps.IconPackManager
import com.stario.launcher.apps.LauncherApplication
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.icons.AdaptiveIconView
import com.stario.launcher.ui.utils.UiUtils
import java.util.ArrayList

open class IconsRecyclerAdapter(
    private val activity: ThemedActivity,
    private val application: LauncherApplication,
    private val listener: View.OnClickListener?
) : RecyclerView.Adapter<IconsRecyclerAdapter.ViewHolder>() {
    private val manager = IconPackManager.from(activity)
    private var icons: List<Pair<IconPackManager.IconPack?, Pair<String?, Drawable?>>>? = ArrayList()

    init {
        manager.getIcons(application).thenAccept { iconList ->
            icons = iconList
            UiUtils.post(this::notifyDataSetChanged)
        }
    }

    open class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        internal val icon: AdaptiveIconView = itemView.findViewById(R.id.icon)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val icons = icons ?: return
        val item = icons[position]

        viewHolder.icon.setIcon(item.second.second)
        viewHolder.itemView.setOnClickListener { view ->
            manager.setIconPackPreference(
                application.getInfo().packageName,
                item.first,
                item.second.first
            )

            listener?.onClick(view)
        }
    }

    override fun getItemCount(): Int = icons?.size ?: 0

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(activity).inflate(R.layout.pop_up_icon_item, container, false)
        )
    }
}
