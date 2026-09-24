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

package com.stario.launcher.ui.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.widget.RelativeLayout
import androidx.constraintlayout.widget.ConstraintLayout

open class WidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
    private val contextValue: Context? = context

    override fun onAppWidgetRemoved(identifier: Int) {
        val intent = Intent(REMOVE_WIDGET)
        intent.putExtra(WIDGET_ID, identifier)

        if (contextValue != null) {
            contextValue.sendBroadcast(intent)
        }

        super.onAppWidgetRemoved(identifier)
    }

    protected override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        val params = RelativeLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )

        return WidgetHostView(context, params)
    }

    companion object {
        const val REMOVE_WIDGET = "com.stario.launcher.LauncherAppWidgetHost.REMOVE_WIDGET"
        const val WIDGET_ID = "com.stario.launcher.LauncherAppWidgetHost.WIDGET_ID"
    }
}
