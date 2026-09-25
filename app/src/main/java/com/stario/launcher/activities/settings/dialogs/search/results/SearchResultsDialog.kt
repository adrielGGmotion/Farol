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

package com.stario.launcher.activities.settings.dialogs.search.results

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.materialswitch.MaterialSwitch
import com.stario.launcher.R
import com.stario.launcher.preferences.Entry
import com.stario.launcher.sheet.drawer.search.recyclers.adapters.WebAdapter
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.dialogs.ActionDialog
import androidx.core.view.isEmpty
import androidx.core.view.isNotEmpty
import androidx.core.content.edit

open class SearchResultsDialog(activity: ThemedActivity) : ActionDialog(activity) {
    private val preferences: SharedPreferences = activity.getApplicationContext()
        .getSharedPreferences(Entry.SEARCH)

    private lateinit var resultsSwitch: MaterialSwitch
    private var listener: StatusListener? = null

    override fun inflateContent(inflater: LayoutInflater, parent: android.view.ViewGroup): View {
        val root = inflater.inflate(R.layout.pop_up_kagi, parent, false)

        resultsSwitch = root.findViewById(R.id.search_results)
        resultsSwitch.setOnCheckedChangeListener { button, isChecked ->
            val key = preferences.getString(WebAdapter.KAGI_API_KEY, null)

            if (key == null || key.isEmpty()) {
                button.setChecked(false)
            } else {
                listener?.onChanged(isChecked)
            }
        }

        root.findViewById<View>(R.id.search_results_container)
            .setOnClickListener { resultsSwitch.performClick() }

        val editText = root.findViewById<EditText>(R.id.edit_text)
        editText.setText(preferences.getString(WebAdapter.KAGI_API_KEY, ""))
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(content: Editable?) {
                preferences.edit {
                        putString(WebAdapter.KAGI_API_KEY, content!!.toString())
                    }
            }
        })

        root.findViewById<View>(R.id.paste).setOnClickListener {
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            if (clipboard.hasPrimaryClip()) {
                val clip: ClipData? = clipboard.getPrimaryClip()

                if (clip != null && clip.getDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                    val text = clip.getItemAt(0).coerceToText(activity).toString()

                    if (text.isNotEmpty()) {
                        editText.setText(text)
                        editText.setSelection(text.length)
                    }
                }
            }
        }

        return root
    }

    override fun show() {
        super.show()

        resultsSwitch.setChecked(preferences.getBoolean(WebAdapter.SEARCH_RESULTS, false))
        resultsSwitch.jumpDrawablesToCurrentState()
    }

    open fun setStatusListener(listener: StatusListener?) {
        this.listener = listener
    }

    override fun blurBehind(): Boolean = true

    override fun getDesiredInitialState(): Int = BottomSheetBehavior.STATE_EXPANDED

    fun interface StatusListener {
        fun onChanged(enabled: Boolean)
    }
}
