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

package com.stario.launcher.activities.settings.dialogs.pin

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.materialswitch.MaterialSwitch
import com.stario.launcher.R
import com.stario.launcher.activities.launcher.widgets.pins.PinnedCategory
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.dialogs.ActionDialog
import com.stario.launcher.ui.recyclers.DividerItemDecorator

open class PinnedCategoryDialog(
    activity: ThemedActivity,
    private val preferences: SharedPreferences,
    private val checkedChangeListener: OnCheckedChangeListener?
) : ActionDialog(activity) {
    @SuppressLint("ClickableViewAccessibility")
    override fun inflateContent(inflater: LayoutInflater): View {
        val root = inflater.inflate(R.layout.pop_up_pinned_category, null)
        val recycler = root.findViewById<RecyclerView>(R.id.recycler)

        val materialSwitch = root.findViewById<MaterialSwitch>(R.id.pinned_category)
        materialSwitch.setChecked(preferences.getBoolean(PinnedCategory.PINNED_CATEGORY_VISIBLE, false))
        materialSwitch.jumpDrawablesToCurrentState()

        materialSwitch.setOnCheckedChangeListener { _, isChecked ->
            val listener = checkedChangeListener
            if (listener != null) {
                val overrideValue = listener.onChecked(isChecked)
                if (overrideValue != isChecked) {
                    materialSwitch.setChecked(overrideValue)
                }
            }
        }

        root.setOnClickListener { materialSwitch.performClick() }

        recycler.setLayoutManager(LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false))
        recycler.addItemDecoration(DividerItemDecorator(activity, MaterialDividerItemDecoration.VERTICAL))
        recycler.setAdapter(PinnedCategoryRecyclerAdapter(activity) {
            if (materialSwitch.isChecked) {
                dismiss()
            } else {
                materialSwitch.setChecked(true)
            }
        })

        return root
    }

    override fun blurBehind(): Boolean = true

    override fun getDesiredInitialState(): Int = BottomSheetBehavior.STATE_EXPANDED

    fun interface OnCheckedChangeListener {
        /**
         * @return checked state override value
         */
        fun onChecked(isChecked: Boolean): Boolean
    }
}
