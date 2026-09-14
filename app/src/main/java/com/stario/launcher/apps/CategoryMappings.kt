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

class CategoryMappings private constructor(private val provider: SharedPreferencesProvider) {
    fun interface SharedPreferencesProvider {
        fun provide(name: String): SharedPreferences
    }

    abstract class Comparator<T> : java.util.Comparator<T> {
        abstract fun updatePermutation()
    }

    private class ApplicationComparator(
        provider: SharedPreferencesProvider,
        private val category: Category,
    ) : Comparator<LauncherApplication>() {
        private val indexCache = HashMap<String, Int>()
        private val categoryMap = provider.provide(category.identifier.toString())

        init {
            categoryMap.all.forEach { (key, value) ->
                if (value is Int) indexCache[key] = value
            }
        }

        private fun getApplicationKey(application: LauncherApplication): String {
            return if (application.getProfile() == ProfileManager.getOwner()) {
                application.getInfo().packageName
            } else {
                "${application.getInfo().packageName}:${application.getProfile().hashCode()}"
            }
        }

        override fun compare(a: LauncherApplication, b: LauncherApplication): Int {
            val aIndex = indexCache[getApplicationKey(a)]
            val bIndex = indexCache[getApplicationKey(b)]
            return if (aIndex != null && bIndex != null) {
                aIndex.compareTo(bIndex)
            } else {
                a.compareTo(b)
            }
        }

        override fun updatePermutation() {
            val applications = category.getAll()
            val editor = categoryMap.edit().clear()
            indexCache.clear()
            applications.forEachIndexed { index, application ->
                val key = getApplicationKey(application)
                editor.putInt(key, index)
                indexCache[key] = index
            }
            editor.apply()
        }
    }

    private class MapComparator(provider: SharedPreferencesProvider) : Comparator<Category>() {
        private val indexCache = HashMap<String, Int>()
        private val categoryMap = provider.provide("CATEGORIES")

        init {
            categoryMap.all.forEach { (key, value) ->
                if (value is Int) indexCache[key] = value
            }
        }

        override fun compare(a: Category, b: Category): Int {
            val aIndex = indexCache[a.identifier.toString()]
            val bIndex = indexCache[b.identifier.toString()]
            return if (aIndex != null && bIndex != null) {
                aIndex.compareTo(bIndex)
            } else {
                a.identifier.compareTo(b.identifier)
            }
        }

        override fun updatePermutation() {
            val categories = CategoryManager.getInstance().getAll()
            val editor = categoryMap.edit().clear()
            indexCache.clear()
            categories.forEachIndexed { index, category ->
                val key = category.identifier.toString()
                editor.putInt(key, index)
                indexCache[key] = index
            }
            editor.apply()
        }
    }

    companion object {
        private var instance: CategoryMappings? = null

        @JvmStatic
        fun from(stario: Stario) {
            if (instance == null) {
                instance = CategoryMappings { name ->
                    stario.getSharedPreferences(
                        Entry.CATEGORY_MAP.toSubPreference(name),
                        Context.MODE_PRIVATE,
                    )
                }
            }
        }

        @JvmStatic
        @Throws(NoExistingInstanceException::class)
        fun getCategoryComparator(): Comparator<Category> {
            val value = instance ?: throw NoExistingInstanceException(CategoryMappings::class.java)
            return MapComparator(value.provider)
        }

        @JvmStatic
        fun getCategoryApplicationComparator(category: Category): Comparator<LauncherApplication> {
            val value = instance ?: throw NoExistingInstanceException(CategoryMappings::class.java)
            return ApplicationComparator(value.provider, category)
        }
    }
}
