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

package com.farol.launcher.activities.settings.dialogs.theme

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.materialswitch.MaterialSwitch
import com.farol.launcher.R
import com.farol.launcher.preferences.Entry
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.ui.dialogs.ActionDialog
import androidx.core.content.edit

open class ThemeDialog(activity: ThemedActivity) : ActionDialog(activity) {
    private var listener: OnDismissListener? = null
    private var recreateActivity = false

    @SuppressLint("ClickableViewAccessibility")
    override fun inflateContent(inflater: LayoutInflater, parent: android.view.ViewGroup): View {
        val themePreferences: SharedPreferences = activity.getApplicationContext()
            .getSharedPreferences(Entry.THEME)
        val root = inflater.inflate(R.layout.pop_up_theme, parent, false)

        val isForceDarkOn = themePreferences.getBoolean(ThemedActivity.FORCE_DARK, false)

        val materialSwitch = root.findViewById<MaterialSwitch>(R.id.force_dark)
        materialSwitch.setChecked(isForceDarkOn)
        materialSwitch.jumpDrawablesToCurrentState()
        materialSwitch.setOnCheckedChangeListener { _, isChecked ->
            themePreferences.edit {
                    putBoolean(ThemedActivity.FORCE_DARK, isChecked)
                }
        }

        root.findViewById<View>(R.id.force_dark_container)
            .setOnClickListener { materialSwitch.performClick() }

        val recycler = root.findViewById<RecyclerView>(R.id.recycler)
        recycler.setLayoutManager(LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false))
        recycler.setAdapter(ThemeRecyclerAdapter(activity) {
            recreateActivity = true
            dismiss()
        })

        super.setOnDismissListener {
            listener?.onDismiss(recreateActivity || isForceDarkOn != materialSwitch.isChecked)
        }

        return root
    }

    override fun blurBehind(): Boolean = true

    override fun getDesiredInitialState(): Int = BottomSheetBehavior.STATE_EXPANDED

    override fun setOnDismissListener(listener: DialogInterface.OnDismissListener?) {
        throw RuntimeException("Operation not supported by " + javaClass.name)
    }

    open fun setOnDismissListener(listener: OnDismissListener?) {
        this.listener = listener
    }

    fun interface OnDismissListener {
        fun onDismiss(stateChanged: Boolean)
    }
}
