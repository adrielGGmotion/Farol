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

import com.stario.launcher.ui.recyclers.RecyclerItemAnimator
import com.stario.launcher.ui.utils.animation.Animation

class SearchRecyclerItemAnimator(animation: Animation) : RecyclerItemAnimator(
    RecyclerItemAnimator.DISAPPEARANCE or RecyclerItemAnimator.APPEARANCE,
    animation
) {
    override fun getRemovedAlpha(): Float = 1f
    override fun getRemovedScaleX(): Float = 1f
    override fun getRemovedScaleY(): Float = 1f
}
