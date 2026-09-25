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

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.farol.launcher.R
import com.farol.launcher.apps.ProfileApplicationManager
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.ui.Measurements
import com.farol.launcher.ui.recyclers.autogrid.AutoGridLayoutManager
import com.farol.launcher.ui.recyclers.overscroll.OverScrollEffect
import com.farol.launcher.ui.recyclers.overscroll.OverScrollRecyclerView
import com.farol.launcher.ui.utils.LayoutSizeObserver

open class HideApplicationsPage() : Fragment() {
    private var applicationManager: ProfileApplicationManager? = null
    private lateinit var recyclerView: OverScrollRecyclerView

    constructor(applicationManager: ProfileApplicationManager?) : this() {
        this.applicationManager = applicationManager
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.pop_up_hide_page, container, false)

        recyclerView = root.findViewById(R.id.recycler)
        recyclerView.setOverscrollPullEdges(OverScrollEffect.PULL_EDGE_BOTTOM)

        recyclerView.setPadding(
            recyclerView.getPaddingLeft(),
            recyclerView.getPaddingTop(),
            recyclerView.getPaddingRight(),
            Measurements.dpToPx(40f) + Measurements.spToPx(60f)
        )

        val manager = AutoGridLayoutManager(requireActivity(), 1)
        LayoutSizeObserver.attach(root, LayoutSizeObserver.WIDTH, object : LayoutSizeObserver.OnChange {
            override fun onChange(view: View, watchFlags: Int, rect: Rect) {
                manager.setSpanCount(minOf(6, rect.width() / Measurements.dpToPx(90f)))
            }
        })

        recyclerView.setLayoutManager(manager)
        recyclerView.setItemAnimator(null)
        recyclerView.setAdapter(object : HiddenRecyclerAdapter(getActivity() as ThemedActivity, applicationManager) {})

        return root
    }

    override fun onDestroy() {
        recyclerView.setAdapter(null)
        super.onDestroy()
    }

    open fun getRecycler(): OverScrollRecyclerView = recyclerView
}
