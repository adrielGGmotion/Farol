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

package com.stario.launcher.sheet.drawer.list

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import com.stario.launcher.R
import com.stario.launcher.apps.ProfileApplicationManager
import com.stario.launcher.apps.ProfileManager
import com.stario.launcher.sheet.drawer.search.ListDrawerPage
import com.stario.launcher.ui.Measurements
import com.stario.launcher.ui.recyclers.FastScroller
import com.stario.launcher.ui.recyclers.async.AsyncRecyclerAdapter
import com.stario.launcher.ui.recyclers.autogrid.AutoGridLayoutManager
import com.stario.launcher.ui.utils.LayoutSizeObserver
import com.stario.launcher.utils.Utils
import com.stario.launcher.utils.objects.ObservableObject
import androidx.core.view.size

open class List : ListDrawerPage {
    companion object { private const val USER_HANDLE_KEY = "com.stario.UserHandle" }

    private lateinit var fastScroller: FastScroller
    private var handle: UserHandle? = null

    constructor() : super()
    constructor(profile: ProfileApplicationManager) : super() { handle = profile.handle }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val rootView = super.onCreateView(inflater, container, savedInstanceState)
        fastScroller = rootView.findViewById(R.id.fast_scroller)
        val manager = AutoGridLayoutManager(activity, 1)
        LayoutSizeObserver.attach(fastScroller, LayoutSizeObserver.WIDTH, object : LayoutSizeObserver.OnChange {
            override fun onChange(view: View, watchFlags: Int, rect: Rect) {
                manager.spanCount = getColumnCount(rect.width())
            }
        })
        drawer.layoutManager = manager
        drawer.itemAnimator = null
        Measurements.addStatusBarListener { fastScroller.setTopOffset(drawer.paddingTop) }
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val searchContainer = search.parent as View
        search.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            if (Measurements.isLandscape() && view.measuredWidth >
                Measurements.spToPx(200f) + searchContainer.measuredWidth) {
                fastScroller.setBottomOffset(searchContainer.paddingBottom)
            } else {
                fastScroller.setBottomOffset(searchContainer.paddingBottom + (bottom - top) +
                    Measurements.spToPx(32f) + Measurements.dpToPx(20f))
            }
        }
    }

    override fun setSelected(selected: Boolean) {
        if (isSelected() == selected) {
            super.setSelected(selected)
            return
        }
        super.setSelected(selected)
        fastScroller.animateVisibility(selected)
    }

    override fun onResume() {
        if (ProfileManager.getInstance().getProfiles().size <= 1) {
            title.setText(R.string.apps)
        } else {
            title.setText(if (Utils.isMainProfile(handle)) R.string.personal else R.string.managed)
        }
        super.onResume()
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        if (savedInstanceState?.containsKey(USER_HANDLE_KEY) == true) {
            handle = if (Utils.isMinimumSDK(Build.VERSION_CODES.TIRAMISU)) {
                savedInstanceState.getParcelable(USER_HANDLE_KEY, UserHandle::class.java)
            } else {
                @Suppress("DEPRECATION")
                savedInstanceState.getParcelable(USER_HANDLE_KEY)
            }
        }
        val applicationManager = ProfileManager.getInstance().getProfile(handle)
        val adapter: AsyncRecyclerAdapter<*> = ListAdapter(activity, applicationManager)
        LayoutSizeObserver.attach(drawer, LayoutSizeObserver.WIDTH, object : LayoutSizeObserver.OnChange {
            private val columnCount = ObservableObject(0) { _: Int? -> adapter.approximateRecyclerHeight() }
            override fun onChange(view: View, watchFlags: Int, rect: Rect) {
                columnCount.updateObject(getColumnCount(rect.width()))
            }
        })
        adapter.setRecyclerHeightApproximationListener { height ->
            val parent = drawer.parent as ViewGroup
            val params = drawer.layoutParams as ConstraintLayout.LayoutParams
            if (parent.measuredHeight < height) {
                if (params.height == ConstraintLayout.LayoutParams.WRAP_CONTENT) {
                    params.height = ConstraintLayout.LayoutParams.MATCH_PARENT
                    drawer.requestLayout()
                }
            } else if (params.height == ConstraintLayout.LayoutParams.MATCH_PARENT) {
                params.height = ConstraintLayout.LayoutParams.WRAP_CONTENT
                drawer.requestLayout()
            }
            drawer.requestLayout()
        }
        drawer.adapter = adapter
        applicationManager?.addOnReadyListener { showLayout() }
        super.onViewStateRestored(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(USER_HANDLE_KEY, handle)
        super.onSaveInstanceState(outState)
    }

    override fun getLayoutResID(): Int = R.layout.drawer_list
    open fun getUserHandle(): UserHandle? = handle
}
