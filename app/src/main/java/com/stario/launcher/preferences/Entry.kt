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

package com.stario.launcher.preferences

enum class Entry(private val serializedName: String) {
    CATEGORY_APPLICATION_MAP("CATEGORY_APPLICATION_MAP"),
    GRID_TEMPLATE_MANAGER("GRID_TEMPLATE_MANAGER"),
    APPLICATION_LABELS("APPLICATION_LABELS"),
    PINNED_CATEGORY("PINNED_CATEGORY"),
    CATEGORY_NAMES("CATEGORY_NAMES"),
    CATEGORY_MAP("CATEGORY_MAP"),
    HIDDEN_APPS("HIDDEN_APPS"),
    CATEGORIES("CATEGORIES"),
    BRIEFING("BRIEFING"),
    WEATHER("WEATHER"),
    WIDGETS("WIDGETS"),
    SEARCH("SEARCH"),
    STARIO("STARIO"),
    DRAWER("DRAWER"),
    SHEET("SHEET"),
    THEME("THEME"),
    ICONS("ICONS"),
    CLOCK("CLOCK");

    private val serialized = "com.stario.$serializedName"

    override fun toString(): String = serialized

    fun toSubPreference(name: String): String = "$this.$name"

    companion object {
        @JvmStatic
        fun isValid(serialized: String?): Boolean {
            return !serialized.isNullOrEmpty() && values().any { serialized.startsWith(it.serialized) }
        }
    }
}
