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

package com.farol.launcher.ui.keyboard.extract

import android.app.Dialog
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.farol.launcher.R
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.ui.Measurements
import com.farol.launcher.ui.keyboard.InlineAutocompleteEditText
import com.farol.launcher.ui.keyboard.KeyboardHeightProvider
import com.farol.launcher.ui.utils.UiUtils

open class ExtractDialog(editText: ExtractEditText) : DialogFragment() {
    private val heightProvider: KeyboardHeightProvider
    private val editText: ExtractEditText = editText
    private val activityValue: ThemedActivity
    private var extractedEditText: InlineAutocompleteEditText? = null
    private var shown = false

    init {
        activityValue = editText.getContext() as ThemedActivity
        heightProvider = KeyboardHeightProvider(activityValue)
        shown = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        var theme = getTheme()

        if (theme == 0) {
            val outValue = TypedValue()

            if (activityValue.getTheme()
                    .resolveAttribute(com.google.android.material.R.attr.bottomSheetDialogTheme, outValue, true)
            ) {
                theme = outValue.resourceId
            } else {
                theme = com.google.android.material.R.style.Theme_Design_Light_BottomSheetDialog
            }
        }

        val dialog = ComponentDialog(activityValue, theme)

        dialog.setOnShowListener {
            val extractedEditText = this.extractedEditText
            if (extractedEditText != null) {
                extractedEditText.setMaxLines(editText.getMaxLines())
                extractedEditText.setImeOptions(editText.getImeOptions() or EditorInfo.IME_FLAG_NO_EXTRACT_UI)
                extractedEditText.setSingleLine(editText.isSingleLine())
                extractedEditText.setHint(editText.getHint())

                val text = editText.getText()

                if (text != null) {
                    extractedEditText.setText(text)
                    extractedEditText.setSelection(text.length)
                }
            }
        }

        return dialog
    }

    override fun onStart() {
        super.onStart()

        val dialog = getDialog()

        if (dialog != null) {
            val window = dialog.getWindow()

            if (window != null) {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

                window.setWindowAnimations(R.style.ExtractedEditTextDialogAnimations)

                UiUtils.makeSysUITransparent(window)
                window.getDecorView().setBackgroundColor(
                    activityValue.getAttributeData(com.google.android.material.R.attr.colorSurface)
                )
            }
        }

        shown = false

        heightProvider.start()
    }

    override fun onStop() {
        super.onStop()

        heightProvider.close()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.extract_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        UiUtils.Notch.applyNotchMargin(view)

        val done = view.findViewById<MaterialButton>(R.id.proceed)
        done.setOnClickListener { dismiss() }

        val extractedEditText = view.findViewById<InlineAutocompleteEditText>(R.id.edit_text)
        this.extractedEditText = extractedEditText
        extractedEditText.setInputType(
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        )
        extractedEditText.setAutocompleteProvider(editText.getAutocompleteProvider())
        extractedEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                if (shown) {
                    editText.setText(s)
                }
            }
        })

        val params = view.getLayoutParams() as ViewGroup.MarginLayoutParams

        Measurements.addStatusBarListener { value ->
            params.topMargin = value!!

            view.requestLayout()
        }

        Measurements.addNavListener { value ->
            params.bottomMargin = value!! + heightProvider.getKeyboardHeight()

            view.requestLayout()
        }

        heightProvider.addKeyboardHeightListener(object : KeyboardHeightProvider.KeyboardHeightListener {
            override fun onKeyboardHeightChanged(height: Int) {
                if (height <= 0) {
                    if (shown) {
                        this@ExtractDialog.dismiss()

                        shown = false
                    }
                } else {
                    shown = true
                }

                params.bottomMargin = height + Measurements.getNavHeight()

                view.requestLayout()
            }
        })

        val showKeyboardRunnable = object : Runnable {
            override fun run() {
                if (!shown) {
                    UiUtils.showKeyboard(extractedEditText)
                    UiUtils.postDelayed(this, 50)
                }
            }
        }
        UiUtils.post(showKeyboardRunnable)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        dismiss()
    }

    override fun dismiss() {
        editText.setText(extractedEditText!!.getText())

        super.dismiss()
    }
}
