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

import com.farol.launcher.apps.Category
import com.farol.launcher.apps.LauncherApplication
import com.farol.launcher.sheet.drawer.RecyclerApplicationAdapter
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.ui.Measurements
import com.farol.launcher.ui.icons.AdaptiveIconView
import com.farol.launcher.ui.recyclers.async.InflationType
import java.util.function.Supplier

open class PinnedCategoryApplicationRecyclerAdapter(
    activity: ThemedActivity,
    private val category: Category?
) : RecyclerApplicationAdapter(activity, false, InflationType.SYNCED) {
    open inner class PinnedCategoryApplicationViewHolder(viewType: Int) : ApplicationViewHolder(viewType) {
        override fun onInflated() {
            itemView.getLayoutParams().width = AdaptiveIconView.getMaxIconSize() + Measurements.getDefaultPadding()
            super.onInflated()
        }
    }

    override fun getHolderSupplier(viewType: Int): Supplier<ApplicationViewHolder> {
        return Supplier { PinnedCategoryApplicationViewHolder(viewType) }
    }

    override fun getApplication(index: Int): LauncherApplication {
        return (category?.get(index) ?: LauncherApplication.FALLBACK_APP)!!
    }

    override fun getTotalItemCount(): Int = category?.getSize() ?: 0

    override fun allowApplicationStateEditing(): Boolean = false
}
