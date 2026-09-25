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

package com.farol.launcher.sheet.drawer.category.folder

import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Transition
import com.farol.launcher.R
import com.farol.launcher.apps.Category
import com.farol.launcher.apps.CategoryManager
import com.farol.launcher.apps.popup.RenameCategoryDialog
import com.farol.launcher.preferences.Vibrations
import com.farol.launcher.sheet.drawer.RecyclerApplicationAdapter
import com.farol.launcher.sheet.drawer.search.ListDrawerPage
import com.farol.launcher.ui.Measurements
import com.farol.launcher.ui.recyclers.autogrid.AutoGridLayoutManager
import com.farol.launcher.ui.utils.LayoutSizeObserver
import java.lang.reflect.Method
import java.util.UUID

open class Folder : ListDrawerPage() {
    private val categoryListener = object : CategoryManager.CategoryListener {
        override fun onChanged(category: Category?) {
            identifier?.let { title.text = CategoryManager.getInstance().getCategoryName(it) }
        }
    }
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var listener: OnCreateListener? = null
    private lateinit var adapter: FolderAdapter
    private var identifier: UUID? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val rootView = super.onCreateView(inflater, container, savedInstanceState)
        val manager = AutoGridLayoutManager(activity, 1)
        LayoutSizeObserver.attach(rootView, LayoutSizeObserver.WIDTH, object : LayoutSizeObserver.OnChange {
            override fun onChange(view: View, watchFlags: Int, rect: Rect) {
                manager.spanCount = minOf(6, rect.width() / Measurements.dpToPx(90f))
            }
        })
        drawer.layoutManager = manager
        drawer.itemAnimator = null
        drawer.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var method: Method? = null
            private var valid = true
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                val tester = enterTransition
                if (tester is Transition) {
                    try {
                        if (!valid) return
                        if (method == null) {
                            method = Transition::class.java.getDeclaredMethod("forceToEnd", ViewGroup::class.java)
                            method?.isAccessible = true
                        }
                        method?.invoke(tester, rootView)
                    } catch (exception: SecurityException) {
                        valid = false
                    } catch (exception: NoSuchMethodException) {
                        valid = false
                    } catch (exception: IllegalAccessError) {
                        valid = false
                    } catch (exception: Exception) {
                        Log.e("Folder", "onScrollStateChanged: ", exception)
                    }
                }
            }
        })

        val callback = object : ItemTouchHelper.Callback() {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder): Boolean {
                val state = manager.onSaveInstanceState()
                val result = adapter.move(viewHolder, target)
                manager.onRestoreInstanceState(state)
                if (result) Vibrations.getInstance().vibrate()
                return false
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    viewHolder.itemView.forceLayout()
                    (viewHolder as RecyclerApplicationAdapter.ApplicationViewHolder).hideLabel()
                    drawer.itemAnimator = DefaultItemAnimator()
                    Vibrations.getInstance().vibrate()
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                (viewHolder as RecyclerApplicationAdapter.ApplicationViewHolder).showLabel()
                drawer.itemAnimator?.isRunning { drawer.itemAnimator = null }
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }

            override fun interpolateOutOfBoundsScroll(recyclerView: RecyclerView, viewSize: Int,
                viewSizeOutOfBounds: Int, totalSize: Int, msSinceStartScroll: Long): Int =
                minOf(super.interpolateOutOfBoundsScroll(recyclerView, viewSize, viewSizeOutOfBounds, totalSize, msSinceStartScroll), 100)

            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
                makeFlag(ItemTouchHelper.ACTION_STATE_DRAG,
                    ItemTouchHelper.DOWN or ItemTouchHelper.UP or ItemTouchHelper.START or ItemTouchHelper.END)

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled(): Boolean = false
            override fun isItemViewSwipeEnabled(): Boolean = false
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(drawer)
        listener?.onCreate()
        return rootView
    }

    override fun hideInflatedLayout(): Boolean = false

    open fun updateCategory(identifier: UUID) {
        postponeEnterTransition()
        setOnCreateListener {
            this.identifier = identifier
            adapter = FolderAdapter(activity, identifier, itemTouchHelper)
            drawer.adapter = adapter
            drawer.post {
                drawer.scrollToPosition(0)
                updateTitleTransforms(drawer)
                title.text = CategoryManager.getInstance().getCategoryName(identifier)
                title.setOnClickListener { RenameCategoryDialog(activity, identifier).show() }
                startPostponedEnterTransition()
            }
        }
    }

    override fun onStart() {
        CategoryManager.getInstance().addOnCategoryUpdateListener(categoryListener)
        super.onStart()
    }

    override fun onStop() {
        CategoryManager.getInstance().removeOnCategoryUpdateListener(categoryListener)
        super.onStop()
    }

    private fun setOnCreateListener(listener: OnCreateListener) { this.listener = listener }
    private fun interface OnCreateListener { fun onCreate() }
    override fun getLayoutResID(): Int = R.layout.drawer_folder
}
