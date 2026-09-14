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

package com.stario.launcher.ui.common.tabs

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.viewpager.widget.ViewPager
import com.ogaclejapan.smarttablayout.SmartTabLayout
import com.stario.launcher.R
import com.stario.launcher.ui.utils.UiUtils

class CenterTabLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : SmartTabLayout(context, attrs, defStyle) {
    private var listener: OnLongClickTabListener? = null
    private val inflater: LayoutInflater = UiUtils.unwrapContext(context).getLayoutInflater()
    private var viewPager: ViewPager? = null

    init {
        setCustomTabView { viewGroup, position, adapter ->
            val textView = inflater.inflate(R.layout.tab, viewGroup, false) as TextView
            textView.text = adapter.getPageTitle(position)
            textView.setOnClickListener { viewPager?.currentItem = position }
            textView.setOnLongClickListener { view ->
                listener?.onLongClick(view, position)
                true
            }
            textView
        }
    }

    override fun setViewPager(viewPager: ViewPager) {
        this.viewPager = viewPager
        super.setViewPager(viewPager)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = false

    fun setOnTabLongClickListener(listener: OnLongClickTabListener?) {
        this.listener = listener
    }

    fun interface OnLongClickTabListener {
        fun onLongClick(tab: View, position: Int)
    }
}
