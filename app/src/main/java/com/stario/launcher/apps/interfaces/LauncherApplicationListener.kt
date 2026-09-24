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

package com.stario.launcher.apps.interfaces

import com.stario.launcher.apps.LauncherApplication

interface LauncherApplicationListener {
    fun onInserted(application: LauncherApplication) {
    }

    fun onShowed(application: LauncherApplication) {
    }

    /**
     * This will always be called before and in the same UI frame as {@link #onRemoved(LauncherApplication)}
     */
    fun onPrepareRemoval() {
    }

    /**
     * This will always be called after and in the same UI frame as {@link #onPrepareRemoval()}
     */
    fun onRemoved(application: LauncherApplication) {
    }

    /**
     * This will always be called before and in the same UI frame as {@link #onHidden(LauncherApplication)}
     */
    fun onPrepareHiding() {
    }

    /**
     * This will always be called after and in the same UI frame as {@link #onPrepareHiding()}
     */
    fun onHidden(application: LauncherApplication) {
    }

    fun onUpdated(application: LauncherApplication) {
    }
}
