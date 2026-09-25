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

package com.farol.launcher.activities.pages.insert

import android.view.LayoutInflater
import android.view.View
import androidx.core.util.Pair
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.farol.launcher.R
import com.farol.launcher.sheet.SheetDialogFragment
import com.farol.launcher.sheet.SheetType
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.ui.dialogs.ActionDialog
import com.farol.launcher.ui.recyclers.DividerItemDecorator

class InsertPageDialog(activity: ThemedActivity, listener: OnItemSelected) : ActionDialog(activity) {
    private val adapter = InsertPageRecyclerAdapter(activity) { item ->
        listener.onSelect(item)
        dismiss()
    }

    override fun inflateContent(inflater: LayoutInflater, parent: android.view.ViewGroup): View {
        val root = inflater.inflate(R.layout.pop_up_insert_item, parent, false)
        val recycler = root.findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false)
        recycler.addItemDecoration(DividerItemDecorator(activity, MaterialDividerItemDecoration.VERTICAL))
        recycler.adapter = adapter
        return root
    }

    fun setItems(items: List<Pair<SheetType, Class<out SheetDialogFragment>>>) {
        adapter.setItems(items)
    }

    override fun blurBehind() = true

    override fun getDesiredInitialState() = BottomSheetBehavior.STATE_EXPANDED

    fun interface OnItemSelected {
        fun onSelect(item: Pair<SheetType, Class<out SheetDialogFragment>>)
    }
}
