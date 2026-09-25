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

package com.stario.launcher.activities.settings.dialogs.hide.pager

import android.annotation.SuppressLint
import android.os.Parcelable
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.stario.launcher.R
import com.stario.launcher.apps.LauncherApplication
import com.stario.launcher.apps.ProfileApplicationManager
import com.stario.launcher.apps.interfaces.LauncherApplicationListener
import com.stario.launcher.sheet.drawer.RecyclerApplicationAdapter
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.icons.AdaptiveIconView
import com.stario.launcher.ui.recyclers.async.InflationType
import java.util.function.Supplier
import androidx.core.view.isVisible

open class HiddenRecyclerAdapter(
    activity: ThemedActivity,
    private val applicationManager: ProfileApplicationManager?
) : RecyclerApplicationAdapter(activity, InflationType.ASYNC) {
    private val listener: LauncherApplicationListener = object : LauncherApplicationListener {
        override fun onInserted(application: LauncherApplication) {
            recyclerView!!.post {
                notifyItemInsertedInternal(applicationManager!!.indexOf(application))
                approximateRecyclerHeight()
            }
        }

        override fun onRemoved(application: LauncherApplication) {
            recyclerView!!.post {
                notifyItemRemovedInternal()
                approximateRecyclerHeight()
            }
        }

        override fun onUpdated(application: LauncherApplication) {
            recyclerView!!.post {
                notifyItemChanged(applicationManager!!.indexOf(application))
            }
        }
    }

    private var recyclerView: RecyclerView? = null

    init {
        setHasStableIds(true)
    }

    open inner class HiddenViewHolder : ApplicationViewHolder() {
        internal lateinit var icon: AdaptiveIconView

        @SuppressLint("ClickableViewAccessibility")
        protected override fun onInflated() {
            super.onInflated()
            icon = itemView.findViewById(R.id.icon)
        }

        override fun getOnClickListener(): View.OnClickListener {
            return View.OnClickListener {
                val index = getBindingAdapterPosition()
                if (index == RecyclerView.NO_POSITION) {
                    return@OnClickListener
                }

                val application = getApplication(index)
                val manager = applicationManager!!
                if (manager.isVisibleToUser(application)) {
                    manager.hideApplication(application)
                } else {
                    manager.showApplication(application)
                }

                notifyItemChanged(index)
            }
        }

        override fun getOnLongClickListener(): View.OnLongClickListener? = null
    }

    override fun onBind(holder: ApplicationViewHolder, position: Int) {
        super.onBind(holder, position)

        val hidden = !applicationManager!!.isVisibleToUser(getApplication(position))
        val hiddenViewHolder = holder as HiddenViewHolder

        hiddenViewHolder.icon.setAlpha(if (hidden) HIDDEN_ALPHA else 1f)
        hiddenViewHolder.icon.setScaleX(if (hidden) HIDDEN_SCALE else 1f)
        hiddenViewHolder.icon.setScaleY(if (hidden) HIDDEN_SCALE else 1f)
        hiddenViewHolder.icon.setGrayscale(hidden)
    }

    protected override fun getHolderSupplier(viewType: Int): Supplier<ApplicationViewHolder> {
        return Supplier { HiddenViewHolder() }
    }

    private fun notifyItemRemovedInternal() {
        val recyclerView = recyclerView
        if (recyclerView != null) {
            val manager = recyclerView.getLayoutManager()
            if (manager != null) {
                val state: Parcelable? = manager.onSaveInstanceState()
                notifyItemRangeRemoved(0, getItemCount())
                manager.onRestoreInstanceState(state)
            }
        }
    }

    private fun notifyItemInsertedInternal(position: Int) {
        val recyclerView = recyclerView
        if (recyclerView != null) {
            val manager = recyclerView.getLayoutManager()
            if (manager != null) {
                val state: Parcelable? = manager.onSaveInstanceState()
                notifyItemInserted(position)
                manager.onRestoreInstanceState(state)
            }
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)

        this.recyclerView = recyclerView
        applicationManager!!.addApplicationListener(listener)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)

        applicationManager!!.removeApplicationListener(listener)
        this.recyclerView = null
    }

    protected override fun getApplication(index: Int): LauncherApplication {
        val manager = applicationManager
        return (if (manager != null) manager.get(index, true) else LauncherApplication.FALLBACK_APP)!!
    }

    override fun getTotalItemCount(): Int = applicationManager!!.getActualSize()

    protected override fun allowApplicationStateEditing(): Boolean = false

    private companion object {
        const val TAG = "HiddenRecyclerAdapter"
        const val HIDDEN_ALPHA = 0.3f
        const val HIDDEN_SCALE = 0.9f
    }
}
