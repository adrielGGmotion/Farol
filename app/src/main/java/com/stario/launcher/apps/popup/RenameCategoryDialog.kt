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

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.stario.launcher.R
import com.stario.launcher.apps.CategoryManager
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.dialogs.ActionDialog
import com.stario.launcher.ui.keyboard.extract.ExtractEditText
import java.util.UUID

open class RenameCategoryDialog(
    activity: ThemedActivity,
    private val categoryIdentifier: UUID
) : ActionDialog(activity) {
    private val categoryManager = CategoryManager.getInstance()
    private lateinit var editText: ExtractEditText

    init {
        setOnDismissListener {
            val name = editText.text

            if (name != null) {
                categoryManager.updateCategory(categoryIdentifier, name.toString())
            }
        }
    }

    override fun inflateContent(inflater: LayoutInflater, parent: android.view.ViewGroup): View {
        val root = inflater.inflate(R.layout.pop_up_category, parent, false)
        val initialName = categoryManager.getCategoryName(categoryIdentifier)
        val warning = root.findViewById<View>(R.id.warning)

        editText = root.findViewById(R.id.category)
        editText.setText(initialName)
        editText.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                charSequence: CharSequence?,
                i: Int,
                i1: Int,
                i2: Int
            ) {
            }

            override fun onTextChanged(charSequence: CharSequence?, i: Int, i1: Int, i2: Int) {
            }

            override fun afterTextChanged(editable: Editable?) {
                val text = editText.text

                if (text != null) {
                    if (text.toString().equals(initialName, ignoreCase = true) ||
                        categoryManager.getIdentifier(text.toString(), true) == null
                    ) {
                        if (warning.visibility != View.GONE) {
                            TransitionManager.beginDelayedTransition(root.rootView as ViewGroup, ChangeBounds())
                            warning.visibility = View.GONE
                        }
                    } else if (warning.visibility != View.VISIBLE) {
                        TransitionManager.beginDelayedTransition(root.rootView as ViewGroup, ChangeBounds())
                        warning.visibility = View.VISIBLE
                    }
                }
            }
        })

        root.findViewById<View>(R.id.reset).setOnClickListener {
            editText.setText(initialName)
            editText.setSelection(initialName!!.length)
        }

        return root
    }

    override fun getDesiredInitialState(): Int = BottomSheetBehavior.STATE_EXPANDED

    override fun blurBehind(): Boolean = true
}
