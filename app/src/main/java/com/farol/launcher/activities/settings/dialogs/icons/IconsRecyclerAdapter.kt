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

package com.farol.launcher.activities.settings.dialogs.icons

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.RecyclerView
import com.farol.launcher.R
import com.farol.launcher.apps.IconPackManager
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.ui.icons.AdaptiveIconView
import com.farol.launcher.ui.utils.UiUtils
import java.util.Locale
import java.util.concurrent.CompletableFuture

open class IconsRecyclerAdapter(
    private val activity: ThemedActivity,
    private val listener: View.OnClickListener?
) : RecyclerView.Adapter<IconsRecyclerAdapter.ViewHolder>() {
    private val manager: IconPackManager = IconPackManager.from(activity)

    open class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        internal val icon: AdaptiveIconView = itemView.findViewById(R.id.icon)
        internal val label: TextView = itemView.findViewById(R.id.label)
        internal val count: TextView = itemView.findViewById(R.id.count)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        if (position < getItemCount() - 1) {
            val pack = manager.getPack(position)

            viewHolder.label.setText(pack.getLabel())
            viewHolder.icon.setIcon(pack.getIcon())

            viewHolder.count.setVisibility(View.VISIBLE)
            viewHolder.count.setText(R.string.calculating)

            val future: CompletableFuture<Int> = pack.getComponentCount()
            future.thenAccept { integer ->
                UiUtils.post {
                    viewHolder.count.setText(
                        activity.getString(R.string.components_count, String.format(Locale.getDefault(), "%,d", integer))
                    )
                }
            }

            viewHolder.itemView.setOnClickListener { view ->
                manager.setActiveIconPack(pack)
                listener?.onClick(view)
            }
        } else {
            viewHolder.label.setText(R.string.default_text)
            viewHolder.icon.setIcon(AppCompatResources.getDrawable(activity, R.mipmap.ic_launcher))

            viewHolder.count.setVisibility(View.GONE)

            viewHolder.itemView.setOnClickListener { view ->
                manager.setActiveIconPack(null)
                listener?.onClick(view)
            }
        }
    }

    override fun getItemCount(): Int = manager.getCount() + 1

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(activity).inflate(R.layout.icon_item, container, false))
    }
}
