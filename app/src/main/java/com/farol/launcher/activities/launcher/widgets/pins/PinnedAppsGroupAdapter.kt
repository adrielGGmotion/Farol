/*
 * Copyright (C) 2026 Răzvan Albu
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

package com.farol.launcher.activities.launcher.widgets.pins

import android.annotation.SuppressLint
import com.farol.launcher.R
import com.farol.launcher.apps.Category
import com.farol.launcher.apps.LauncherApplication
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.ui.icons.AdaptiveIconView
import com.farol.launcher.ui.recyclers.async.AsyncRecyclerAdapter
import com.farol.launcher.ui.recyclers.async.InflationType
import java.util.function.Supplier

internal open class PinnedAppsGroupAdapter(
    activity: ThemedActivity,
    private val category: Category?,
    private val startingIndex: Int
) : AsyncRecyclerAdapter<PinnedAppsGroupAdapter.ViewHolder>(activity, InflationType.SYNCED) {
    inner open class ViewHolder : AsyncViewHolder() {
        private var icon: AdaptiveIconView? = null

        @SuppressLint("ClickableViewAccessibility")
        override fun onInflated() {
            icon = itemView.findViewById(R.id.icon)
        }

        fun setApplication(application: LauncherApplication) {
            icon?.setApplication(application)
        }
    }

    override fun onBind(holder: ViewHolder, position: Int) {
        holder.setApplication(getApplication(position))
    }

    override fun getLayout(viewType: Int): Int = R.layout.pinned_application_group_item

    override fun getHolderSupplier(viewType: Int): Supplier<ViewHolder> = Supplier { ViewHolder() }

    protected open fun getApplication(index: Int): LauncherApplication {
        return category?.get(index + startingIndex) ?: fallbackApplication()
    }

    override fun getTotalItemCount(): Int {
        return Math.min(category?.let { Math.max(0, it.getSize() - startingIndex) } ?: 0, 4)
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        private fun <T> unchecked(value: T?): T = value as T

        private fun fallbackApplication(): LauncherApplication = unchecked(LauncherApplication.FALLBACK_APP)
    }
}
