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

package com.stario.launcher.activities.settings.dialogs.hide

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.ogaclejapan.smarttablayout.SmartTabLayout
import com.stario.launcher.R
import com.stario.launcher.activities.settings.dialogs.hide.pager.HideApplicationsPage
import com.stario.launcher.activities.settings.dialogs.hide.pager.HideApplicationsPagerAdapter
import com.stario.launcher.apps.ProfileManager
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.dialogs.ActionDialog
import com.stario.launcher.ui.recyclers.overscroll.OverScrollRecyclerView
import com.stario.launcher.ui.utils.animation.Animation
import androidx.core.view.size

open class HideApplicationsDialog : DialogFragment() {
    private var hideListener: OnHideListener? = null
    private lateinit var themedActivity: ThemedActivity

    override fun onAttach(context: Context) {
        if (context !is ThemedActivity) {
            throw RuntimeException("Parent activity is not of type ThemedActivity.")
        }

        themedActivity = context
        super.onAttach(context as Context)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return object : ActionDialog(themedActivity) {
            override fun inflateContent(inflater: LayoutInflater, parent: android.view.ViewGroup): View {
                val root = inflater.inflate(R.layout.pop_up_hide, parent, false)

                val pager = root.findViewById<ViewPager>(R.id.pager)
                val adapter = HideApplicationsPagerAdapter(
                    this@HideApplicationsDialog.getChildFragmentManager(),
                    themedActivity.getResources()
                )

                val tabsContainer = root.findViewById<View>(R.id.tabs_container)
                val tabLayout = root.findViewById<SmartTabLayout>(R.id.tabs)

                pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
                    private val scrollListener: RecyclerView.OnScrollListener = object : RecyclerView.OnScrollListener() {
                        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                            if (dy > 0 && !recyclerView.canScrollVertically(1)) {
                                showTooltip(false)
                            } else if (dy < 0) {
                                showTooltip(true)
                            } else if (dy > 0) {
                                hideTooltip()
                            }
                        }
                    }
                    private var recyclerView: OverScrollRecyclerView? = null
                    private var contrastDrawable: Drawable? = null
                    private var hidden = tabsContainer.getTranslationY() != 0f

                    init {
                        val fader = root.findViewById<View>(R.id.contrast_fader)
                        if (fader != null) {
                            contrastDrawable = fader.getBackground()
                        }

                        pager.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                            val fragment = adapter.getRegisteredFragment(pager.getCurrentItem())
                            if (fragment != null) {
                                updateObservedRecycler(fragment.getRecycler())
                            }
                        }
                    }

                    @SuppressLint("ClickableViewAccessibility")
                    private fun updateObservedRecycler(recyclerView: OverScrollRecyclerView) {
                        val observedRecyclerView = this.recyclerView
                        if (observedRecyclerView != null) {
                            if (observedRecyclerView == recyclerView) {
                                return
                            }

                            observedRecyclerView.removeOnScrollListener(scrollListener)
                        }

                        recyclerView.addOnScrollListener(scrollListener)
                        this.recyclerView = recyclerView
                    }

                    override fun onPageScrollStateChanged(state: Int) {
                        if (state != ViewPager.SCROLL_STATE_IDLE) {
                            showTooltip(true)
                        }
                    }

                    override fun onPageSelected(position: Int) {
                        val fragment = adapter.getRegisteredFragment(position)
                        if (fragment != null) {
                            updateObservedRecycler(fragment.getRecycler())
                        }
                    }

                    private fun hideTooltip() {
                        if (!hidden) {
                            tabsContainer.animate()
                                .translationY(tabsContainer.getMeasuredHeight().toFloat())
                                .setDuration(Animation.MEDIUM.getDuration().toLong())

                            hidden = true
                        }
                    }

                    private fun showTooltip(enforceContrast: Boolean) {
                        contrastDrawable?.setAlpha(if (enforceContrast) 255 else 0)

                        if (hidden) {
                            tabsContainer.animate()
                                .translationY(0f)
                                .setDuration(Animation.MEDIUM.getDuration().toLong())

                            hidden = false
                        }
                    }
                })

                pager.setAdapter(adapter)
                if (ProfileManager.from(themedActivity.getApplicationContext(), false).getProfiles().size > 1) {
                    tabLayout.setViewPager(pager)
                } else {
                    tabLayout.setVisibility(View.GONE)
                }

                return root
            }

            override fun getDesiredInitialState(): Int = BottomSheetBehavior.STATE_EXPANDED

            override fun blurBehind(): Boolean = true

            override fun hide() {
                hideListener?.onHide()
            }
        }
    }

    override fun onStop() {
        dismissAllowingStateLoss()
        super.onStop()
    }

    open fun show() {
        val dialog = getDialog()
        if (dialog != null) {
            dialog.show()
        }
    }

    open fun setOnHideListener(listener: OnHideListener?) {
        hideListener = listener
    }

    fun interface OnHideListener {
        fun onHide()
    }
}
