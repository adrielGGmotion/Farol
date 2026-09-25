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

package com.stario.launcher.utils.objects

import java.util.ArrayList
import androidx.core.view.size

open class ObservableObject<A>(initialObject: A?) {
    private val observable = ClosedObservableObject(initialObject)

    constructor(initialObject: A?, listener: OnSet<A>) : this(initialObject) {
        observable.addListener(listener)
    }

    open fun updateObject(updatedObject: A?) {
        observable.updateObject(updatedObject)
    }

    open fun getObject(): A? {
        return observable.getObject()
    }

    open fun addListener(listener: OnSet<A>) {
        observable.addListener(listener)
    }

    open fun removeListeners() {
        observable.removeListeners()
    }

    open fun getListenerCount(): Int {
        return observable.getListenerCount()
    }

    open fun close(): ClosedObservableObject<A> {
        return observable
    }

    open class ClosedObservableObject<B>(initialObject: B?) {
        private var currentObject: B? = initialObject
        private val listeners = ArrayList<OnSet<B>?>()

        internal fun updateObject(updatedObject: B?) {
            if (!((updatedObject != null && updatedObject == currentObject) ||
                    updatedObject === currentObject)
            ) {
                currentObject = updatedObject

                for (listener in listeners) {
                    if (listener != null) {
                        listener.onSet(updatedObject)
                    }
                }
            }
        }

        open fun addListener(listener: OnSet<B>) {
            listeners.add(listener)
        }

        open fun getObject(): B? {
            return currentObject
        }

        open fun removeListeners() {
            listeners.clear()
        }

        open fun getListenerCount(): Int {
            return listeners.size
        }
    }

    fun interface OnSet<G> {
        fun onSet(updatedObject: G?)
    }
}
