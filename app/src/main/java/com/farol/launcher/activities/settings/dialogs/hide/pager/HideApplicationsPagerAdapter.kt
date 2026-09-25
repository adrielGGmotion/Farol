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

package com.farol.launcher.activities.settings.dialogs.hide.pager

import android.content.res.Resources
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.farol.launcher.R
import com.farol.launcher.apps.ProfileApplicationManager
import com.farol.launcher.apps.ProfileManager
import com.farol.launcher.utils.Utils
import java.lang.ref.WeakReference
import androidx.core.view.size

open class HideApplicationsPagerAdapter(
    manager: FragmentManager,
    private val resources: Resources
) : FragmentPagerAdapter(manager) {
    private val registeredFragments: SparseArray<WeakReference<HideApplicationsPage>> = SparseArray()
    private val profileManager: ProfileManager = ProfileManager.getInstance()

    override fun getPageTitle(position: Int): CharSequence? {
        val profiles: List<ProfileApplicationManager> = profileManager.getProfiles()
        if (profiles.size <= 1) {
            return resources.getString(R.string.apps)
        }

        return resources.getString(
            if (Utils.isMainProfile(profiles[position].handle)) R.string.personal else R.string.managed
        )
    }

    override fun getItem(position: Int): Fragment {
        return HideApplicationsPage(profileManager.getProfile(position))
    }

    override fun getCount(): Int = ProfileManager.getInstance().size()

    override fun setPrimaryItem(container: ViewGroup, position: Int, item: Any) {
        super.setPrimaryItem(container, position, item)

        val fragment = item as Fragment
        var view = fragment.getView()

        if (view != null) {
            val nestedView = view.findViewWithTag<View>("nested")
            if (nestedView != null) {
                nestedView.setNestedScrollingEnabled(true)
            }
        }

        val fragmentManager = fragment.getParentFragmentManager()
        for (otherFragment in fragmentManager.getFragments()) {
            if (!fragment.equals(otherFragment)) {
                view = otherFragment.getView()

                if (view != null) {
                    val nestedView = view.findViewWithTag<View>("nested")
                    if (nestedView != null) {
                        nestedView.setNestedScrollingEnabled(false)
                    }
                }
            }
        }

        container.requestLayout()
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val fragment = super.instantiateItem(container, position) as HideApplicationsPage
        registeredFragments.put(position, WeakReference(fragment))

        return fragment
    }

    override fun destroyItem(container: ViewGroup, position: Int, item: Any) {
        registeredFragments.remove(position)
        super.destroyItem(container, position, item)
    }

    open fun getRegisteredFragment(position: Int): HideApplicationsPage? {
        return registeredFragments.get(position)?.get()
    }
}
