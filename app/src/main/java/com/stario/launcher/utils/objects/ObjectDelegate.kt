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

class ObjectDelegate<T> {
    private val action: ObjectDelegateAction<T>
    private var value: T?

    constructor(action: ObjectDelegateAction<T>) {
        this.value = null
        this.action = action
    }

    constructor(value: T?, action: ObjectDelegateAction<T>) {
        this.value = value
        this.action = action
    }

    fun getValue(): T? = value

    fun setValue(value: T?) {
        this.value = value
        action.onSet(value)
    }

    fun interface ObjectDelegateAction<T> {
        fun onSet(value: T?)
    }
}
