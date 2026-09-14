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

package com.stario.launcher.sheet.widgets

import com.google.gson.annotations.SerializedName
import com.stario.launcher.utils.Utils

class Widget(
    @SerializedName("size")
    @JvmField var size: WidgetSize?,
    @SerializedName("id")
    @JvmField val id: Int,
    @SerializedName("position")
    @JvmField var position: Int,
) : Comparable<Widget> {
    constructor(id: Int, position: Int, size: WidgetSize) : this(size, id, position)

    companion object {
        @JvmStatic
        fun deserialize(data: String): Widget? {
            return try {
                val holder = Utils.getGsonInstance().fromJson(data, Widget::class.java)
                if (holder.size == null || holder.id == -1 || holder.position == -1) null else holder
            } catch (_: Exception) {
                null
            }
        }
    }

    fun serialize(): String = Utils.getGsonInstance().toJson(this)

    override fun equals(other: Any?): Boolean = other is Widget && other.id == id

    override fun compareTo(other: Widget): Int = position - other.position
}
