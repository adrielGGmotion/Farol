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

package com.farol.launcher.sheet.briefing.dialog

import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.PagerAdapter
import com.farol.launcher.sheet.briefing.dialog.page.FeedPage
import com.farol.launcher.sheet.briefing.dialog.page.feed.BriefingFeedList
import com.farol.launcher.sheet.briefing.dialog.page.feed.Feed
import com.farol.launcher.themes.ThemedActivity
import java.lang.ref.WeakReference
import androidx.core.view.size

open class BriefingAdapter(
    activity: ThemedActivity,
    fragmentManager: FragmentManager
) : FragmentPagerAdapter(fragmentManager) {
    private val registeredFragments: MutableMap<Int, WeakReference<FeedPage>> = HashMap()
    private val list = BriefingFeedList.from(activity)

    override fun getItem(position: Int): Fragment = FeedPage(position)

    override fun getCount(): Int = list.size()

    override fun getItemPosition(item: Any): Int = PagerAdapter.POSITION_NONE

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val fragment = super.instantiateItem(container, position) as FeedPage
        registeredFragments[position] = WeakReference(fragment)

        return fragment
    }

    override fun getItemId(position: Int): Long {
        val feed: Feed = list.get(position)

        return feed.getRSSLink().hashCode().toLong()
    }

    override fun destroyItem(container: ViewGroup, position: Int, item: Any) {
        registeredFragments.remove(position)

        super.destroyItem(container, position, item)
    }

    override fun getPageTitle(position: Int): CharSequence? = list.get(position).getTitle()

    fun getRegisteredFragment(position: Int): FeedPage? {
        val page = registeredFragments[position]

        return page?.get()
    }

    fun reset(vararg skipPositions: Int) {
        for ((position, fragmentReference) in registeredFragments) {
            var skipped = false

            for (skippedPosition in skipPositions) {
                if (position == skippedPosition) {
                    skipped = true
                    break
                }
            }

            if (!skipped) {
                val fragment = fragmentReference.get()
                if (fragment != null) {
                    fragment.reset()
                }
            }
        }
    }
}
