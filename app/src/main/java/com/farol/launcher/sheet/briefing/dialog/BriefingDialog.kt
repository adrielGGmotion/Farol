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

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.farol.launcher.R
import com.farol.launcher.preferences.Vibrations
import com.farol.launcher.sheet.SheetDialogFragment
import com.farol.launcher.sheet.SheetType
import com.farol.launcher.sheet.briefing.configurator.BriefingConfigurator
import com.farol.launcher.sheet.briefing.configurator.FeedConfigurator
import com.farol.launcher.sheet.briefing.dialog.page.FeedPage
import com.farol.launcher.sheet.briefing.dialog.page.feed.BriefingFeedList
import com.farol.launcher.sheet.briefing.dialog.page.feed.Feed
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.ui.Measurements
import com.farol.launcher.ui.common.pager.CustomDurationViewPager
import com.farol.launcher.ui.common.tabs.LeftTabLayout
import com.farol.launcher.ui.popup.PopupMenu
import java.util.Objects

open class BriefingDialog : SheetDialogFragment {
    private var recyclerToBeObserved: RecyclerView? = null
    private lateinit var scrollListener: RecyclerView.OnScrollListener
    private lateinit var layoutListener: View.OnLayoutChangeListener
    private lateinit var feedListener: BriefingFeedList.FeedListener
    private var listener: BriefingDialogPageListener? = null
    private lateinit var pager: CustomDurationViewPager
    private lateinit var activity: ThemedActivity
    private lateinit var adapter: BriefingAdapter
    private lateinit var list: BriefingFeedList
    private lateinit var placeholder: ViewGroup
    private lateinit var tabsContainer: View
    private lateinit var tabs: LeftTabLayout
    private lateinit var main: ViewGroup
    private lateinit var title: View
    private lateinit var root: View

    constructor() : super() {
        init()
    }

    constructor(type: SheetType) : super(type) {
        init()
    }

    private fun init() {
        recyclerToBeObserved = null
        scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateHeader(recyclerView)
            }
        }
        layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateHeader(recyclerToBeObserved)
        }
        feedListener = object : BriefingFeedList.FeedListener {
            private fun notifyUpdate() {
                adapter.notifyDataSetChanged()
                tabs.setViewPager(pager)

                if (adapter.getCount() > 0) {
                    pager.setCurrentItem(0, true)
                    observePageRecycler(0)
                } else {
                    observePageRecycler(null)
                }

                updateHeader(recyclerToBeObserved)

                if (adapter.getCount() > 0) {
                    placeholder.setVisibility(View.GONE)
                    main.setVisibility(View.VISIBLE)
                } else {
                    placeholder.setVisibility(View.VISIBLE)
                    main.setVisibility(View.GONE)
                }
            }

            override fun onInserted(index: Int) {
                notifyUpdate()
            }

            override fun onRemoved(index: Int) {
                notifyUpdate()
            }

            override fun onUpdated(index: Int) {
                notifyUpdate()
            }
        }
    }

    override fun requiresEagerInitialization(): Boolean = false

    override fun onAttach(context: Context) {
        super.onAttach(context)

        activity = context as ThemedActivity
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        root = inflater.inflate(R.layout.briefing, container, false)

        title = root.findViewById(R.id.title_feeds)
        placeholder = root.findViewById(R.id.placeholder)
        pager = root.findViewById(R.id.articles_container)
        tabs = root.findViewById(R.id.tabs)
        main = root.findViewById(R.id.main)

        title.setHapticFeedbackEnabled(false)
        main.setHapticFeedbackEnabled(false)

        pager.setOffscreenPageLimit(2)
        pager.setOverScrollMode(View.OVER_SCROLL_NEVER)
        pager.setSaveEnabled(false)
        val pageListener = BriefingDialogPageListener()
        listener = pageListener
        pager.addOnPageChangeListener(pageListener)

        adapter = BriefingAdapter(activity, getChildFragmentManager())
        pager.setAdapter(adapter)

        pager.post {
            if (adapter.getCount() > 0) {
                observePageRecycler(pager.getCurrentItem())
            }
        }

        list = BriefingFeedList.from(activity)
        list.addOnFeedUpdateListener(feedListener)

        tabs.setOverScrollMode(View.OVER_SCROLL_NEVER)
        tabs.setOnTabLongClickListener { tab, position ->
            Vibrations.getInstance().vibrate()
            val resources: Resources = activity.getResources()

            val menu = PopupMenu(activity)

            menu.add(
                PopupMenu.Item(
                    resources.getString(R.string.remove),
                    ResourcesCompat.getDrawable(
                        resources,
                        R.drawable.ic_delete,
                        activity.getTheme()
                    ),
                    View.OnClickListener { list.remove(position) }
                )
            )

            val feed: Feed = list.get(position)
            menu.add(
                PopupMenu.Item(
                    resources.getString(R.string.rename_feed),
                    ResourcesCompat.getDrawable(
                        resources,
                        R.drawable.ic_edit,
                        activity.getTheme()
                    ),
                    View.OnClickListener { FeedConfigurator(activity, feed).show() }
                )
            )

            menu.show(activity, tab, PopupMenu.PIVOT_CENTER_HORIZONTAL)
        }
        tabs.setViewPager(pager)

        tabsContainer = tabs.getParent() as View

        setOnBackPressed {
            hide(true)
            true
        }

        title.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val recycler = recyclerToBeObserved
            if (recycler != null) {
                recycler.post { updateHeader(recyclerToBeObserved) }
            }
        }

        val clickListener = object : View.OnClickListener {
            private var configurator: BriefingConfigurator? = null

            override fun onClick(view: View) {
                if (configurator == null) {
                    configurator = BriefingConfigurator(activity)
                }

                configurator!!.show()
            }
        }

        title.setOnClickListener(clickListener)
        placeholder.setOnClickListener(clickListener)
        placeholder.findViewById<View>(R.id.add_button).setOnClickListener(clickListener)

        title.setOnLongClickListener { false }
        title.setOnTouchListener { _, event ->
            Objects.requireNonNull(getBehavior())!!.interceptTouches(
                event.getAction() != MotionEvent.ACTION_CANCEL &&
                    event.getAction() != MotionEvent.ACTION_UP
            )
            false
        }

        Measurements.addStatusBarListener { value -> root.setPadding(0, value!!, 0, 0) }
        Measurements.addNavListener { value -> placeholder.setPadding(0, 0, 0, value!!) }

        return root
    }

    override fun onDestroy() {
        if (::list.isInitialized) {
            list.removeOnFeedUpdateListener(feedListener)
        }

        super.onDestroy()
    }

    private fun observePageRecycler(position: Int?) {
        val page = if (position != null) adapter.getRegisteredFragment(position) else null

        recyclerToBeObserved?.let {
            it.removeOnScrollListener(scrollListener)
            it.removeOnLayoutChangeListener(layoutListener)
        }

        if (page != null) {
            recyclerToBeObserved = page.getRecycler()

            recyclerToBeObserved?.let {
                it.addOnScrollListener(scrollListener)
                it.addOnLayoutChangeListener(layoutListener)
            }
        } else {
            recyclerToBeObserved = null
        }
    }

    private fun updateHeader(recycler: RecyclerView?) {
        if (recycler == null) {
            updateHeader(0f)
            return
        }

        val layoutManager = recycler.getLayoutManager()
        if (layoutManager == null) {
            updateHeader(0f)
            return
        }

        val firstView = layoutManager.findViewByPosition(0)
        var offset = 0

        if (firstView != null) {
            val padding = recycler.getPaddingTop() + Measurements.dpToPx(10f)
            offset = Math.max(0, padding - firstView.getTop())
        } else if (layoutManager.getItemCount() > 0) {
            offset = title.getMeasuredHeight()
        }

        updateHeader(offset.toFloat())
    }

    private fun updateHeader(translation: Float) {
        val maxTranslation = title.getMeasuredHeight()
        val adjustedTranslation = -Math.min(maxTranslation.toFloat(), translation)

        tabsContainer.setTranslationY(maxTranslation + adjustedTranslation)
        title.setTranslationY(adjustedTranslation)

        val alpha = (adjustedTranslation + maxTranslation / 2f) / maxTranslation * 2f

        title.setAlpha(alpha)
        title.setVisibility(if (alpha > 0) View.VISIBLE else View.INVISIBLE)
    }

    private fun updateTitleHeight() {
        if (Measurements.isLandscape()) {
            title.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT
        } else {
            title.getLayoutParams().height = Measurements.dpToPx(Measurements.HEADER_SIZE_DP.toFloat())
        }

        listener?.reset()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        updateTitleHeight()
    }

    override fun onResume() {
        updateTitleHeight()

        if (adapter.getCount() > 0) {
            placeholder.setVisibility(View.GONE)
            main.setVisibility(View.VISIBLE)
        } else {
            placeholder.setVisibility(View.VISIBLE)
            main.setVisibility(View.GONE)
        }

        super.onResume()
    }

    inner class BriefingDialogPageListener : ViewPager.OnPageChangeListener {
        private var startingTranslationTitle = Float.NaN
        private var startingTranslationTabs = Float.NaN
        private var startingAlphaTitle = Float.NaN
        private var currentOffset = 0f
        private var startingPosition = -1
        private var currentPosition = 0

        fun reset() {
            startingTranslationTitle = Float.NaN
            startingTranslationTabs = Float.NaN
            startingAlphaTitle = Float.NaN

            onPageScrolled(currentPosition, currentOffset, 0)
        }

        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            currentPosition = position
            currentOffset = positionOffset

            var adjustedPositionOffset = positionOffset
            if (position < startingPosition) {
                adjustedPositionOffset = if (startingPosition - position > 1) {
                    1f
                } else {
                    1 - positionOffset
                }
            } else if (position > startingPosition) {
                adjustedPositionOffset = 1f
            }

            if (startingTranslationTitle.isNaN() ||
                startingTranslationTabs.isNaN() ||
                startingAlphaTitle.isNaN()
            ) {
                tabsContainer.setTranslationY(
                    (1 - adjustedPositionOffset) + title.getMeasuredHeight() * adjustedPositionOffset
                )

                title.setTranslationY(1 - adjustedPositionOffset)
                title.setAlpha(adjustedPositionOffset)
            } else {
                tabsContainer.setTranslationY(
                    startingTranslationTabs * (1 - adjustedPositionOffset) +
                        title.getMeasuredHeight() * adjustedPositionOffset
                )

                title.setTranslationY(startingTranslationTitle * (1 - adjustedPositionOffset))
                title.setAlpha(
                    startingAlphaTitle + (1 - startingAlphaTitle) * adjustedPositionOffset
                )
            }

            title.setVisibility(if (title.getAlpha() > 0) View.VISIBLE else View.INVISIBLE)
        }

        override fun onPageSelected(position: Int) {
            observePageRecycler(position)
        }

        override fun onPageScrollStateChanged(state: Int) {
            if (state == ViewPager.SCROLL_STATE_DRAGGING ||
                state == ViewPager.SCROLL_STATE_SETTLING
            ) {
                if (startingPosition == -1) {
                    startingPosition = if (currentOffset > 0.5f) {
                        currentPosition + 1
                    } else {
                        currentPosition
                    }
                }

                if (startingAlphaTitle.isNaN()) {
                    startingAlphaTitle = title.getAlpha()
                }

                if (startingTranslationTitle.isNaN()) {
                    startingTranslationTitle = title.getTranslationY()
                }

                if (startingTranslationTabs.isNaN()) {
                    startingTranslationTabs = tabsContainer.getTranslationY()
                }
            } else if (state == ViewPager.SCROLL_STATE_IDLE) {
                startingTranslationTitle = Float.NaN
                startingTranslationTabs = Float.NaN
                startingAlphaTitle = Float.NaN
                startingPosition = -1

                adapter.reset(pager.getCurrentItem())
            }
        }
    }

    companion object {
        @JvmStatic
        fun getName(): String = "Briefing"
    }
}
