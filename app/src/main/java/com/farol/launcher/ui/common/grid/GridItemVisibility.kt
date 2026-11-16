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

package com.farol.launcher.ui.common.grid

import com.farol.launcher.ui.utils.UiUtils

/** Keeps [item] in [container] while [shouldBeVisible] is true. Changes are posted to the UI thread. */
class GridItemVisibility(
    private val container: DynamicGridLayout,
    private val item: DraggableGridItem,
    private val layoutData: DynamicGridLayout.ItemLayoutData
) {
    private var isAttached = false

    fun update(shouldBeVisible: Boolean) {
        UiUtils.post {
            if (shouldBeVisible && !isAttached) {
                container.addItem(item, layoutData)
                isAttached = true
            } else if (!shouldBeVisible && isAttached) {
                container.removeItem(item)
                isAttached = false
            }
        }
    }
}
