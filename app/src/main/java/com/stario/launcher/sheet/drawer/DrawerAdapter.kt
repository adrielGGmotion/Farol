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

package com.stario.launcher.sheet.drawer

import android.os.UserHandle
import androidx.annotation.IntRange
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.stario.launcher.apps.ProfileManager
import com.stario.launcher.apps.interfaces.LauncherProfileListener
import com.stario.launcher.sheet.drawer.category.Categories
import com.stario.launcher.sheet.drawer.list.List as DrawerList

/**
 * @noinspection deprecation
 */
class DrawerAdapter(private val fragmentManager: FragmentManager) : FragmentPagerAdapter(fragmentManager) {
    companion object {
        const val CATEGORIES_POSITION = 1
        private const val PAGES = 3
    }

    private val fragments = ArrayList<Fragment?>()
    private var transaction: FragmentTransaction? = null

    init {
        val listener = object : LauncherProfileListener {
            override fun onInserted(handle: UserHandle) {
                val oldCount = count - 1
                if (fragments.size > oldCount - 1) {
                    removeFragment(oldCount - 1)
                    if (fragments.size > oldCount) {
                        fragments[oldCount] = fragments[oldCount - 1]
                    } else {
                        fragments.add(fragments[oldCount - 1])
                    }
                    fragments[oldCount - 1] = null
                }
                notifyDataSetChanged()
            }

            override fun onRemoved(handle: UserHandle) {
                for (index in fragments.indices) {
                    val fragment = fragments[index]
                    if (fragment is DrawerList && handle == fragment.getUserHandle()) {
                        removeFragment(index)
                        for (move in index + 1 until fragments.size) {
                            if (fragments[move] != null) removeFragment(move)
                            fragments[move - 1] = fragments[move]
                        }
                        if (fragments.size > count) {
                            if (fragments[count] != null) {
                                removeFragment(count)
                                fragments[count] = null
                            }
                        }
                        break
                    }
                }
                notifyDataSetChanged()
            }

            override fun hashCode(): Int = -1
            override fun equals(other: Any?): Boolean = other != null && hashCode() == other.hashCode()
        }
        val manager = ProfileManager.getInstance()
        manager.removeLauncherProfileListener(listener)
        manager.addLauncherProfileListener(listener)
    }

    override fun getItem(position: Int): Fragment {
        while (position >= fragments.size) fragments.add(null)
        if (fragments[position] == null) {
            fragments[position] = when {
                position == 0 || position == count - 1 -> Fragment()
                position == CATEGORIES_POSITION -> Categories()
                else -> ProfileManager.getInstance().getProfile(position - 2)?.let { DrawerList(it) } ?: Fragment()
            }
        }
        return requireNotNull(fragments[position])
    }

    private fun removeFragment(position: Int) {
        val fragment = fragments[position] ?: return
        val host = fragment.host
        if (host is FragmentActivity) {
            val lifecycle = host.lifecycle
            if (lifecycle.currentState == Lifecycle.State.RESUMED) {
                destroyItem(host.findViewById(android.R.id.content), position, fragment)
                fragmentManager.beginTransaction().remove(fragment).commitNow()
            } else {
                if (transaction == null) transaction = fragmentManager.beginTransaction()
                transaction?.remove(fragment)
                lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) {
                        transaction = null
                        lifecycle.removeObserver(this)
                    }

                    override fun onResume(owner: LifecycleOwner) {
                        transaction?.commitNow()
                        transaction = null
                        lifecycle.removeObserver(this)
                    }
                })
            }
        }
    }

    fun getFragment(position: Int): Fragment? = fragments.getOrNull(position)

    override fun getItemPosition(`object`: Any): Int {
        val index = fragments.indexOfFirst { it == `object` }
        return if (index >= 0 && index < count) index else POSITION_NONE
    }

    @IntRange(from = PAGES.toLong())
    override fun getCount(): Int = PAGES + ProfileManager.getInstance().size()

    fun reset() {
        collapse()
        if (!fragmentManager.isDestroyed) {
            for (fragment in fragmentManager.fragments) {
                if (fragment is ScrollToTop) fragment.scrollToTop()
            }
        }
    }

    fun collapse(): Boolean = !fragmentManager.isDestroyed &&
        fragmentManager.popBackStackImmediate(Categories.FOLDER_STACK_ID, FragmentManager.POP_BACK_STACK_INCLUSIVE)
}
