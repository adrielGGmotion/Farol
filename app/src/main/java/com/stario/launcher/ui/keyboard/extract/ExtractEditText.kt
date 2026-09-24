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

package com.stario.launcher.ui.keyboard.extract

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.Measurements
import com.stario.launcher.ui.keyboard.InlineAutocompleteEditText
import com.stario.launcher.ui.utils.UiUtils

open class ExtractEditText : InlineAutocompleteEditText {
    private lateinit var extractDialog: ExtractDialog
    private lateinit var manager: FragmentManager

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr) {
        init(context)
    }

    private fun init(context: Context) {
        if (context !is ThemedActivity) {
            throw RuntimeException("ExtractEditText can only be used from a FragmentActivity context.")
        }

        manager = (context as FragmentActivity).supportFragmentManager

        setShowSoftInputOnFocus(false)

        setOnClickListener {
            if (Measurements.isLandscape()) {
                UiUtils.hideKeyboard(this)
                setCursorVisible(false)

                openExtractDialog()
            } else {
                setCursorVisible(true)

                UiUtils.showKeyboard(this)
            }
        }

        extractDialog = ExtractDialog(this)
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        if (focused) {
            if (Measurements.isLandscape()) {
                UiUtils.hideKeyboard(this)
                setCursorVisible(false)

                openExtractDialog()
            } else {
                setCursorVisible(true)

                post { UiUtils.showKeyboard(this) }
            }
        }

        super.onFocusChanged(focused, direction, previouslyFocusedRect)
    }

    override fun extractText(request: ExtractedTextRequest, outText: ExtractedText): Boolean {
        if (Measurements.isLandscape()) {
            UiUtils.hideKeyboard(this)
        }

        return false
    }

    private fun openExtractDialog() {
        if (!extractDialog.isAdded) {
            extractDialog.show(manager, null)
        } else {
            val dialog = extractDialog.dialog

            if (dialog != null) {
                dialog.show()
            }
        }
    }
}
