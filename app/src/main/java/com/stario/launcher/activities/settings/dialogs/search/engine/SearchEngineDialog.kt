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

package com.stario.launcher.activities.settings.dialogs.search.engine

import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.stario.launcher.R
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.dialogs.ActionDialog
import com.stario.launcher.ui.recyclers.DividerItemDecorator

open class SearchEngineDialog(activity: ThemedActivity) : ActionDialog(activity) {
    override fun inflateContent(inflater: LayoutInflater): View {
        val root = inflater.inflate(R.layout.pop_up_engine, null)
        val recycler = root.findViewById<RecyclerView>(R.id.recycler)

        recycler.setLayoutManager(LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false))
        recycler.addItemDecoration(DividerItemDecorator(activity, MaterialDividerItemDecoration.VERTICAL))
        recycler.setAdapter(SearchEngineRecyclerAdapter(activity) { dismiss() })

        return root
    }

    override fun blurBehind(): Boolean = true

    override fun getDesiredInitialState(): Int = BottomSheetBehavior.STATE_EXPANDED
}
