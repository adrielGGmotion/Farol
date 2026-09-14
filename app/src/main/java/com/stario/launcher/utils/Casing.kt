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

package com.stario.launcher.utils

class Casing {
    companion object {
        private const val WORD_SEPARATORS = " .-_/()"

        @JvmStatic
        fun toSentenceCase(value: String): String = toSentenceCase(StringBuilder(value)).toString()

        private fun toSentenceCase(builder: StringBuilder): StringBuilder {
            var capitalizeNext = true
            for (i in 0 until builder.length) {
                val character = builder[i]
                if (character == '.') {
                    capitalizeNext = true
                } else if (capitalizeNext && !isSeparator(character)) {
                    builder.setCharAt(i, Character.toTitleCase(character))
                    capitalizeNext = false
                } else if (!Character.isLowerCase(character)) {
                    builder.setCharAt(i, Character.toLowerCase(character))
                }
            }
            return builder
        }

        private fun isSeparator(character: Char): Boolean = WORD_SEPARATORS.indexOf(character) >= 0

        @JvmStatic
        fun toTitleCase(value: String): String = toTitleCase(StringBuilder(value)).toString()

        private fun toTitleCase(builder: StringBuilder): StringBuilder {
            var capitalizeNext = true
            for (i in 0 until builder.length) {
                val character = builder[i]
                if (isSeparator(character)) {
                    capitalizeNext = true
                } else if (capitalizeNext) {
                    builder.setCharAt(i, Character.toTitleCase(character))
                    capitalizeNext = false
                } else if (!Character.isLowerCase(character)) {
                    builder.setCharAt(i, Character.toLowerCase(character))
                }
            }
            return builder
        }
    }
}
