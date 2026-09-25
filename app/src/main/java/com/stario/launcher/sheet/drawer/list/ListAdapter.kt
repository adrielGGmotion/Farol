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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.stario.launcher.apps.LauncherApplication
import com.stario.launcher.apps.ProfileApplicationManager
import com.stario.launcher.apps.interfaces.LauncherApplicationListener
import com.stario.launcher.preferences.Vibrations
import com.stario.launcher.sheet.drawer.RecyclerApplicationAdapter
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.icons.AdaptiveIconView
import com.stario.launcher.ui.recyclers.FastScroller
import com.stario.launcher.ui.utils.animation.Animation
import androidx.core.view.isNotEmpty

open class ListAdapter(activity: ThemedActivity, private val applicationManager: ProfileApplicationManager?) :
    RecyclerApplicationAdapter(activity), FastScroller.OnPopupViewUpdate, FastScroller.OnPopupViewReset {
    private var recyclerView: RecyclerView? = null
    private var oldScrollerPosition = -1
    private val listener = object : LauncherApplicationListener {
        override fun onHidden(application: LauncherApplication) {
            recyclerView?.post { notifyItemRemovedInternal(); approximateRecyclerHeight() }
        }
        override fun onInserted(application: LauncherApplication) {
            recyclerView?.post {
                notifyItemInsertedInternal(requireNotNull(applicationManager).indexOf(application))
                approximateRecyclerHeight()
            }
        }
        override fun onRemoved(application: LauncherApplication) {
            recyclerView?.post { notifyItemRemovedInternal(); approximateRecyclerHeight() }
        }
        override fun onShowed(application: LauncherApplication) {
            recyclerView?.post {
                notifyItemInsertedInternal(requireNotNull(applicationManager).indexOf(application))
                approximateRecyclerHeight()
            }
        }
        override fun onUpdated(application: LauncherApplication) {
            recyclerView?.post { notifyItemChanged(requireNotNull(applicationManager).indexOf(application)) }
        }
    }

    private fun notifyItemRemovedInternal() {
        val manager = recyclerView?.layoutManager ?: return
        val state = manager.onSaveInstanceState()
        notifyItemRangeRemoved(0, itemCount)
        manager.onRestoreInstanceState(state)
    }

    private fun notifyItemInsertedInternal(position: Int) {
        val manager = recyclerView?.layoutManager ?: return
        val state = manager.onSaveInstanceState()
        notifyItemInserted(position)
        manager.onRestoreInstanceState(state)
    }

    override fun onUpdate(index: Int, textView: TextView) {
        removeLimit()
        val size = requireNotNull(applicationManager).getSize() - 1
        val position = minOf(index, size)
        if (oldScrollerPosition != position) {
            Vibrations.getInstance().vibrate()
            val layoutManager = recyclerView?.layoutManager
            if (layoutManager != null) {
                val lastView = layoutManager.findViewByPosition(oldScrollerPosition)
                val currentView = layoutManager.findViewByPosition(position)
                currentView?.animate()?.scaleX(AdaptiveIconView.MAX_SCALE)
                    ?.scaleY(AdaptiveIconView.MAX_SCALE)?.setDuration(Animation.MEDIUM.getDuration().toLong())
                lastView?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(Animation.MEDIUM.getDuration().toLong())
            }
        }
        oldScrollerPosition = position
        val application = applicationManager.get(position)
        if (application !== LauncherApplication.FALLBACK_APP && application != null) {
            val label = application.getLabel()
            if (label.isNotEmpty()) textView.text = label[0].toString().uppercase()
        }
    }

    override fun onReset(position: Int) {
        val layoutManager = recyclerView?.layoutManager
        if (layoutManager != null) {
            val currentView = layoutManager.findViewByPosition(oldScrollerPosition)
            oldScrollerPosition = -1
            if (currentView != null) {
                currentView.animate().scaleX(1f).scaleY(1f)
                    .setDuration(Animation.MEDIUM.getDuration().toLong())
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationCancel(animation: Animator) {
                            currentView.scaleX = 1f
                            currentView.scaleY = 1f
                        }
                    })
            }
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
        applicationManager?.addApplicationListener(listener)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        applicationManager?.removeApplicationListener(listener)
        this.recyclerView = null
    }

    override fun getApplication(index: Int): LauncherApplication? = applicationManager?.get(index) ?: LauncherApplication.FALLBACK_APP
    override fun allowApplicationStateEditing(): Boolean = true
    override fun getItemId(position: Int): Long = applicationManager?.get(position)?.getInfo()?.packageName?.hashCode()?.toLong() ?: -1L
    override fun getTotalItemCount(): Int = requireNotNull(applicationManager).getSize()
}
