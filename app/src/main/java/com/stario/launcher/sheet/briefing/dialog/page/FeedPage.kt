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

package com.stario.launcher.sheet.briefing.dialog.page

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.prof18.rssparser.model.RssItem
import com.stario.launcher.R
import com.stario.launcher.sheet.SheetType
import com.stario.launcher.sheet.briefing.dialog.BriefingDialog
import com.stario.launcher.sheet.briefing.dialog.page.feed.BriefingFeedList
import com.stario.launcher.sheet.briefing.rss.RSSHelper
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.Measurements
import com.stario.launcher.ui.common.scrollers.CustomSwipeRefreshLayout
import com.stario.launcher.ui.recyclers.RecyclerItemAnimator
import com.stario.launcher.ui.recyclers.managers.ScrollControlStaggeredGridLayoutManager
import com.stario.launcher.ui.recyclers.overscroll.OverScrollEffect
import com.stario.launcher.ui.recyclers.overscroll.OverScrollRecyclerView
import com.stario.launcher.ui.utils.LayoutSizeObserver
import com.stario.launcher.ui.utils.UiUtils
import com.stario.launcher.ui.utils.animation.Animation
import com.stario.launcher.utils.Utils
import java.util.concurrent.Future

open class FeedPage : Fragment {
    private var swipeRefreshLayout: CustomSwipeRefreshLayout? = null
    private var manager: ScrollControlStaggeredGridLayoutManager? = null
    private var recyclerView: OverScrollRecyclerView? = null
    private var themedActivity: ThemedActivity? = null
    private var adapter: FeedPageAdapter? = null
    private var exceptionView: ViewGroup? = null
    private var fetchingView: TextView? = null
    private var runningTask: Future<*>? = null
    private var position = 0
    private var title: View? = null
    private var tabs: View? = null

    constructor() : super()

    constructor(position: Int) : this() {
        this.position = position
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(FEED_POSITION, position)

        super.onSaveInstanceState(outState)
    }

    override fun onAttach(context: Context) {
        if (context !is ThemedActivity) {
            throw RuntimeException("Parent activity is not of type ThemedActivity.")
        }

        themedActivity = context

        super.onAttach(context as Context)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (savedInstanceState != null) {
            position = savedInstanceState.getInt(FEED_POSITION, -1)
        }

        val root = inflater.inflate(R.layout.articles, container, false)
        val containerRoot = container!!.getRootView()
        val title = containerRoot.findViewById<View>(R.id.title_feeds)
        val tabs = containerRoot.findViewById<View>(R.id.tabs)
        this.title = title
        this.tabs = tabs

        val recyclerView = root.findViewById<OverScrollRecyclerView>(R.id.recycler_view)
        val swipeRefreshLayout = root.findViewById<CustomSwipeRefreshLayout>(R.id.refresh)
        val exceptionView = root.findViewById<ViewGroup>(R.id.exception)
        val fetchingView = root.findViewById<TextView>(R.id.fetching)
        this.recyclerView = recyclerView
        this.swipeRefreshLayout = swipeRefreshLayout
        this.exceptionView = exceptionView
        this.fetchingView = fetchingView

        recyclerView.setItemAnimator(
            RecyclerItemAnimator(RecyclerItemAnimator.APPEARANCE, Animation.EXTENDED)
        )

        val activity = checkNotNull(themedActivity)
        val type = SheetType.getSheetTypeForSheetDialogFragment(activity, BriefingDialog::class.java)!!
        if (type.getAxes() == View.SCROLL_AXIS_HORIZONTAL) {
            recyclerView.setOverscrollPullEdges(OverScrollEffect.PULL_EDGE_BOTTOM)
        }

        invalidateLayoutPadding()
        Measurements.addNavListener { value ->
            recyclerView.setPadding(
                recyclerView.getPaddingLeft(),
                recyclerView.getPaddingTop(),
                recyclerView.getPaddingRight(),
                value!!
            )
        }
        title.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> invalidateLayoutPadding() }
        tabs.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> invalidateLayoutPadding() }

        Measurements.addStatusBarListener {
            recyclerView.setPadding(
                recyclerView.getPaddingLeft(),
                recyclerView.getPaddingTop(),
                recyclerView.getPaddingRight(),
                Measurements.getNavHeight()
            )
        }

        if (this.adapter == null) {
            this.adapter = FeedPageAdapter(activity.getApplicationContext())
        }
        val adapter = this.adapter!!

        val manager = ScrollControlStaggeredGridLayoutManager(0)
        this.manager = manager
        LayoutSizeObserver.attach(
            root,
            LayoutSizeObserver.WIDTH,
            object : LayoutSizeObserver.OnChange {
                override fun onChange(view: View, watchFlags: Int, rect: Rect) {
                    manager.setSpanCount(Math.max(1, rect.width() / Measurements.dpToPx(400f)))
                }
            }
        )
        manager.setItemPrefetchEnabled(true)

        recyclerView.setLayoutManager(manager)
        recyclerView.setAdapter(adapter)

        swipeRefreshLayout.setOnRefreshListener(this::update)
        swipeRefreshLayout.setOnEngageListener { engaged -> manager.setScrollEnabled(!engaged) }
        swipeRefreshLayout.setSize(SwipeRefreshLayout.LARGE)
        swipeRefreshLayout.setOverScrollMode(View.OVER_SCROLL_NEVER)

        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(
            activity.getAttributeData(com.google.android.material.R.attr.colorSurfaceContainer)
        )

        swipeRefreshLayout.setColorSchemeColors(
            activity.getAttributeData(com.google.android.material.R.attr.colorSecondary),
            activity.getAttributeData(com.google.android.material.R.attr.colorTertiary),
            activity.getAttributeData(androidx.appcompat.R.attr.colorPrimary)
        )

        root.findViewById<View>(R.id.refresh_button).setOnClickListener {
            UiUtils.post(this::update)
        }

        Measurements.addNavListener { bottomInset ->
            recyclerView.setPadding(
                recyclerView.getPaddingLeft(),
                recyclerView.getPaddingTop(),
                recyclerView.getPaddingRight(),
                bottomInset!!
            )
        }

        return root
    }

    fun invalidateLayoutPadding() {
        val title = title!!
        val tabs = tabs!!
        val recyclerView = recyclerView!!
        val exceptionView = exceptionView!!
        val fetchingView = fetchingView!!
        val swipeRefreshLayout = swipeRefreshLayout!!
        val titleHeight = title.getMeasuredHeight()
        val tabsHeight = tabs.getMeasuredHeight()

        recyclerView.setPadding(
            recyclerView.getPaddingLeft(),
            Measurements.dpToPx(15f) + titleHeight + tabsHeight,
            recyclerView.getPaddingRight(),
            Measurements.getNavHeight()
        )
        exceptionView.setPadding(0, (titleHeight + tabsHeight) / 2, 0, 0)
        fetchingView.setPadding(0, (titleHeight + tabsHeight) / 2, 0, 0)
        swipeRefreshLayout.setProgressViewOffset(
            true,
            titleHeight + tabsHeight,
            ((titleHeight + tabsHeight) * 1.5f).toInt()
        )
    }

    override fun onResume() {
        super.onResume()
        reset()

        UiUtils.post(this::update)
    }

    fun update() {
        val swipeRefreshLayout = swipeRefreshLayout!!
        val runningTask = runningTask
        if (runningTask != null && !runningTask.isDone) {
            swipeRefreshLayout.setRefreshing(false)
            return
        }

        val adapter = adapter
        val activity = checkNotNull(themedActivity)
        if (adapter == null || position < 0 ||
            position >= BriefingFeedList.from(activity).size()
        ) {
            showErrorState()
            return
        }

        if (!adapter.shouldUpdate()) {
            if (adapter.getItemCount() == 0) {
                showErrorState()
            } else {
                showContentState(false)
            }
            return
        }

        val manager = manager!!
        val exceptionView = exceptionView!!
        val recyclerView = recyclerView!!
        val fetchingView = fetchingView!!
        manager.setScrollEnabled(false)
        exceptionView.setVisibility(View.GONE)
        recyclerView.clearAnimation()

        if (adapter.getItemCount() == 0) {
            fetchingView.setVisibility(View.VISIBLE)
            swipeRefreshLayout.setVisibility(View.INVISIBLE)
            recyclerView.setAlpha(0f)
        } else {
            fetchingView.setVisibility(View.GONE)
            swipeRefreshLayout.setRefreshing(true)
        }

        this.runningTask = Utils.submitTask {
            val items: List<RssItem>? = RSSHelper.parse(
                BriefingFeedList.getInstance().get(position).getRSSLink()
            )

            UiUtils.post {
                if (items != null) {
                    adapter.update(items)

                    if (adapter.getItemCount() == 0) {
                        showErrorState()
                    } else {
                        showContentState(true)
                    }
                } else {
                    showErrorState()
                }
            }
        }
    }

    private fun showContentState(animate: Boolean) {
        val exceptionView = exceptionView!!
        val fetchingView = fetchingView!!
        val swipeRefreshLayout = swipeRefreshLayout!!
        val manager = manager!!
        val recyclerView = recyclerView!!
        exceptionView.setVisibility(View.GONE)
        fetchingView.setVisibility(View.GONE)
        swipeRefreshLayout.setVisibility(View.VISIBLE)

        manager.setScrollEnabled(true)
        swipeRefreshLayout.setRefreshing(false)

        if (recyclerView.getAlpha() == 0f) {
            recyclerView.setAlpha(1f)
            recyclerView.setScaleX(UPDATE_SCALE)
            recyclerView.setScaleY(UPDATE_SCALE)

            recyclerView.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(if (animate) Animation.MEDIUM.getDuration().toLong() else 0L)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        }
    }

    private fun showErrorState() {
        val exceptionView = exceptionView!!
        val fetchingView = fetchingView!!
        val swipeRefreshLayout = swipeRefreshLayout!!
        val recyclerView = recyclerView!!
        exceptionView.setVisibility(View.VISIBLE)
        fetchingView.setVisibility(View.GONE)
        swipeRefreshLayout.setVisibility(View.INVISIBLE)
        recyclerView.setAlpha(0f)

        swipeRefreshLayout.setRefreshing(false)
    }

    fun reset() {
        swipeRefreshLayout!!.setRefreshing(false)
        recyclerView!!.scrollBy(0, -Integer.MAX_VALUE)
    }

    fun getRecycler(): RecyclerView? = recyclerView

    companion object {
        const val FEED_POSITION = "com.stario.FeedTab.FEED_POSITION"
        private const val UPDATE_SCALE = 0.9f
    }
}
