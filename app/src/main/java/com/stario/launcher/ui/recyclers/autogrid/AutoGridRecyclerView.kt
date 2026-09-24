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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.stario.launcher.ui.recyclers.autogrid

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView
import com.stario.launcher.ui.recyclers.overscroll.OverScrollRecyclerView

open class AutoGridRecyclerView : OverScrollRecyclerView {
    private var managedLayout: RecyclerView.LayoutManager? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    open override fun setLayoutManager(layout: RecyclerView.LayoutManager?) {
        super.setLayoutManager(layout)
        managedLayout = layout
    }

    open override fun setAdapter(adapter: RecyclerView.Adapter<*>?) {
        super.setAdapter(adapter)
        (managedLayout as? AutoGridLayoutManager)?.setAdapter(getAdapter())
    }
}
