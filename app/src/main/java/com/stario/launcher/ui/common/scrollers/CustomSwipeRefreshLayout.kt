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

package com.stario.launcher.ui.common.scrollers

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class CustomSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SwipeRefreshLayout(context, attrs) {
    private var engageListener: OnEngageListener? = null
    private var pullingOrSettling = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (isRefreshing || pullingOrSettling) return true
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_MOVE -> if (scrollY < 0 && !pullingOrSettling) {
                pullingOrSettling = true
                engageListener?.onEngaged(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (pullingOrSettling) {
                pullingOrSettling = false
                engageListener?.onEngaged(false)
            }
        }
        if (pullingOrSettling) return true
        return super.onTouchEvent(event)
    }

    override fun setRefreshing(refreshing: Boolean) {
        super.setRefreshing(refreshing)
        if (pullingOrSettling != refreshing) {
            pullingOrSettling = refreshing
            engageListener?.onEngaged(pullingOrSettling)
        }
    }

    fun setOnEngageListener(listener: OnEngageListener?) {
        engageListener = listener
    }

    fun interface OnEngageListener {
        fun onEngaged(engaged: Boolean)
    }
}
