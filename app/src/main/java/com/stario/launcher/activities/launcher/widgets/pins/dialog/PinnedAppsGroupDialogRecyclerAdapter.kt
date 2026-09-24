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

package com.stario.launcher.activities.launcher.widgets.pins.dialog

import android.annotation.SuppressLint
import com.stario.launcher.apps.Category
import com.stario.launcher.apps.LauncherApplication
import com.stario.launcher.sheet.drawer.RecyclerApplicationAdapter
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.recyclers.async.InflationType
import java.util.Collections

open class PinnedAppsGroupDialogRecyclerAdapter(activity: ThemedActivity) :
    RecyclerApplicationAdapter(activity, InflationType.SYNCED) {
    private var applications: List<LauncherApplication> = Collections.emptyList()

    override fun getApplication(index: Int): LauncherApplication {
        if (index >= 0 && index < applications.size) {
            return applications[index]
        }
        return fallbackApplication()
    }

    override fun allowApplicationStateEditing(): Boolean = true

    override fun getTotalItemCount(): Int = applications.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateDataSnapshot(category: Category?, skip: Int) {
        applications = if (category == null) {
            Collections.emptyList()
        } else {
            val snapshot = ArrayList<LauncherApplication>()
            for (index in skip until category.getSize()) {
                snapshot.add(category.get(index) ?: fallbackApplication())
            }
            snapshot
        }

        notifyDataSetChanged()
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        private fun <T> unchecked(value: T?): T = value as T

        private fun fallbackApplication(): LauncherApplication = unchecked(LauncherApplication.FALLBACK_APP)
    }
}
