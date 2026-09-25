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

package com.farol.launcher.utils

open class Casing {
    companion object {
        private const val WORD_SEPARATORS = " .-_/()"

        /**
         * Converts an input string into sentence case, capitalizing the first word in the string and every word that
         * follows a period.
         * @param s input string
         * @return string transformed into sentence case
         */
        @JvmStatic
        fun toSentenceCase(s: String): String {
            return toSentenceCase(StringBuilder(s)).toString()
        }

        @JvmStatic
        private fun toSentenceCase(sb: StringBuilder): StringBuilder {
            var capitalizeNext = true
            for (i in 0 until sb.length) {
                val c = sb[i]
                if (c == '.') {
                    capitalizeNext = true
                } else if (capitalizeNext && !isSeparator(c)) {
                    sb.setCharAt(i, Character.toTitleCase(c))
                    capitalizeNext = false
                } else if (!Character.isLowerCase(c)) {
                    sb.setCharAt(i, Character.toLowerCase(c))
                }
            }
            return sb
        }

        @JvmStatic
        private fun isSeparator(c: Char): Boolean {
            return WORD_SEPARATORS.indexOf(c) >= 0
        }

        /**
         * Converts an input string into title case, capitalizing the first character of every word.
         * @param s input string
         * @return string transformed into title case
         */
        @JvmStatic
        fun toTitleCase(s: String): String {
            return toTitleCase(StringBuilder(s)).toString()
        }

        @JvmStatic
        private fun toTitleCase(sb: StringBuilder): StringBuilder {
            var capitalizeNext = true
            for (i in 0 until sb.length) {
                val c = sb[i]
                if (isSeparator(c)) {
                    capitalizeNext = true
                } else if (capitalizeNext) {
                    sb.setCharAt(i, Character.toTitleCase(c))
                    capitalizeNext = false
                } else if (!Character.isLowerCase(c)) {
                    sb.setCharAt(i, Character.toLowerCase(c))
                }
            }
            return sb
        }
    }
}
