/*
 * Copyright (C) 2026 Răzvan Albu
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

package com.stario.launcher.ui.common.text

import android.content.Context
import android.text.Layout
import android.text.Spanned
import android.text.style.ClickableSpan
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.appcompat.widget.AppCompatTextView

class ClickableSpanTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatTextView(context, attrs, defStyleAttr), View.OnTouchListener {
    private var spanClickListener: OnSpanClickListener? = null
    private var longPressTriggered = false
    private val longPressRunnable = Runnable { longPressTriggered = true }
    private var pressedSpan: ClickableSpan? = null
    private val moveSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f

    init {
        super.setOnTouchListener(this)
    }

    override fun setOnTouchListener(listener: View.OnTouchListener?) {
        throw RuntimeException("ClickableSpanTextView cannot set a touch listener.")
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val textValue = text
        if (textValue !is Spanned) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pressedSpan = findSpan(event, textValue)
                pressedSpan?.let {
                    downX = event.x
                    downY = event.y
                    longPressTriggered = false
                    postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (pressedSpan == null) return false
                if (kotlin.math.abs(event.x - downX) > moveSlop ||
                    kotlin.math.abs(event.y - downY) > moveSlop
                ) cancelPressedSpan()
            }
            MotionEvent.ACTION_CANCEL -> cancelPressedSpan()
            MotionEvent.ACTION_UP -> {
                val span = pressedSpan
                if (span != null && !longPressTriggered) {
                    spanClickListener?.let {
                        it.onSpanClick(this, span)
                        cancelPressedSpan()
                        return true
                    }
                }
                cancelPressedSpan()
            }
        }
        return false
    }

    private fun cancelPressedSpan() {
        removeCallbacks(longPressRunnable)
        pressedSpan = null
        longPressTriggered = false
    }

    private fun findSpan(event: MotionEvent, spannable: Spanned): ClickableSpan? {
        val x = event.x.toInt() - totalPaddingLeft + scrollX
        val y = event.y.toInt() - totalPaddingTop + scrollY
        val textLayout: Layout = layout ?: return null
        val line = textLayout.getLineForVertical(y)
        val offset = textLayout.getOffsetForHorizontal(line, x.toFloat())
        return spannable.getSpans(offset, offset, ClickableSpan::class.java).firstOrNull()
    }

    fun setOnSpanClickListener(listener: OnSpanClickListener?) {
        spanClickListener = listener
    }

    fun interface OnSpanClickListener {
        fun onSpanClick(view: ClickableSpanTextView, span: ClickableSpan)
    }
}
