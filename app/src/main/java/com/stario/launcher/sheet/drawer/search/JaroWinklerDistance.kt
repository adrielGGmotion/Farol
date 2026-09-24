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

package com.stario.launcher.sheet.drawer.search

import java.util.Arrays

/**
 * A similarity algorithm indicating the percentage of matched characters between two character sequences.
 *
 * The Jaro measure is the weighted sum of percentage of matched characters
 * from each file and transposed characters. Winkler increased this measure
 * for matching initial characters.
 *
 * This implementation is based on the Jaro Winkler similarity algorithm
 * from <a href="http://en.wikipedia.org/wiki/Jaro%E2%80%93Winkler_distance">
 * http://en.wikipedia.org/wiki/Jaro%E2%80%93Winkler_distance</a>.
 *
 * This code has been adapted from Apache Commons Lang 3.3.
 *
 * @since 1.0
 */
open class JaroWinklerDistance {
    companion object {
        /**
         * Find the Jaro Winkler Distance which indicates the similarity score
         * between two CharSequences.
         *
         * distance.apply(null, null)          = IllegalArgumentException
         * distance.apply("","")               = 0.0
         * distance.apply("","a")              = 0.0
         * distance.apply("aaapppp", "")       = 0.0
         * distance.apply("frog", "fog")       = 0.93
         * distance.apply("fly", "ant")        = 0.0
         * distance.apply("elephant", "hippo") = 0.44
         * distance.apply("hippo", "elephant") = 0.44
         * distance.apply("hippo", "zzzzzzzz") = 0.0
         * distance.apply("hello", "hallo")    = 0.88
         * distance.apply("ABC Corporation", "ABC Corp") = 0.93
         * distance.apply("D N H Enterprises Inc", "D &amp; H Enterprises, Inc.") = 0.95
         * distance.apply("My Gym Children's Fitness Center", "My Gym. Childrens Fitness") = 0.92
         * distance.apply("PENNSYLVANIA", "PENNCISYLVNIA") = 0.88
         *
         * @param left the first String, must not be null
         * @param right the second String, must not be null
         * @return result distance
         * @throws IllegalArgumentException if either String input is null
         */
        @JvmStatic
        fun getScore(left: CharSequence?, right: CharSequence?): Double {
            require(left != null && right != null) { "Strings must not be null" }
            val mtp = matches(left, right)
            val m = mtp[0].toDouble()
            if (m == 0.0) return 0.0
            val j = (m / left.length + m / right.length + (m - mtp[1]) / m) / 3
            val jw = if (j < 0.7) j else j + minOf(0.1, 1.0 / mtp[3]) * mtp[2] * (1.0 - j)
            return Math.round(jw * 100.0) / 100.0
        }

        /**
         * This method returns the Jaro-Winkler string matches, transpositions, prefix, max array.
         *
         * @param first the first string to be matched
         * @param second the second string to be matched
         * @return mtp array containing: matches, transpositions, prefix, and max length
         */
        @JvmStatic
        protected fun matches(first: CharSequence, second: CharSequence): IntArray {
            val max: CharSequence
            val min: CharSequence
            if (first.length > second.length) {
                max = first
                min = second
            } else {
                max = second
                min = first
            }
            val range = maxOf(max.length / 2 - 1, 0)
            val matchIndexes = IntArray(min.length)
            Arrays.fill(matchIndexes, -1)
            val matchFlags = BooleanArray(max.length)
            var matched = 0
            for (mi in min.indices) {
                val c1 = min[mi]
                for (xi in maxOf(mi - range, 0) until minOf(mi + range + 1, max.length)) {
                    if (!matchFlags[xi] && c1 == max[xi]) {
                        matchIndexes[mi] = xi
                        matchFlags[xi] = true
                        matched++
                        break
                    }
                }
            }
            val ms1 = CharArray(matched)
            val ms2 = CharArray(matched)
            var si = 0
            for (i in min.indices) {
                if (matchIndexes[i] != -1) ms1[si++] = min[i]
            }
            si = 0
            for (i in max.indices) {
                if (matchFlags[i]) ms2[si++] = max[i]
            }
            var transpositions = 0
            for (mi in ms1.indices) if (ms1[mi] != ms2[mi]) transpositions++
            var prefix = 0
            for (mi in min.indices) {
                if (first[mi] == second[mi]) prefix++ else break
            }
            return intArrayOf(matched, transpositions / 2, prefix, max.length)
        }
    }
}
