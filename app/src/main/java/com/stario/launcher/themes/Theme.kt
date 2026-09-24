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

package com.stario.launcher.themes

import com.stario.launcher.R

enum class Theme(
    private val themeIdentifier: String,
    private val displayNameResource: Int,
    private val lightRes: Int,
    private val darkRes: Int
) {
    THEME_DYNAMIC("com.stario.THEME_DYNAMIC", R.string.dynamic,
        R.style.Theme_Light_Dynamic, R.style.Theme_Dark_Dynamic),
    THEME_MONOCHROME("com.stario.THEME_MONOCHROME", R.string.monochrome,
        R.style.Theme_Light_Monochrome, R.style.Theme_Dark_Monochrome),
    THEME_RED("com.stario.THEME_RED", R.string.red,
        R.style.Theme_Light_Red, R.style.Theme_Dark_Red),
    THEME_ORANGE("com.stario.THEME_ORANGE", R.string.orange,
        R.style.Theme_Light_Orange, R.style.Theme_Dark_Orange),
    THEME_YELLOW("com.stario.THEME_YELLOW", R.string.yellow,
        R.style.Theme_Light_Yellow, R.style.Theme_Dark_Yellow),
    THEME_LIME("com.stario.THEME_LIME", R.string.lime,
        R.style.Theme_Light_Lime, R.style.Theme_Dark_Lime),
    THEME_GREEN("com.stario.THEME_GREEN", R.string.green,
        R.style.Theme_Light_Green, R.style.Theme_Dark_Green),
    THEME_TURQUOISE("com.stario.THEME_TURQUOISE", R.string.turquoise,
        R.style.Theme_Light_Turquoise, R.style.Theme_Dark_Turquoise),
    THEME_CYAN("com.stario.THEME_CYAN", R.string.cyan,
        R.style.Theme_Light_Cyan, R.style.Theme_Dark_Cyan),
    THEME_BLUE("com.stario.THEME_BLUE", R.string.blue,
        R.style.Theme_Light_Blue, R.style.Theme_Dark_Blue),
    THEME_PURPLE("com.stario.THEME_PURPLE", R.string.purple,
        R.style.Theme_Light_Purple, R.style.Theme_Dark_Purple),
    THEME_PINK("com.stario.THEME_PINK", R.string.pink,
        R.style.Theme_Light_Pink, R.style.Theme_Dark_Pink);

    fun getLightResourceID(): Int {
        return lightRes
    }

    fun getDarkResourceID(): Int {
        return darkRes
    }

    fun getDisplayName(): Int {
        return displayNameResource
    }

    override fun toString(): String {
        return themeIdentifier
    }

    companion object {
        // Defaults to THEME_BLUE
        @JvmStatic
        fun from(theme: String?): Theme {
            val themeIdentifier = theme!!

            return if (themeIdentifier == THEME_DYNAMIC.themeIdentifier) {
                THEME_DYNAMIC
            } else if (themeIdentifier == THEME_MONOCHROME.themeIdentifier) {
                THEME_MONOCHROME
            } else if (themeIdentifier == THEME_RED.themeIdentifier) {
                THEME_RED
            } else if (themeIdentifier == THEME_ORANGE.themeIdentifier) {
                THEME_ORANGE
            } else if (themeIdentifier == THEME_YELLOW.themeIdentifier) {
                THEME_YELLOW
            } else if (themeIdentifier == THEME_LIME.themeIdentifier) {
                THEME_LIME
            } else if (themeIdentifier == THEME_GREEN.themeIdentifier) {
                THEME_GREEN
            } else if (themeIdentifier == THEME_TURQUOISE.themeIdentifier) {
                THEME_TURQUOISE
            } else if (themeIdentifier == THEME_CYAN.themeIdentifier) {
                THEME_CYAN
            } else if (themeIdentifier == THEME_PURPLE.themeIdentifier) {
                THEME_PURPLE
            } else if (themeIdentifier == THEME_PINK.themeIdentifier) {
                THEME_PINK
            } else {
                THEME_BLUE
            }
        }
    }
}
