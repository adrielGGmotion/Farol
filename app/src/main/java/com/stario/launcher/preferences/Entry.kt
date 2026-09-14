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

class Entry private constructor(private val name: String) {
    private val serialized = "com.stario.$name"

    override fun toString(): String = serialized

    fun toSubPreference(name: String): String = "$this.$name"

    companion object {
        val CATEGORY_APPLICATION_MAP = Entry("CATEGORY_APPLICATION_MAP")
        val GRID_TEMPLATE_MANAGER = Entry("GRID_TEMPLATE_MANAGER")
        val APPLICATION_LABELS = Entry("APPLICATION_LABELS")
        val PINNED_CATEGORY = Entry("PINNED_CATEGORY")
        val CATEGORY_NAMES = Entry("CATEGORY_NAMES")
        val CATEGORY_MAP = Entry("CATEGORY_MAP")
        val HIDDEN_APPS = Entry("HIDDEN_APPS")
        val CATEGORIES = Entry("CATEGORIES")
        val BRIEFING = Entry("BRIEFING")
        val WEATHER = Entry("WEATHER")
        val WIDGETS = Entry("WIDGETS")
        val SEARCH = Entry("SEARCH")
        val STARIO = Entry("STARIO")
        val DRAWER = Entry("DRAWER")
        val SHEET = Entry("SHEET")
        val THEME = Entry("THEME")
        val ICONS = Entry("ICONS")
        val CLOCK = Entry("CLOCK")

        @JvmStatic
        fun isValid(serialized: String?): Boolean {
            return !serialized.isNullOrEmpty() && listOf(
                CATEGORY_APPLICATION_MAP,
                GRID_TEMPLATE_MANAGER,
                APPLICATION_LABELS,
                PINNED_CATEGORY,
                CATEGORY_NAMES,
                CATEGORY_MAP,
                HIDDEN_APPS,
                CATEGORIES,
                BRIEFING,
                WEATHER,
                WIDGETS,
                SEARCH,
                STARIO,
                DRAWER,
                SHEET,
                THEME,
                ICONS,
                CLOCK,
            ).any { serialized.startsWith(it.serialized) }
        }
    }
}
