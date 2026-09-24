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

package com.stario.launcher.activities.launcher.widgets.glance.extensions.calendar

import android.app.ActivityOptions
import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.stario.launcher.R
import com.stario.launcher.activities.launcher.widgets.glance.GlanceViewExtension
import com.stario.launcher.preferences.Vibrations
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.utils.Casing
import java.text.SimpleDateFormat

class Calendar : GlanceViewExtension {
    private var clickListener: View.OnClickListener? = null
    private var month: TextView? = null
    private var date: TextView? = null

    override fun inflate(activity: ThemedActivity, container: LinearLayout): View {
        val root = activity.layoutInflater.inflate(R.layout.calendar, container, false)
        month = root.findViewById(R.id.month)
        date = root.findViewById(R.id.date)

        clickListener = View.OnClickListener {
            Vibrations.getInstance().vibrate()

            try {
                val builder = CalendarContract.CONTENT_URI.buildUpon()
                builder.appendPath("time")
                ContentUris.appendId(builder, java.util.Calendar.getInstance().timeInMillis)
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW).setData(builder.build()),
                    ActivityOptions.makeScaleUpAnimation(container, 0, 0, container.measuredWidth, container.measuredHeight).toBundle()
                )
            } catch (exception: Exception) {
                Log.e("Calendar", "inflate: ", exception)
            }
        }

        return root
    }

    override fun update() {
        val month = month ?: return
        val date = date ?: return
        val time = android.icu.util.Calendar.getInstance().time.time
        val locale = month.textLocale

        month.post {
            month.text = Casing.toTitleCase(SimpleDateFormat("EEEE, ", locale).format(time))
        }
        date.post {
            date.text = Casing.toTitleCase(SimpleDateFormat("MMM\u00A0d", locale).format(time))
        }
    }

    override fun getClickListener(): View.OnClickListener? = clickListener
}
