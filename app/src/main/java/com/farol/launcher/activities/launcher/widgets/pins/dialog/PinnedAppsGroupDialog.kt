/*
 * Copyright (C) 2026 Răzvan Albu
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

package com.farol.launcher.activities.launcher.widgets.pins.dialog

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.RelativeLayout
import androidx.annotation.IntRange
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.farol.launcher.R
import com.farol.launcher.apps.Category
import com.farol.launcher.apps.LauncherApplication
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.ui.Measurements
import com.farol.launcher.ui.dialogs.PersistentFullscreenDialog
import com.farol.launcher.ui.utils.HomeWatcher
import com.farol.launcher.ui.utils.UiUtils
import com.farol.launcher.ui.utils.animation.Animation

open class PinnedAppsGroupDialog(
    private val activity: ThemedActivity,
    private val transitionListener: TransitionListener?
) : PersistentFullscreenDialog(activity, activity.getThemeResourceId(), true) {
    private val sourceLayoutChangeListener: View.OnLayoutChangeListener
    private val categoryChangeListener: Category.CategoryItemListener
    private val adapter: PinnedAppsGroupDialogRecyclerAdapter = PinnedAppsGroupDialogRecyclerAdapter(activity)
    private val manager: GridLayoutManager = GridLayoutManager(activity, 1)
    private val homeWatcher: HomeWatcher = HomeWatcher(activity)

    private var recyclerContainer: RelativeLayout? = null
    private var container: RelativeLayout? = null
    private var previousOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var allowDismissal = false
    private var recycler: RecyclerView? = null
    private var category: Category? = null
    private var source: View? = null
    private var skip = 0

    init {
        categoryChangeListener = object : Category.CategoryItemListener {
            private fun update() {
                if (isShowing) {
                    dismiss()
                }
            }

            override fun onInserted(application: LauncherApplication) = update()
            override fun onRemoved(application: LauncherApplication) = update()
            override fun onUpdated(application: LauncherApplication) = update()
            override fun onSwapped(index1: Int, index2: Int) = update()
        }
        sourceLayoutChangeListener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            updateRecyclerPositionInContainer(view)
        }

        val lifecycle = activity.lifecycle
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                dismiss(false)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                lifecycle.removeObserver(this)
            }
        })

        homeWatcher.setOnHomePressedListener { dismiss() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.pinned_apps_dialog)

        container = findViewById(R.id.container)
        val container = container
        if (container != null) {
            UiUtils.Notch.applyNotchMargin(container, UiUtils.Notch.Treatment.CENTER)
            container.setOnClickListener { dismiss() }
            Measurements.addNavListener { value ->
                val params = container.layoutParams as ViewGroup.MarginLayoutParams
                params.bottomMargin = value ?: 0
                container.requestLayout()
            }
            Measurements.addStatusBarListener { value ->
                val params = container.layoutParams as ViewGroup.MarginLayoutParams
                params.topMargin = value ?: 0
                container.requestLayout()
            }

            recyclerContainer = container.findViewById(R.id.recycler_container)
            recycler = container.findViewById(R.id.recycler)

            recyclerContainer?.clipToOutline = true

            adapter.setRecyclerHeightApproximationListener { invalidateRecycler() }

            recycler?.layoutManager = manager
            recycler?.itemAnimator = null
            recycler?.adapter = adapter
        } else {
            dismiss()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        homeWatcher.startWatch()
    }

    override fun onDetachedFromWindow() {
        homeWatcher.stopWatch()
        super.onDetachedFromWindow()
    }

    fun setCategory(category: Category?) {
        this.category?.removeCategoryItemListener(categoryChangeListener)
        this.category = category
        this.category?.addCategoryItemListener(categoryChangeListener)

        if (isShowing) {
            adapter.updateDataSnapshot(category, skip)
            manager.spanCount = invalidateRecycler()
        }
    }

    /**
     * Invalidates the recycler layout and calculates the span count.
     *
     * @return layout manager span count
     */
    private fun invalidateRecycler(): Int {
        var size = getItemCount()

        size = if (size < 3) {
            Math.max(1, size)
        } else if (size < 5) {
            2
        } else {
            3
        }

        val recycler = recycler
        val container = container
        if (recycler == null || container == null) {
            return size
        }

        val recyclerWidth = size * Measurements.dpToPx(ITEM_SIZE_DP) + recycler.paddingLeft + recycler.paddingRight
        val recyclerHeight = Math.min(
            Measurements.dpToPx(Measurements.HEADER_SIZE_DP.toFloat()),
            adapter.approximateRecyclerHeight()
        ) + recycler.paddingBottom + recycler.paddingTop

        val params = recycler.layoutParams
        val changed = params.width != recyclerWidth || params.height != recyclerHeight
        params.width = recyclerWidth
        params.height = recyclerHeight

        if (changed) {
            recycler.layoutParams = params
        }

        recycler.post { updateRecyclerPositionInContainer(source) }
        return size
    }

    private fun getItemCount(): Int = category?.let { Math.max(0, it.getSize() - skip) } ?: 0

    private fun updateRecyclerPositionInContainer(view: View?) {
        val recyclerContainer = recyclerContainer
        val recycler = recycler
        val container = container
        if (recyclerContainer == null || recycler == null || container == null || view == null) {
            return
        }

        if (container.width == 0 || container.height == 0) {
            return
        }

        val sourceLoc = IntArray(2)
        val containerLoc = IntArray(2)
        view.getLocationOnScreen(sourceLoc)
        container.getLocationOnScreen(containerLoc)

        val relativeSourceX = sourceLoc[0] - containerLoc[0]
        val relativeSourceY = sourceLoc[1] - containerLoc[1]

        val sourceCenterX = relativeSourceX + view.width / 2
        val sourceCenterY = relativeSourceY + view.height / 2

        val containerWidth = container.width
        val containerHeight = container.height

        val recyclerWidth = recycler.layoutParams.width
        val recyclerHeight = recycler.layoutParams.height

        if (recyclerWidth == 0 || recyclerHeight == 0) {
            return
        }

        val targetX = (sourceCenterX * (1 - CENTER_PIVOT_WEIGHT) + containerWidth / 2f * CENTER_PIVOT_WEIGHT).toInt()
        val targetY = (sourceCenterY * (1 - CENTER_PIVOT_WEIGHT) + containerHeight / 2f * CENTER_PIVOT_WEIGHT).toInt()

        var finalLeft = targetX - recyclerWidth / 2
        var finalTop = targetY - recyclerHeight / 2

        val padding = Measurements.getDefaultPadding()
        finalLeft = Math.max(padding, Math.min(finalLeft, containerWidth - recyclerWidth - padding))
        finalTop = Math.max(padding, Math.min(finalTop, containerHeight - recyclerHeight - padding))

        val params = recyclerContainer.layoutParams as ViewGroup.MarginLayoutParams
        if (params.leftMargin != finalLeft || params.topMargin != finalTop) {
            params.leftMargin = finalLeft
            params.topMargin = finalTop

            recyclerContainer.pivotX = Math.max(0, Math.min(sourceCenterX - finalLeft, recyclerWidth)).toFloat()
            recyclerContainer.pivotY = Math.max(0, Math.min(sourceCenterY - finalTop, recyclerHeight)).toFloat()
            recyclerContainer.layoutParams = params
        }
    }

    fun show(@IntRange(from = 0) skip: Int, source: View?) {
        val category = category
        if (isShowing || category == null || category.getSize() <= skip) {
            return
        }

        previousOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

        this.skip = Math.max(0, skip)
        adapter.updateDataSnapshot(category, skip)
        manager.spanCount = invalidateRecycler()

        this.source = source
        source?.addOnLayoutChangeListener(sourceLayoutChangeListener)
        source?.let {
            sourceLayoutChangeListener.onLayoutChange(it, 0, 0, 0, 0, 0, 0, 0, 0)
        }

        allowDismissal = true
        super.showDialog()

        val window = window
        window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

        UiUtils.post {
            recyclerContainer?.animate()
                ?.scaleX(1f)
                ?.scaleY(1f)
                ?.alpha(1f)
                ?.setDuration(Animation.MEDIUM.duration.toLong())
                ?.setUpdateListener { valueAnimator -> setDimmingFactor(valueAnimator.animatedFraction) }
                ?.setInterpolator(DecelerateInterpolator(2.5f))
        }
    }

    override fun dismiss() {
        dismiss(true)
    }

    fun dismiss(animate: Boolean) {
        if (!allowDismissal) {
            return
        }

        val window = window
        val recyclerContainer = recyclerContainer
        if (window != null && recyclerContainer != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

            allowDismissal = false
            if (animate) {
                recyclerContainer.animate()
                    .scaleX(SCALE_FACTOR)
                    .scaleY(SCALE_FACTOR)
                    .alpha(0f)
                    .setDuration(Animation.MEDIUM.duration.toLong())
                    .setInterpolator(AccelerateInterpolator(2f))
                    .setUpdateListener { valueAnimator -> setDimmingFactor(1f - valueAnimator.animatedFraction) }
                    .withEndAction {
                        if (!allowDismissal) {
                            window.decorView.post {
                                if (!activity.isDestroyed) {
                                    activity.requestedOrientation = previousOrientation
                                    super.dismiss()
                                }
                            }
                            source?.removeOnLayoutChangeListener(sourceLayoutChangeListener)
                        }
                    }
            } else {
                recyclerContainer.scaleX = SCALE_FACTOR
                recyclerContainer.scaleY = SCALE_FACTOR
                recyclerContainer.alpha = 0f

                setDimmingFactor(0f)

                if (!activity.isDestroyed) {
                    activity.requestedOrientation = previousOrientation
                    super.dismiss()
                }

                source?.removeOnLayoutChangeListener(sourceLayoutChangeListener)
            }
        }
    }

    override fun setDimmingFactor(factor: Float) {
        super.setDimmingFactor(factor)
        transitionListener?.onProgressFraction(factor)
    }

    fun interface TransitionListener {
        fun onProgressFraction(factor: Float)
    }

    companion object {
        private const val CENTER_PIVOT_WEIGHT = 0.2f
        private const val SCALE_FACTOR = 0.75f
        private const val ITEM_SIZE_DP = 90f
    }
}
