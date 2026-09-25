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
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.stario.launcher.R
import com.stario.launcher.apps.CategoryManager
import com.stario.launcher.apps.LauncherApplication
import com.stario.launcher.apps.ProfileManager
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.dialogs.ActionDialog
import com.stario.launcher.ui.keyboard.InlineAutocompleteEditText
import java.util.Locale

open class ApplicationCustomizationDialog(
    activity: ThemedActivity,
    private val application: LauncherApplication
) : ActionDialog(activity) {
    private val categoryManager = CategoryManager.getInstance()
    private var category: InlineAutocompleteEditText? = null
    private lateinit var label: EditText

    init {
        setOnDismissListener {
            val manager = ProfileManager.getInstance()
            val newLabel = label.text
            if (newLabel != null) {
                manager.updateLabel(application.getInfo().packageName, newLabel.toString())
            }

            val category = category
            if (category != null) {
                val newCategoryName = category.text

                if (newCategoryName != null &&
                    newCategoryName.toString() != categoryManager.getCategoryName(application.getCategory())
                ) {
                    categoryManager.updateCategory(
                        application,
                        categoryManager.addCustomCategory(newCategoryName.toString())
                    )
                    manager.notifyUpdate(application.getInfo().packageName)
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun inflateContent(inflater: LayoutInflater, parent: android.view.ViewGroup): View {
        val root = inflater.inflate(R.layout.pop_up_customize, parent, false)

        val icons = root.findViewById<RecyclerView>(R.id.icons)
        icons.layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        icons.adapter = IconsRecyclerAdapter(activity, application, View.OnClickListener { dismiss() })

        val labelWarning = root.findViewById<View>(R.id.label_warning)

        label = root.findViewById(R.id.label)
        label.setText(application.getLabel())
        label.isFocusable = true
        label.isFocusableInTouchMode = true
        label.showSoftInputOnFocus = true
        label.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        label.addTextChangedListener(object : TextWatcher {
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
                if (editable != null && editable.length > 0) {
                    if (labelWarning.visibility != View.GONE) {
                        TransitionManager.beginDelayedTransition(root.rootView as ViewGroup, ChangeBounds())
                        labelWarning.visibility = View.GONE
                    }
                } else if (labelWarning.visibility != View.VISIBLE) {
                    TransitionManager.beginDelayedTransition(root.rootView as ViewGroup, ChangeBounds())
                    labelWarning.visibility = View.VISIBLE
                }
            }
        })

        val categoryWarning = root.findViewById<View>(R.id.category_warning)
        val category = root.findViewById<InlineAutocompleteEditText>(R.id.category)
        this.category = category
        category.setText(categoryManager.getCategoryName(application.getCategory()))
        category.isFocusable = true
        category.isFocusableInTouchMode = true
        category.showSoftInputOnFocus = true
        category.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        category.setAutocompleteProvider(object : InlineAutocompleteEditText.AutocompleteProvider {
            override fun autocomplete(input: String): String? {
                val suggestion = categoryManager.getSuggestion(input)

                if (suggestion != null) {
                    return suggestion.substring(
                        suggestion.lowercase(Locale.getDefault())
                            .indexOf(input.lowercase(Locale.getDefault())) + input.length
                    )
                }

                return null
            }
        })
        category.addTextChangedListener(object : TextWatcher {
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
                val text = category.text

                if (text != null) {
                    if (categoryManager.getIdentifier(text.toString()) != null) {
                        if (categoryWarning.visibility != View.GONE) {
                            TransitionManager.beginDelayedTransition(root.rootView as ViewGroup, ChangeBounds())
                            categoryWarning.visibility = View.GONE
                        }
                    } else if (categoryWarning.visibility != View.VISIBLE) {
                        TransitionManager.beginDelayedTransition(root.rootView as ViewGroup, ChangeBounds())
                        categoryWarning.visibility = View.VISIBLE
                    }
                }
            }
        })

        root.findViewById<View>(R.id.reset).setOnClickListener {
            val applicationLabel = application.getInfo()
                .loadLabel(activity.packageManager).toString()

            label.setText(applicationLabel)
            label.setSelection(applicationLabel.length)
        }

        return root
    }

    override fun getDesiredInitialState(): Int = BottomSheetBehavior.STATE_EXPANDED

    override fun blurBehind(): Boolean = true
}
