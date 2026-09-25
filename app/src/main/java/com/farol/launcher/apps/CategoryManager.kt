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

package com.farol.launcher.apps

import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.UserHandle
import com.farol.launcher.R
import com.farol.launcher.Farol
import com.farol.launcher.apps.interfaces.LauncherApplicationListener
import com.farol.launcher.apps.interfaces.LauncherProfileListener
import com.farol.launcher.preferences.Entry
import com.farol.launcher.ui.utils.UiUtils
import com.farol.launcher.utils.Utils
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.Locale
import java.util.UUID
import androidx.core.view.isVisible
import androidx.core.view.size
import androidx.core.content.edit

class CategoryManager private constructor(farol: Farol) {
    private val categories: MutableList<Category> = Collections.synchronizedList(ArrayList())
    private val remappedCategories: SharedPreferences = farol.getSharedPreferences(Entry.CATEGORIES)
    private val customCategoryNames: SharedPreferences = farol.getSharedPreferences(Entry.CATEGORY_NAMES)
    private val resolvedCategoryResources = HashMap<String, String>()
    private val profileListeners: MutableMap<UserHandle, LauncherApplicationListener> = HashMap()
    private val categoryListeners = ArrayList<CategoryListener>()
    private val comparator: CategoryMappings.Comparator<Category>

    init {
        CategoryMappings.from(farol)
        comparator = CategoryMappings.getCategoryComparator()

        for (manager in ProfileManager.getInstance().getProfiles()) {
            registerProfile(manager)
        }

        ProfileManager.getInstance().addLauncherProfileListener(object : LauncherProfileListener {
            override fun onInserted(handle: UserHandle) {
                val manager = ProfileManager.getInstance().getProfile(handle)

                if (manager != null) {
                    registerProfile(manager)
                }
            }

            override fun onRemoved(handle: UserHandle) {
                unregisterProfile(handle)
            }
        })
    }

    private fun registerProfile(manager: ProfileApplicationManager) {
        val listener = object : LauncherApplicationListener {
            override fun onUpdated(application: LauncherApplication) {
                for (category in categories) {
                    if (category.applications.contains(application)) {
                        for (itemListener in category.listeners) {
                            itemListener.onUpdated(application)
                        }
                    }
                }
            }
        }

        manager.addApplicationListener(listener)
        profileListeners[manager.handle] = listener
    }

    private fun unregisterProfile(handle: UserHandle) {
        val listener = profileListeners.remove(handle)
        val manager = ProfileManager.getInstance().getProfile(handle)

        if (manager != null && listener != null) {
            manager.removeApplicationListener(listener)
        }
    }

    fun addOnReadyListener(readyListener: OnLoadReadyListener) {
        if (isReady()) {
            readyListener.onReady()
            return
        }

        for (manager in ProfileManager.getInstance().getProfiles()) {
            manager.addOnReadyListener {
                if (isReady()) {
                    readyListener.onReady()
                }
            }
        }
    }

    fun isReady(): Boolean {
        for (manager in ProfileManager.getInstance().getProfiles()) {
            if (!manager.isReady()) {
                return false
            }
        }

        return true
    }

    fun get(index: Int): Category = categories[index]

    fun get(identifier: UUID?): Category? {
        for (category in categories) {
            if (category.identifier == identifier) {
                return category
            }
        }

        return null
    }

    fun getIdentifier(name: String?): UUID? = getIdentifier(name, false)

    fun getIdentifier(name: String?, includeDefaults: Boolean): UUID? {
        if (name == null) {
            return null
        }

        val testedDefaults = ArrayList<UUID>()

        for (category in categories) {
            val categoryName = getCategoryName(category.identifier)

            if (categoryName != null && categoryName.equals(name, ignoreCase = true)) {
                return category.identifier
            }

            if (resolvedCategoryResources.containsKey(category.identifier.toString())) {
                testedDefaults.add(category.identifier)
            }
        }

        if (includeDefaults) {
            for ((key, value) in resolvedCategoryResources) {
                if (value.equals(name, ignoreCase = true) &&
                    !testedDefaults.contains(UUID.fromString(key))
                ) {
                    return UUID.fromString(key)
                }
            }
        }

        return null
    }

    fun getCategoryName(identifier: UUID): String? {
        var name = customCategoryNames.getString(identifier.toString(), null)

        if (name == null) {
            name = resolvedCategoryResources[identifier.toString()]
        }

        return name
    }

    private fun indexOf(identifier: UUID): Int {
        for (index in 0 until size()) {
            if (get(index).identifier == identifier) {
                return index
            }
        }

        return NO_CATEGORY
    }

    fun indexOf(category: Category): Int = categories.indexOf(category)

    fun getAll(): List<Category> = Collections.unmodifiableList(categories)

    fun getSuggestion(input: String?): String? {
        if (input == null) {
            return null
        }

        for (tester in resolvedCategoryResources.values) {
            if (tester.lowercase(Locale.getDefault()).startsWith(input.lowercase(Locale.getDefault()))) {
                return tester
            }
        }

        for (category in categories) {
            val name = getCategoryName(category.identifier)

            if (name != null && name.lowercase(Locale.getDefault())
                    .startsWith(input.lowercase(Locale.getDefault()))
            ) {
                return name
            }
        }

        return null
    }

    private fun getRemappedCategoryKey(
        info: ApplicationInfo,
        handle: UserHandle
    ): String {
        return if (handle == ProfileManager.getOwner()) {
            info.packageName
        } else {
            info.packageName + ":" + handle.hashCode()
        }
    }

    fun getCategoryIdentifier(
        applicationInfo: ApplicationInfo,
        handle: UserHandle
    ): UUID {
        val key = getRemappedCategoryKey(applicationInfo, handle)

        if (remappedCategories.contains(key)) {
            val identifier = remappedCategories.getString(key, null)

            try {
                return UUID.fromString(identifier)
            } catch (exception: IllegalArgumentException) {
                remappedCategories.edit {
                        remove(key)
                    }
            }
        }

        if (handle != ProfileManager.getOwner()) {
            return Utils.intToUUID(MANAGED_CATEGORY)
        }

        return Utils.intToUUID(applicationInfo.category)
    }

    fun size(): Int = categories.size

    @Synchronized
    private fun addCategory(identifier: UUID): Int {
        val categoryToAdd = Category(identifier)
        var left = 0
        var right = size() - 1

        while (left <= right) {
            val middle = (left + right) / 2
            val categoryAtMiddle = get(middle)
            val compareValue = comparator.compare(categoryAtMiddle, categoryToAdd)

            if (compareValue < 0) {
                left = middle + 1
            } else if (compareValue > 0) {
                right = middle - 1
            } else {
                return middle
            }
        }

        categories.add(left, categoryToAdd)

        for (listener in categoryListeners) {
            listener.onCreated(categoryToAdd)
        }

        return left
    }

    fun addCustomCategory(name: String): UUID {
        var identifier = getIdentifier(name, true)
        if (identifier == null) {
            identifier = UUID.randomUUID()

            customCategoryNames.edit {
                    putString(identifier.toString(), name)
                }
        } else if (indexOf(identifier) == NO_CATEGORY) {
            addCategory(identifier)
        }

        return identifier
    }

    fun updateCategory(application: LauncherApplication, uuid: UUID?) {
        if (uuid == null || application.getCategory() == uuid) {
            return
        }

        remappedCategories.edit {
                putString(getRemappedCategoryKey(application.info, application.handle), uuid.toString())
            }

        val category = getCategoryIdentifier(application.info, application.handle)

        if (application.category != category) {
            removeApplication(application)
            application.category = category
            addApplication(application)
        }
    }

    fun updateCategory(categoryIdentifier: UUID, text: String) {
        if (getIdentifier(text, true) != null) {
            return
        }

        customCategoryNames.edit {
                putString(categoryIdentifier.toString(), text)
            }

        for (listener in categoryListeners) {
            listener.onChanged(get(categoryIdentifier))
        }
    }

    @Synchronized
    fun addApplication(application: LauncherApplication) {
        val manager = ProfileManager.getInstance().getProfile(application.getProfile())

        if (manager != null && manager.isVisibleToUser(application)) {
            UiUtils.post {
                var index = indexOf(application.getCategory())

                if (index == NO_CATEGORY) {
                    index = addCategory(application.getCategory())
                }

                val category = get(index)
                category.addApplication(application)
            }
        }
    }

    @Synchronized
    fun removeApplication(application: LauncherApplication) {
        val index = indexOf(application.getCategory())

        if (index != NO_CATEGORY) {
            UiUtils.post {
                val category = get(index)
                category.removeApplication(application)

                if (category.getSize() == 0) {
                    for (listener in categoryListeners) {
                        listener.onPrepareRemoval(category)
                    }

                    category.clearListeners()
                    categories.removeAt(index)

                    for (listener in categoryListeners) {
                        listener.onRemoved(category)
                    }
                }
            }
        }
    }

    fun swap(index1: Int, index2: Int) {
        Collections.swap(categories, index1, index2)
        comparator.updatePermutation()
    }

    fun addOnCategoryUpdateListener(updateListener: CategoryListener?) {
        if (updateListener != null) {
            categoryListeners.add(updateListener)
        }
    }

    fun removeOnCategoryUpdateListener(updateListener: CategoryListener?) {
        if (updateListener != null) {
            categoryListeners.remove(updateListener)
        }
    }

    /**
     * These callbacks do not guarantee that will be run on the UI thread
     */
    interface CategoryListener {
        fun onCreated(category: Category) {
        }

        fun onChanged(category: Category?) {
        }

        /**
         * This will always be called before {@link #onRemoved(Category)}
         */
        fun onPrepareRemoval(category: Category) {
        }

        /**
         * This will always be called after {@link #onPrepareRemoval(Category)}
         */
        fun onRemoved(category: Category) {
        }
    }

    fun interface OnLoadReadyListener {
        fun onReady()
    }

    companion object {
        private const val NO_CATEGORY = -1
        private const val MANAGED_CATEGORY = -2
        private val DEFAULT_CATEGORIES = hashMapOf(
            MANAGED_CATEGORY to R.string.managed,
            NO_CATEGORY to R.string.unsorted,
            0 to R.string.games,
            1 to R.string.audio,
            2 to R.string.video,
            3 to R.string.images,
            4 to R.string.social,
            5 to R.string.news,
            6 to R.string.maps,
            7 to R.string.productivity,
            8 to R.string.accessibility,
            9 to R.string.finance,
            10 to R.string.health,
            11 to R.string.personalization,
            12 to R.string.sports
        )
        private var instance: CategoryManager? = null

        @JvmStatic
        fun from(farol: Farol): CategoryManager {
            if (instance == null) {
                instance = CategoryManager(farol)
            }

            val manager = instance!!
            for ((id, resource) in DEFAULT_CATEGORIES) {
                manager.resolvedCategoryResources[Utils.intToUUID(id).toString()] =
                    farol.resources.getString(resource)
            }

            return manager
        }

        @JvmStatic
        fun getInstance(): CategoryManager {
            return instance ?: throw RuntimeException("Applications not initialized.")
        }
    }
}
