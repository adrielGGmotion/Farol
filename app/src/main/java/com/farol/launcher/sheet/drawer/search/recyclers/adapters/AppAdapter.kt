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

package com.farol.launcher.sheet.drawer.search.recyclers.adapters

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.farol.launcher.apps.LauncherApplication
import com.farol.launcher.apps.ProfileManager
import com.farol.launcher.preferences.Entry
import com.farol.launcher.sheet.drawer.RecyclerApplicationAdapter
import com.farol.launcher.sheet.drawer.search.JaroWinklerDistance
import com.farol.launcher.sheet.drawer.search.SearchFragment
import com.farol.launcher.sheet.drawer.search.Searchable
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.ui.recyclers.async.InflationType
import java.util.Locale
import androidx.core.view.isVisible
import androidx.core.view.size

open class AppAdapter(activity: ThemedActivity) : RecyclerApplicationAdapter(activity, null, InflationType.SYNCED), Searchable {
    private val preferences: SharedPreferences = activity.applicationContext.getSharedPreferences(Entry.SEARCH)
    private var applications: MutableList<LauncherApplication> = ArrayList()
    private var recyclerView: RecyclerView? = null
    private var currentQuery: String? = ""

    override fun getApplication(index: Int): LauncherApplication = applications[index]
    override fun allowApplicationStateEditing(): Boolean = false
    override fun getLabelLineCount(): Int = 1
    override fun getTotalItemCount(): Int = applications.size

    @SuppressLint("NotifyDataSetChanged")
    override fun update(query: String?) {
        var filteredList: MutableList<LauncherApplication> = ArrayList()
        var starting = 0
        var containing = 0
        var close = 0

        if (!query.isNullOrEmpty()) {
            val filterPattern = query.lowercase(Locale.getDefault())
            val profileManagers = ProfileManager.getInstance().getProfiles()
            val showHiddenItems = preferences.getBoolean(SearchFragment.SEARCH_HIDDEN_APPS, false)
            for (manager in profileManagers) {
                for (index in 0 until if (showHiddenItems) manager.getSize() else manager.getActualSize()) {
                    val application = if (showHiddenItems) manager.get(index, true) else manager.get(index)
                    if (application != null) {
                        val lowercaseLabel = application.getLabel().lowercase(Locale.getDefault())
                        if (lowercaseLabel.startsWith(filterPattern)) {
                            filteredList.add(starting++, application)
                        } else if (lowercaseLabel.contains(filterPattern)) {
                            filteredList.add(starting + containing++, application)
                        } else if (JaroWinklerDistance.getScore(lowercaseLabel, filterPattern) > 0.87) {
                            filteredList.add(starting + containing + close++, application)
                        }
                    }
                }
            }
            if (filteredList.size > SearchFragment.MAX_APP_QUERY_ITEMS) {
                filteredList = filteredList.subList(0, SearchFragment.MAX_APP_QUERY_ITEMS)
            }
        }
        applications = filteredList
        currentQuery = query
        val runnable = Runnable {
            notifyDataSetChanged()
            updateRecyclerVisibility()
        }
        val recycler = recyclerView
        if (recycler != null && recycler.isAnimating) recycler.post(runnable) else runnable.run()
    }

    override fun submit(): Boolean {
        val recycler = recyclerView ?: return false
        if (recycler.layoutManager != null && recycler.isVisible) {
            val view = recycler.layoutManager?.findViewByPosition(0)
            if (view != null) {
                view.callOnClick()
                return true
            }
        }
        return false
    }

    private fun updateRecyclerVisibility() {
        val recycler = recyclerView ?: return
        if (itemCount == 0 && recycler.visibility != View.GONE) recycler.visibility = View.GONE
        else if (itemCount > 0 && recycler.visibility != View.VISIBLE) recycler.visibility = View.VISIBLE
    }

    override fun onBind(holder: ApplicationViewHolder, position: Int) {
        super.onBind(holder, position)
        val label = getApplication(position).getLabel()
        val query = currentQuery!!
        val substringStart = label.lowercase(Locale.getDefault()).indexOf(query)
        if (substringStart >= 0) {
            val builder = SpannableStringBuilder(label)
            builder.setSpan(StyleSpan(Typeface.BOLD), substringStart, substringStart + query.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            holder.setLabel(builder)
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
        updateRecyclerVisibility()
        super.onAttachedToRecyclerView(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        updateRecyclerVisibility()
        this.recyclerView = null
        super.onDetachedFromRecyclerView(recyclerView)
    }
}
