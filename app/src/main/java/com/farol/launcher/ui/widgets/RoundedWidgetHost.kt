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

package com.farol.launcher.ui.widgets

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.content.ContextWrapper
import android.view.LayoutInflater
import android.view.ViewGroup
import com.farol.launcher.ui.utils.UiUtils

// RemoteViews inflates with the host context's LayoutInflater. An AppCompat activity's inflater swaps
// ImageView for AppCompatImageView, which RemoteViews rejects ("can't use method with RemoteViews"),
// so widgets render "Couldn't add widget". The application inflater has no such factory.
private fun withPlainInflater(context: Context): Context = object : ContextWrapper(context) {
    private val inflater by lazy { LayoutInflater.from(applicationContext).cloneInContext(this) }

    override fun getSystemService(name: String): Any? =
        if (name == LAYOUT_INFLATER_SERVICE) inflater else super.getSystemService(name)
}

open class RoundedWidgetHost : AppWidgetHostView {
    constructor(context: Context) : super(withPlainInflater(context)) {
        setClipChildren(true)
        setClipToOutline(true)
    }

    constructor(context: Context, params: ViewGroup.LayoutParams) : super(withPlainInflater(context)) {
        setLayoutParams(params)
        setPadding(0, 0, 0, 0)
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        super.setPadding(0, 0, 0, 0)
    }

    override fun setPaddingRelative(start: Int, top: Int, end: Int, bottom: Int) {
        super.setPaddingRelative(0, 0, 0, 0)
    }

    protected override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        UiUtils.roundViewGroup(this, RADIUS_DP)
    }

    companion object {
        const val RADIUS_DP = 20
    }
}
