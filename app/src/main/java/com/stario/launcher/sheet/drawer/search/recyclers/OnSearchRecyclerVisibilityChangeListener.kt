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

package com.stario.launcher.sheet.drawer.search.recyclers

import android.view.View
import com.stario.launcher.sheet.drawer.search.SearchLayoutTransition

class OnSearchRecyclerVisibilityChangeListener(
    private val transition: SearchLayoutTransition,
) : OnVisibilityChangeListener {
    override fun onPreChange(view: View, visibility: Int) {
        if (view.visibility != visibility) {
            transition.setAnimate(false)
            transition.cancel()
        }
    }

    override fun onChange(view: View, visibility: Int) {
        transition.setAnimate(true)
    }
}
