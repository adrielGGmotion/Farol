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

package com.stario.launcher.apps

import android.content.Context
import android.content.SharedPreferences
import com.stario.launcher.Stario
import com.stario.launcher.exceptions.NoExistingInstanceException
import com.stario.launcher.preferences.Entry
import java.util.HashMap
import java.util.Map
import androidx.core.content.edit

open class CategoryMappings private constructor(private val provider: SharedPreferencesProvider) {
    private fun interface SharedPreferencesProvider {
        fun provide(name: String): SharedPreferences
    }

    abstract class Comparator<T> : java.util.Comparator<T> {
        abstract fun updatePermutation()
    }

    private class ApplicationComparator(
        provider: SharedPreferencesProvider,
        private val category: Category
    ) : Comparator<LauncherApplication>() {
        private val indexCache = HashMap<String, Int>()
        private val categoryMap: SharedPreferences = provider.provide(category.identifier.toString())

        init {
            for ((key, value) in categoryMap.all) {
                if (value is Int) {
                    indexCache[key] = value
                }
            }
        }

        private fun getApplicationKey(application: LauncherApplication): String {
            return if (application.getProfile() == ProfileManager.getOwner()) {
                application.getInfo().packageName
            } else {
                application.getInfo().packageName + ":" + application.getProfile().hashCode()
            }
        }

        override fun compare(a: LauncherApplication, b: LauncherApplication): Int {
            val aIndex = indexCache[getApplicationKey(a)]
            val bIndex = indexCache[getApplicationKey(b)]

            if (aIndex != null && bIndex != null) {
                return Integer.compare(aIndex, bIndex)
            }

            return a.compareTo(b)
        }

        override fun updatePermutation() {
            val applications = category.getAll()
            categoryMap.edit {
                clear()
                indexCache.clear()

                for (index in applications.indices) {
                    val application = applications[index]
                    val key = getApplicationKey(application)

                    putInt(key, index)
                    indexCache[key] = index
                }

            }
        }
    }

    private class MapComparator(provider: SharedPreferencesProvider) : Comparator<Category>() {
        private val indexCache = HashMap<String, Int>()
        private val categoryMap: SharedPreferences = provider.provide("CATEGORIES")

        init {
            for ((key, value) in categoryMap.all) {
                if (value is Int) {
                    indexCache[key] = value
                }
            }
        }

        override fun compare(a: Category, b: Category): Int {
            val aIndex = indexCache[a.identifier.toString()]
            val bIndex = indexCache[b.identifier.toString()]

            if (aIndex != null && bIndex != null) {
                return Integer.compare(aIndex, bIndex)
            }

            return a.identifier.compareTo(b.identifier)
        }

        override fun updatePermutation() {
            val categories = CategoryManager.getInstance().getAll()
            categoryMap.edit {
                clear()
                indexCache.clear()

                for (index in categories.indices) {
                    val category = categories[index]
                    val key = category.identifier.toString()

                    putInt(key, index)
                    indexCache[key] = index
                }

            }
        }
    }

    companion object {
        private var instance: CategoryMappings? = null

        @JvmStatic
        fun from(stario: Stario) {
            if (instance == null) {
                instance = CategoryMappings(SharedPreferencesProvider { name ->
                    stario.getSharedPreferences(
                        Entry.CATEGORY_MAP.toSubPreference(name),
                        Context.MODE_PRIVATE
                    )
                })
            }
        }

        /**
         * @return A comparator for sorting {@link Category} objects.
         * @throws NoExistingInstanceException If the singleton instance has not been initialized.
         */
        @JvmStatic
        fun getCategoryComparator(): Comparator<Category> {
            val mappings = instance ?: throw NoExistingInstanceException(CategoryMappings::class.java)
            return MapComparator(mappings.provider)
        }

        /**
         * @param category The category for which to retrieve the application comparator.
         * @return A comparator for sorting {@link LauncherApplication} objects.
         * @throws NoExistingInstanceException If the singleton instance has not been initialized.
         */
        @JvmStatic
        fun getCategoryApplicationComparator(category: Category): Comparator<LauncherApplication> {
            val mappings = instance ?: throw NoExistingInstanceException(CategoryMappings::class.java)
            return ApplicationComparator(mappings.provider, category)
        }
    }
}
