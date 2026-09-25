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

package com.farol.launcher.sheet.drawer

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.farol.launcher.R
import com.farol.launcher.sheet.SheetDialogFragment
import com.farol.launcher.sheet.SheetType
import com.farol.launcher.sheet.drawer.dialog.ApplicationsDialog
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.ui.Measurements
import com.farol.launcher.ui.recyclers.overscroll.OverScrollEffect
import com.farol.launcher.ui.recyclers.overscroll.OverScrollRecyclerView
import com.farol.launcher.ui.utils.animation.Animation

abstract class DrawerPage : Fragment(), ScrollToTop {
    private lateinit var titleContainer: RelativeLayout
    private var selected = false
    private var root: ViewGroup? = null

    protected lateinit var drawer: OverScrollRecyclerView
    protected lateinit var activity: ThemedActivity
    protected lateinit var search: EditText
    protected lateinit var title: TextView

    override fun onAttach(context: Context) {
        if (context !is ThemedActivity) {
            throw RuntimeException("Parent activity is not of type ThemedActivity.")
        }
        activity = context
        super.onAttach(context as Context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val rootView = inflater.inflate(getLayoutResID(), container, false) as ViewGroup
        root = rootView
        if (hideInflatedLayout()) {
            rootView.visibility = View.GONE
            rootView.alpha = 0f
        }

        titleContainer = rootView.findViewById(R.id.title_container)
        drawer = rootView.findViewById(R.id.drawer)
        title = rootView.findViewById(R.id.title)
        search = requireNotNull(container).rootView.findViewById(R.id.search)
        drawer.overScrollMode = View.OVER_SCROLL_ALWAYS
        drawer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateTitleTransforms(drawer) }
        drawer.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateTitleTransforms(drawer)
            }
        })

        if (SheetType.getSheetTypeForSheetDialogFragment(activity, ApplicationsDialog::class.java) == SheetType.BOTTOM_SHEET) {
            drawer.setOverscrollPullEdges(OverScrollEffect.PULL_EDGE_BOTTOM)
        }

        drawer.addOnOverScrollListener(object : OverScrollEffect.OnOverScrollListener {
            private var receivedBottomOverscrollEvent = false
            private var receivedTopOverscrollEvent = false
            private var factor = 0f

            override fun onOverScrollStateChanged(edge: Int, state: OverScrollEffect.OverScrollState) {
                if (state == OverScrollEffect.OverScrollState.SETTLING) {
                    if ((receivedTopOverscrollEvent && edge == OverScrollEffect.PULL_EDGE_TOP) ||
                        (receivedBottomOverscrollEvent && edge == OverScrollEffect.PULL_EDGE_BOTTOM)) {
                        if (factor * drawer.measuredHeight > Measurements.dpToPx(100f)) {
                            (parentFragment as? SheetDialogFragment)?.hide(true)
                        }
                    }
                    receivedBottomOverscrollEvent = false
                    receivedTopOverscrollEvent = false
                } else if (state == OverScrollEffect.OverScrollState.IDLE) {
                    receivedBottomOverscrollEvent = false
                    receivedTopOverscrollEvent = false
                } else if (state == OverScrollEffect.OverScrollState.OVER_SCROLLING) {
                    if (edge == OverScrollEffect.PULL_EDGE_TOP) {
                        receivedTopOverscrollEvent = true
                    } else if (edge == OverScrollEffect.PULL_EDGE_BOTTOM) {
                        receivedBottomOverscrollEvent = true
                    }
                }
            }

            override fun onOverScrolled(edge: Int, factor: Float) {
                this.factor = factor
            }
        })

        titleContainer.layoutParams.height = Measurements.dpToPx(Measurements.HEADER_SIZE_DP.toFloat()) + Measurements.spToPx(8f)
        Measurements.addStatusBarListener { updated ->
            val value = requireNotNull(updated)
            drawer.setPadding(drawer.paddingLeft,
                value + (if (Measurements.isLandscape()) Measurements.getDefaultPadding() else
                    Measurements.dpToPx(Measurements.HEADER_SIZE_DP.toFloat()) + Measurements.getDefaultPadding()),
                drawer.paddingRight, drawer.paddingBottom)
            (titleContainer.layoutParams as ViewGroup.MarginLayoutParams).topMargin = value
        }

        val searchContainer = search.parent as View
        drawer.setPadding(drawer.paddingLeft, drawer.paddingTop, drawer.paddingRight,
            searchContainer.paddingBottom + (search.bottom - search.top))
        search.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            drawer.setPadding(drawer.paddingLeft, drawer.paddingTop, drawer.paddingRight,
                searchContainer.paddingBottom + (bottom - top))
        }
        updateTitleTransforms(drawer)
        return rootView
    }

    open fun hideInflatedLayout(): Boolean = true

    protected fun showLayout() {
        val rootView = root
        if (hideInflatedLayout() && rootView != null) {
            rootView.visibility = View.VISIBLE
            rootView.post {
                rootView.animate().alpha(1f).setDuration(Animation.LONG.getDuration().toLong())
                    .setInterpolator(FastOutLinearInInterpolator())
            }
        }
    }

    open fun setSelected(selected: Boolean) {
        this.selected = selected
    }

    fun isSelected(): Boolean = selected

    protected fun updateTitleTransforms(recyclerView: RecyclerView) {
        titleContainer.post {
            val translation = recyclerView.computeVerticalScrollOffset()
            titleContainer.translationY = -translation / 2f
            val alpha = 1f - translation / (Measurements.dpToPx(Measurements.HEADER_SIZE_DP.toFloat()) / 2f)
            if (alpha > 0 && !Measurements.isLandscape()) {
                titleContainer.alpha = alpha
                titleContainer.visibility = View.VISIBLE
            } else {
                titleContainer.visibility = View.GONE
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateTitleTransforms(drawer)
    }

    override fun onResume() {
        updateTitleTransforms(drawer)
        super.onResume()
    }

    override fun scrollToTop() {
        if (::drawer.isInitialized) drawer.scrollToPosition(0)
    }

    override fun onDestroyView() {
        drawer.adapter = null
        super.onDestroyView()
    }

    protected abstract fun getLayoutResID(): Int
}
