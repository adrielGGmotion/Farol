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

package com.farol.launcher.activities.launcher.widgets.glance.extensions.media

import android.view.View
import android.widget.LinearLayout
import com.farol.launcher.R
import com.farol.launcher.activities.launcher.widgets.glance.GlanceViewExtension
import com.farol.launcher.themes.ThemedActivity

class MediaPreview : GlanceViewExtension {
    private var enabled = false
    private var root: View? = null

    override fun inflate(activity: ThemedActivity, container: LinearLayout): View {
        root = activity.layoutInflater.inflate(R.layout.media_preview, container, false)
        return root!!
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        update()
    }

    override fun update() {
        root?.visibility = if (enabled) View.VISIBLE else View.GONE
    }
}
