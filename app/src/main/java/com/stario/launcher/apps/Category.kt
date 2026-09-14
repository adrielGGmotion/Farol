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

import java.util.Collections
import java.util.UUID

class Category(@JvmField val identifier: UUID) {
    private val comparator: CategoryMappings.Comparator<LauncherApplication> =
        CategoryMappings.getCategoryApplicationComparator(this)

    @JvmField
    val applications: MutableList<LauncherApplication> =
        Collections.synchronizedList(ArrayList())

    @JvmField
    val listeners: MutableList<CategoryItemListener> =
        Collections.synchronizedList(ArrayList())

    fun getSize(): Int = applications.size

    fun get(index: Int): LauncherApplication? =
        if (index < applications.size) applications[index] else LauncherApplication.FALLBACK_APP

    fun getAll(): List<LauncherApplication> = Collections.unmodifiableList(applications)

    @Synchronized
    fun addApplication(applicationToAdd: LauncherApplication) {
        var left = 0
        var right = applications.size - 1
        while (left <= right) {
            val middle = (left + right) / 2
            val applicationAtMiddle = applications[middle]
            val compareValue = comparator.compare(applicationAtMiddle, applicationToAdd)
            if (compareValue < 0) {
                left = middle + 1
            } else if (compareValue > 0) {
                right = middle - 1
            } else if (applicationAtMiddle.info.packageName != applicationToAdd.info.packageName ||
                applicationAtMiddle.getProfile() != applicationToAdd.getProfile()
            ) {
                listeners.forEach { it.onPrepareInsertion(applicationToAdd) }
                applications.add(middle, applicationToAdd)
                listeners.forEach { it.onInserted(applicationToAdd) }
                return
            } else {
                return
            }
        }
        listeners.forEach { it.onPrepareInsertion(applicationToAdd) }
        applications.add(left, applicationToAdd)
        listeners.forEach { it.onInserted(applicationToAdd) }
    }

    @Synchronized
    fun removeApplication(applicationToRemove: LauncherApplication) {
        for (index in applications.indices) {
            val application = applications[index]
            if (application.getInfo().packageName == applicationToRemove.getInfo().packageName &&
                application.getProfile() == applicationToRemove.getProfile()
            ) {
                listeners.forEach { it.onPrepareRemoval(application) }
                applications.removeAt(index)
                listeners.forEach { it.onRemoved(application) }
                return
            }
        }
    }

    fun swap(index1: Int, index2: Int) {
        Collections.swap(applications, index1, index2)
        comparator.updatePermutation()
        listeners.forEach { it.onSwapped(index1, index2) }
    }

    fun indexOf(application: LauncherApplication): Int = applications.indexOf(application)

    fun addCategoryItemListener(listener: CategoryItemListener?) {
        if (listener != null) listeners.add(listener)
    }

    fun removeCategoryItemListener(listener: CategoryItemListener?) {
        if (listener != null) listeners.remove(listener)
    }

    fun clearListeners() {
        listeners.clear()
    }

    override fun hashCode(): Int = identifier.hashCode()

    override fun equals(other: Any?): Boolean =
        other is Category && other.identifier == identifier

    /** These callbacks do not guarantee that they will run on the UI thread. */
    interface CategoryItemListener {
        fun onPrepareInsertion(application: LauncherApplication) {}
        fun onInserted(application: LauncherApplication) {}
        fun onPrepareRemoval(application: LauncherApplication) {}
        fun onRemoved(application: LauncherApplication) {}
        fun onUpdated(application: LauncherApplication) {}
        fun onSwapped(index1: Int, index2: Int) {}
    }
}
