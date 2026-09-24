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

open class BackGestureEventBus {
    private val listeners = CopyOnWriteArrayList<BackEventListener>()

    open fun addListener(listener: BackEventListener) {
        listeners.add(listener)
    }

    open fun removeListener(listener: BackEventListener?) {
        listeners.remove(listener)
    }

    open fun postEvent(event: BackEvent) {
        for (listener in listeners) {
            if (listener.accepts(event)) {
                listener.onBackEvent(event)
            }
        }
    }

    abstract class BackEventListener {
        private val origin: Class<*>?

        constructor() {
            origin = null
        }

        constructor(origin: Class<*>?) {
            this.origin = origin
        }

        @JvmSynthetic
        internal fun accepts(event: BackEvent): Boolean = origin == null || event.origin === origin

        abstract fun onBackEvent(event: BackEvent)
    }

    companion object {
        private val INSTANCE = BackGestureEventBus()

        @JvmStatic
        fun getInstance(): BackGestureEventBus = INSTANCE
    }
}
