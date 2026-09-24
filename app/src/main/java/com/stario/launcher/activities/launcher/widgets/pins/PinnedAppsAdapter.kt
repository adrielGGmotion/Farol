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

package com.stario.launcher.activities.launcher.widgets.pins

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.stario.launcher.R
import com.stario.launcher.activities.launcher.widgets.pins.dialog.PinnedAppsGroupDialog
import com.stario.launcher.apps.Category
import com.stario.launcher.apps.CategoryManager
import com.stario.launcher.apps.LauncherApplication
import com.stario.launcher.preferences.Vibrations
import com.stario.launcher.sheet.drawer.RecyclerApplicationAdapter
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.recyclers.async.InflationType
import com.stario.launcher.ui.utils.UiUtils
import java.util.UUID
import java.util.function.Supplier

open class PinnedAppsAdapter(
    private val activity: ThemedActivity,
    private val settings: SharedPreferences,
    private val popUpShowListener: OnPopUpShowListener?,
    private val transitionListener: PinnedAppsGroupDialog.TransitionListener?
) : RecyclerApplicationAdapter(activity, false, InflationType.SYNCED) {
    private val sharedPreferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener
    private val categoryManagerChangeListener: CategoryManager.CategoryListener
    private val categoryChangeListener: Category.CategoryItemListener
    private var category: Category? = null
    private var itemCount = 0

    init {
        sharedPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (PinnedCategory.PINNED_CATEGORY != key) {
                return@OnSharedPreferenceChangeListener
            }
            load()
        }

        categoryManagerChangeListener = object : CategoryManager.CategoryListener {
            override fun onRemoved(category: Category) {
                if (category == this@PinnedAppsAdapter.category) {
                    load()
                }
            }
        }

        categoryChangeListener = object : Category.CategoryItemListener {
            @SuppressLint("NotifyDataSetChanged")
            override fun onInserted(application: LauncherApplication) {
                notifyDataSetChanged()
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun onRemoved(application: LauncherApplication) {
                notifyDataSetChanged()
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun onUpdated(application: LauncherApplication) {
                notifyDataSetChanged()
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun onSwapped(index1: Int, index2: Int) {
                notifyDataSetChanged()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun load() {
        category?.removeCategoryItemListener(categoryChangeListener)

        try {
            category = CategoryManager.getInstance().get(
                UUID.fromString(settings.getString(PinnedCategory.PINNED_CATEGORY, ""))
            )

            if (category != null) {
                category?.addCategoryItemListener(categoryChangeListener)
            } else {
                resetSharedPreferences()
            }
        } catch (exception: IllegalArgumentException) {
            category = null
            resetSharedPreferences()
        }

        UiUtils.post { notifyDataSetChanged() }
    }

    private fun resetSharedPreferences() {
        settings.edit()
            .remove(PinnedCategory.PINNED_CATEGORY)
            .remove(PinnedCategory.PINNED_CATEGORY_VISIBLE)
            .apply()
    }

    inner open class PinnedGroupViewHolder(viewType: Int) : ApplicationViewHolder(viewType) {
        var group: RecyclerView? = null
            private set

        override fun onInflated() {
            super.onInflated()
            group = itemView.findViewById(R.id.group)
        }

        override fun getOnClickListener(): View.OnClickListener = View.OnClickListener {
            Vibrations.getInstance().vibrate()

            val index = bindingAdapterPosition
            if (index == RecyclerView.NO_POSITION) {
                return@OnClickListener
            }

            val dialog = PinnedAppsGroupDialog(activity, transitionListener)
            dialog.setCategory(category)
            dialog.show(index, group)
        }

        override fun getOnLongClickListener(): View.OnLongClickListener? = null
    }

    inner open class PinnedApplicationViewHolder(viewType: Int) : ApplicationViewHolder(viewType) {
        override fun getOnLongClickListener(): View.OnLongClickListener {
            val listener = super.getOnLongClickListener()
            return View.OnLongClickListener { view ->
                popUpShowListener?.onShow()
                listener?.onLongClick(view) ?: true
            }
        }
    }

    override fun onBind(viewHolder: ApplicationViewHolder, index: Int) {
        super.onBind(viewHolder, index)

        if (viewHolder is PinnedGroupViewHolder) {
            viewHolder.setIcon(null)
            viewHolder.group?.layoutManager = GridLayoutManager(activity, 2)
            viewHolder.group?.adapter = PinnedAppsGroupAdapter(activity, category, itemCount - 1)
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (position == itemCount - 1 && (category?.getSize() ?: 0) - position - 1 > 0) {
            return GROUP_VIEW_TYPE
        }
        return super.getItemViewType(position)
    }

    override fun getLayout(viewType: Int): Int {
        if (viewType == GROUP_VIEW_TYPE) {
            return R.layout.pinned_application_group
        }
        return super.getLayout(ONLY_ICON_LAYOUT)
    }

    override fun getHolderSupplier(viewType: Int): Supplier<ApplicationViewHolder> {
        return if (viewType == GROUP_VIEW_TYPE) {
            Supplier { PinnedGroupViewHolder(viewType) }
        } else {
            Supplier { PinnedApplicationViewHolder(viewType) }
        }
    }

    override fun getApplication(index: Int): LauncherApplication {
        return category?.get(index) ?: fallbackApplication()
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        settings.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
        CategoryManager.getInstance().addOnCategoryUpdateListener(categoryManagerChangeListener)
        load()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        settings.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
        CategoryManager.getInstance().removeOnCategoryUpdateListener(categoryManagerChangeListener)
        category?.removeCategoryItemListener(categoryChangeListener)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setMaxItemCount(count: Int) {
        itemCount = count
        notifyDataSetChanged()
    }

    override fun getTotalItemCount(): Int {
        return Math.min(category?.getSize() ?: 0, itemCount)
    }

    override fun allowApplicationStateEditing(): Boolean = true

    fun interface OnPopUpShowListener {
        fun onShow()
    }

    companion object {
        private const val GROUP_VIEW_TYPE = 2

        @Suppress("UNCHECKED_CAST")
        private fun <T> unchecked(value: T?): T = value as T

        private fun fallbackApplication(): LauncherApplication = unchecked(LauncherApplication.FALLBACK_APP)
    }
}
