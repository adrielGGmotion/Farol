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

package com.farol.launcher.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import androidx.core.view.isNotEmpty

open class LimitingTranslationFrameLayout : FrameLayout {
    private lateinit var layoutChangeListener: View.OnLayoutChangeListener
    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var parentView: View? = null

    constructor(context: Context) : super(context) { init() }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { init() }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { init() }

    private fun init() {
        startX = 0f
        startY = 0f
        endX = 0f
        endY = 0f
        parentView = null
        layoutChangeListener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            startX = view.paddingLeft.toFloat()
            startY = view.paddingTop.toFloat()
            endX = (view.width - view.paddingRight).toFloat()
            endY = (view.height - view.paddingBottom).toFloat()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        parentView = parent as? View
        parentView?.let { parent ->
            parent.addOnLayoutChangeListener(layoutChangeListener)
            layoutChangeListener.onLayoutChange(parent, 0, 0, 0, 0, 0, 0, 0, 0)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        parentView?.removeOnLayoutChangeListener(layoutChangeListener)
    }

    override fun setTranslationX(translationX: Float) {
        val bounded = when {
            translationX + left < startX -> startX - left
            translationX + right > endX -> endX - right
            else -> translationX
        }
        super.setTranslationX(bounded)
    }

    override fun setTranslationY(translationY: Float) {
        val scroll = getParentScroll()
        val range = getParentScrollRange()
        val bounded = when {
            translationY + top + scroll < startY -> startY - top - scroll
            translationY + bottom - (range - scroll) > endY -> endY - bottom + (range - scroll)
            else -> translationY
        }
        super.setTranslationY(bounded)
    }

    private fun getParentScroll(): Int = when (val parent = parent) {
        is RecyclerView -> parent.computeVerticalScrollOffset()
        is ScrollView, is NestedScrollView -> (parent as View).scrollY
        else -> 0
    }

    private fun getParentScrollRange(): Int = when (val parent = parent) {
        is RecyclerView -> parent.computeVerticalScrollRange() - parent.computeVerticalScrollExtent()
        is ScrollView, is NestedScrollView -> {
            val scrollView = parent as ViewGroup
            if (scrollView.isNotEmpty()) {
                val child = scrollView.getChildAt(0)
                max(0, child.height - (scrollView.height - scrollView.paddingBottom - scrollView.paddingTop))
            } else 0
        }
        else -> 0
    }
}
