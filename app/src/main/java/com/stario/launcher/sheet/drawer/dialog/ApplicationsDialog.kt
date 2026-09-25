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

package com.stario.launcher.sheet.drawer.dialog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RelativeLayout
import android.window.OnBackInvokedDispatcher
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.transition.Transition
import androidx.transition.TransitionListenerAdapter
import androidx.viewpager.widget.ViewPager
import com.stario.launcher.R
import com.stario.launcher.preferences.Entry
import com.stario.launcher.sheet.SheetDialogFragment
import com.stario.launcher.sheet.SheetType
import com.stario.launcher.sheet.behavior.SheetBehavior
import com.stario.launcher.sheet.drawer.DrawerAdapter
import com.stario.launcher.sheet.drawer.DrawerPage
import com.stario.launcher.sheet.drawer.category.Categories
import com.stario.launcher.sheet.drawer.search.SearchEngine
import com.stario.launcher.sheet.drawer.search.SearchFragment
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.Measurements
import com.stario.launcher.ui.common.FadingEdgeLayout
import com.stario.launcher.ui.utils.animation.Animation
import com.stario.launcher.ui.utils.animation.FragmentTransition
import com.stario.launcher.utils.Utils
import androidx.core.content.edit

open class ApplicationsDialog : SheetDialogFragment {
    companion object {
        const val INTENT_LAUNCH_SEARCH = "com.stario.INTENT_LAUNCH_SEARCH"
        private const val APPLICATIONS_PAGE = "com.stario.APPLICATIONS_PAGE"
        @JvmStatic fun getName(): String = "Application Drawer"
    }

    private lateinit var launchSearchReceiver: BroadcastReceiver
    private lateinit var popStackReceiver: BroadcastReceiver
    private var searchFragment: SearchFragment? = null
    private lateinit var activity: ThemedActivity
    private var listener: ResumeListener? = null
    private lateinit var fader: FadingEdgeLayout
    private var swipeDrawable: Drawable? = null
    private lateinit var adapter: DrawerAdapter
    private lateinit var pager: ViewPager
    private lateinit var search: EditText

    constructor() : super()
    constructor(type: SheetType) : super(type)

    override fun requiresEagerInitialization(): Boolean = true

    override fun onAttach(context: Context) {
        super.onAttach(context)
        activity = context as ThemedActivity
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.drawer, container, false) as ViewGroup
        fader = root.findViewById(R.id.fader)
        pager = root.findViewById(R.id.pager)
        search = root.findViewById(R.id.search)
        val searchContainer = search.parent as ViewGroup

        setOnBackPressed {
            getBehavior()?.setDraggable(true)
            val popped = childFragmentManager.popBackStackImmediate(SearchFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            if (popped || (pager.currentItem == DrawerAdapter.CATEGORIES_POSITION && adapter.collapse())) {
                false
            } else {
                hide(true)
                true
            }
        }

        addOnShowListener {
            getBehavior()?.addSheetCallback(object : SheetBehavior.SheetCallback {
                override fun onSlide(sheet: View, slideOffset: Float) {
                    when (getType()) {
                        SheetType.BOTTOM_SHEET -> {
                            searchContainer.translationY = (1f - slideOffset) *
                                -(Measurements.dpToPx(SheetBehavior.COLLAPSED_DELTA_DP.toFloat()) - search.measuredHeight * 2)
                            searchContainer.translationX = 0f
                        }
                        SheetType.LEFT_SHEET -> {
                            searchContainer.translationY = 0f
                            searchContainer.translationX = (1f - slideOffset) * search.measuredWidth / 4
                        }
                        SheetType.RIGHT_SHEET -> {
                            searchContainer.translationY = 0f
                            searchContainer.translationX = (slideOffset - 1f) *
                                -(Measurements.dpToPx(SheetBehavior.COLLAPSED_DELTA_DP.toFloat()) - search.measuredHeight * 2)
                        }
                        else -> Unit
                    }
                }

                override fun onStateChanged(sheet: View, newState: Int) {
                    if (newState == SheetBehavior.STATE_COLLAPSED) {
                        if (isAdded) {
                            try {
                                childFragmentManager.popBackStackImmediate(SearchFragment.TAG,
                                    FragmentManager.POP_BACK_STACK_INCLUSIVE)
                                requireNotNull(getBehavior()).setDraggable(true)
                            } catch (exception: Exception) {
                                Log.e("ApplicationsDialog", "onStateChanged: " + exception.message)
                            }
                        }
                        try {
                            adapter.reset()
                        } catch (exception: Exception) {
                            setOnResumeListener {
                                adapter.reset()
                                setOnResumeListener(null)
                            }
                        }
                    } else if (newState == SheetBehavior.STATE_EXPANDED) {
                        searchContainer.translationY = 0f
                        searchContainer.translationX = 0f
                    }
                }

                override fun hashCode(): Int = 2345678
                override fun equals(other: Any?): Boolean =
                    other is SheetBehavior.SheetCallback && other.hashCode() == hashCode()
            })
        }

        Measurements.addNavListener { updated ->
            val value = requireNotNull(updated)
            searchContainer.setPadding(searchContainer.paddingLeft, searchContainer.paddingTop,
                searchContainer.paddingRight, value + Measurements.dpToPx(20f))
            fader.setFadeSizes(if (Measurements.isLandscape()) Measurements.getSysUIHeight()
                else Measurements.dpToPx(Measurements.HEADER_SIZE_DP / 2f),
                0, value + Measurements.getDefaultPadding() + search.measuredHeight, 0)
        }
        search.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            fader.setFadeSizes(if (Measurements.isLandscape()) Measurements.getSysUIHeight()
                else Measurements.dpToPx(Measurements.HEADER_SIZE_DP / 2f),
                0, Measurements.getNavHeight() + Measurements.getDefaultPadding() + search.measuredHeight, 0)
        }
        search.inputType = 0
        search.isFocusable = false
        search.isFocusableInTouchMode = false
        search.setOnClickListener { showSearch(true) }
        return root
    }

    private fun showSearch(animate: Boolean) {
        val manager = childFragmentManager
        val transaction = manager.beginTransaction()
        if (searchFragment == null) {
            searchFragment = SearchFragment()
            if (Utils.isMinimumSDK(Build.VERSION_CODES.TIRAMISU)) {
                dialog?.let { dialog ->
                    dialog.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_OVERLAY) {
                        if (!requireNotNull(searchFragment).onBackPressed()) {
                            @Suppress("DEPRECATION")
                            dialog.onBackPressed()
                            getBehavior()?.setDraggable(true)
                        }
                    }
                }
            }
        }
        val fragment = requireNotNull(searchFragment)
        for (target in manager.fragments) if (target === fragment) return

        val enterDuration = if (animate) Animation.MEDIUM.getDuration().toLong() else 0L
        val enterTransition = FragmentTransition(true)
            .excludeTarget(EditText::class.java, true)
            .excludeTarget(RelativeLayout::class.java, true)
        enterTransition.duration = enterDuration
        val exitTransition = FragmentTransition(true)
            .excludeTarget(EditText::class.java, true)
            .excludeTarget(RelativeLayout::class.java, true)
        exitTransition.duration = Animation.MEDIUM.getDuration().toLong()

        enterTransition.addListener(object : TransitionListenerAdapter() {
            override fun onTransitionStart(transition: Transition) {
                if (animate) {
                    fader.alpha = 1f
                    fader.animate().alpha(0f).translationY(-Measurements.getHeight().toFloat() / 2)
                        .setDuration(transition.duration).setInterpolator(transition.interpolator)
                        .withEndAction {
                            notifySelection(false)
                            fader.translationY = 0f
                            fader.scaleX = 0.9f
                            fader.scaleY = 0.9f
                        }
                } else {
                    fader.alpha = 0f
                    notifySelection(false)
                    fader.scaleX = 0.9f
                    fader.scaleY = 0.9f
                }
                search.visibility = View.GONE
            }
        })
        exitTransition.addListener(object : TransitionListenerAdapter() {
            override fun onTransitionStart(transition: Transition) {
                fader.alpha = 0f
                fader.scaleX = 0.9f
                fader.scaleY = 0.9f
                fader.animate().alpha(1f).scaleY(1f).scaleX(1f).translationY(0f)
                    .setDuration(transition.duration).setInterpolator(transition.interpolator)
                    .withEndAction { notifySelection(true) }
                search.visibility = View.VISIBLE
            }
        })
        fragment.enterTransition = enterTransition
        fragment.returnTransition = exitTransition
        transaction.setReorderingAllowed(true).addToBackStack(SearchFragment.TAG)
            .add(R.id.root, fragment).commit()
        getBehavior()?.setDraggable(false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        pager.overScrollMode = View.OVER_SCROLL_NEVER
        pager.isSaveEnabled = true
        adapter = DrawerAdapter(childFragmentManager)
        pager.offscreenPageLimit = 100
        pager.adapter = adapter
        pager.setPageTransformer(false) { page, position ->
            notifySelection(position == position.toInt().toFloat())
            page.translationX = if (kotlin.math.abs(position) > adapter.count - 3)
                -(adapter.count - 2) * page.width * kotlin.math.sign(position) else 0f
        }
        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            private var positionOffset = 0f
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                this.positionOffset = positionOffset
                if (position == 0 && positionOffset == 0f) pager.setCurrentItem(adapter.count - 2, false)
                if (position == adapter.count - 1) pager.setCurrentItem(1, false)
            }
            override fun onPageSelected(position: Int) {
                if (position > 0 && position < adapter.count - 1) {
                    activity.applicationContext.getSharedPreferences(Entry.DRAWER)
                        .edit {putInt(APPLICATIONS_PAGE, position)}
                }
            }
            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager.SCROLL_STATE_DRAGGING) {
                    swipeDrawable = null
                    updateSearchBarCompoundDrawables()
                }
            }
        })
        pager.setCurrentItem(activity.applicationContext.getSharedPreferences(Entry.DRAWER)
            .getInt(APPLICATIONS_PAGE, DrawerAdapter.CATEGORIES_POSITION), false)
    }

    private fun notifySelection(focused: Boolean) {
        val focusedFragment = adapter.getFragment(pager.currentItem)
        for (index in 0 until adapter.count) {
            val fragment = adapter.getFragment(index)
            if (fragment is DrawerPage) fragment.setSelected(focused && fragment == focusedFragment)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchSearchReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                expand()
                showSearch(false)
            }
        }
        popStackReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (::adapter.isInitialized) adapter.collapse()
            }
        }
        @Suppress("DEPRECATION")
        val manager = androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(activity)
        manager.registerReceiver(popStackReceiver, IntentFilter(Categories.FOLDER_STACK_ID))
        manager.registerReceiver(launchSearchReceiver, IntentFilter(INTENT_LAUNCH_SEARCH))
        swipeDrawable = AppCompatResources.getDrawable(activity, R.drawable.ic_swipe)
    }

    override fun onDestroy() {
        @Suppress("DEPRECATION")
        val manager = androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(activity)
        manager.unregisterReceiver(launchSearchReceiver)
        manager.unregisterReceiver(popStackReceiver)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        updateSearchBarCompoundDrawables()
        childFragmentManager.popBackStackImmediate(SearchFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        getBehavior()?.setDraggable(true)
        listener?.onResume()
    }

    private fun updateSearchBarCompoundDrawables() {
        val engine = SearchEngine.getEngine(activity.applicationContext)
        search.setCompoundDrawablesWithIntrinsicBounds(engine.getDrawable(activity), null, swipeDrawable, null)
    }

    private fun setOnResumeListener(listener: ResumeListener?) { this.listener = listener }
    private fun interface ResumeListener { fun onResume() }
}
