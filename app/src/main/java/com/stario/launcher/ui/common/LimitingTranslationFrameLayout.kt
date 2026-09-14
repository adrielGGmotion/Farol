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

package com.stario.launcher.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView

class LimitingTranslationFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private var layoutChangeListener: View.OnLayoutChangeListener
    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var parentView: View? = null

    init {
        layoutChangeListener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            startX = view.paddingLeft.toFloat()
            startY = view.paddingTop.toFloat()
            endX = (view.width - view.paddingRight).toFloat()
            endY = (view.height - view.paddingBottom).toFloat()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val viewParent: ViewParent? = parent
        if (viewParent is View) {
            parentView = viewParent
            viewParent.addOnLayoutChangeListener(layoutChangeListener)
            layoutChangeListener.onLayoutChange(viewParent, 0, 0, 0, 0, 0, 0, 0, 0)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        parentView?.removeOnLayoutChangeListener(layoutChangeListener)
    }

    override fun setTranslationX(translationX: Float) {
        var value = translationX
        if (value + left < startX) {
            value = startX - left
        } else if (value + right > endX) {
            value = endX - right
        }
        super.setTranslationX(value)
    }

    override fun setTranslationY(translationY: Float) {
        var value = translationY
        val scroll = getParentScroll()
        val range = getParentScrollRange()
        if (value + top + scroll < startY) {
            value = startY - top - scroll
        } else if (value + bottom - (range - scroll) > endY) {
            value = endY - bottom + (range - scroll)
        }
        super.setTranslationY(value)
    }

    private fun getParentScroll(): Int {
        val viewParent = parent
        return when (viewParent) {
            is RecyclerView -> viewParent.computeVerticalScrollOffset()
            is ScrollView -> viewParent.scrollY
            is NestedScrollView -> viewParent.scrollY
            else -> 0
        }
    }

    private fun getParentScrollRange(): Int {
        val viewParent = parent
        return when (viewParent) {
            is RecyclerView -> viewParent.computeVerticalScrollRange() - viewParent.computeVerticalScrollExtent()
            is ScrollView, is NestedScrollView -> {
                val scrollView = viewParent as ViewGroup
                if (scrollView.childCount > 0) {
                    val child = scrollView.getChildAt(0)
                    maxOf(
                        0,
                        child.height - (scrollView.height - scrollView.paddingBottom - scrollView.paddingTop),
                    )
                } else {
                    0
                }
            }
            else -> 0
        }
    }
}
