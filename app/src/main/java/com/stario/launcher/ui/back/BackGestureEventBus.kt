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

package com.stario.launcher.ui.back

import java.util.concurrent.CopyOnWriteArrayList

class BackGestureEventBus private constructor() {
    private val listeners = CopyOnWriteArrayList<BackEventListener>()

    fun addListener(listener: BackEventListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: BackEventListener) {
        listeners.remove(listener)
    }

    fun postEvent(event: BackEvent) {
        listeners.forEach { listener ->
            if (listener.origin == null || event.origin == listener.origin) {
                listener.onBackEvent(event)
            }
        }
    }

    abstract class BackEventListener @JvmOverloads constructor(
        internal val origin: Class<*>? = null,
    ) {
        abstract fun onBackEvent(event: BackEvent)
    }

    companion object {
        private val INSTANCE = BackGestureEventBus()

        @JvmStatic
        fun getInstance(): BackGestureEventBus = INSTANCE
    }
}
