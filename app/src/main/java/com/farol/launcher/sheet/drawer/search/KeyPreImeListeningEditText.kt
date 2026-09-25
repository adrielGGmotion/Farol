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

package com.farol.launcher.sheet.drawer.search

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.appcompat.widget.AppCompatEditText

open class KeyPreImeListeningEditText : AppCompatEditText {
    private val listeners = HashMap<Int, OnKeyListener>()

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP && listeners[event.keyCode]?.onKey() == true) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    fun addOnKeyUp(keyCode: Int, listener: OnKeyListener?) {
        if (listener != null) listeners[keyCode] = listener
    }

    fun interface OnKeyListener {
        /** Return true if back event should be intercepted */
        fun onKey(): Boolean
    }
}
