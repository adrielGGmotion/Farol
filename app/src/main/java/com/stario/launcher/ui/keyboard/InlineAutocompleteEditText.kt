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

package com.stario.launcher.ui.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.appcompat.widget.AppCompatEditText
import com.stario.launcher.ui.utils.UiUtils

open class InlineAutocompleteEditText : AppCompatEditText {
    private var autocompleteProviderValue: AutocompleteProvider? = null
    private var autocompleted = false

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        autocompleted = false
    }

    open fun setAutocompleteProvider(provider: AutocompleteProvider?) {
        autocompleteProviderValue = provider
    }

    open fun getAutocompleteProvider(): AutocompleteProvider? {
        return autocompleteProviderValue
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            val text = getText()

            if (text != null && getSelectionEnd() == text.length) {
                setSelection(text.length)
            }

            UiUtils.hideKeyboard(this)
        }

        return super.onKeyUp(keyCode, event)
    }

    @SuppressLint("SetTextI18n")
    protected override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        val provider = autocompleteProviderValue
        if (provider != null && text != null) {
            val textLength = text.length

            if (textLength > 0 && !autocompleted &&
                getSelectionEnd() == textLength &&
                ((lengthBefore > 0 && lengthAfter > 0) ||
                        lengthBefore < lengthAfter)
            ) {
                val autocompletion = provider.autocomplete(text.toString())

                if (autocompletion != null) {
                    autocompleted = true

                    super.setText(text.toString() + autocompletion)

                    setSelection(textLength, textLength + autocompletion.length)
                }
            } else if (autocompleted) {
                autocompleted = false
            }
        } else if (autocompleted) {
            autocompleted = false
        }
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        autocompleted = true

        super.setText(text, type)
    }

    fun interface AutocompleteProvider {
        /**
         * Returns an autocompleted suggestion for the given input string.
         *
         * If a valid autocomplete suggestion is found based on the input, the suggestion
         * returns the remaining suggestion characters as a string. If no suggestion is available,
         * this method returns `null`.
         *
         * @param input the input string to be autocompleted
         * @return the autocompleted suggestion, or `null` if no suggestion is available
         */
        fun autocomplete(input: String): String?
    }
}
