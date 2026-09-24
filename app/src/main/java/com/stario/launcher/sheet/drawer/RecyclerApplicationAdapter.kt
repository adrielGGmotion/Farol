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

package com.stario.launcher.sheet.drawer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.stario.launcher.R
import com.stario.launcher.apps.LauncherApplication
import com.stario.launcher.apps.ProfileManager
import com.stario.launcher.apps.popup.ApplicationCustomizationDialog
import com.stario.launcher.preferences.Vibrations
import com.stario.launcher.sheet.SheetsFocusController
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.Measurements
import com.stario.launcher.ui.icons.AdaptiveIconView
import com.stario.launcher.ui.popup.PopupMenu
import com.stario.launcher.ui.recyclers.async.AsyncRecyclerAdapter
import com.stario.launcher.ui.recyclers.async.InflationType
import com.stario.launcher.ui.utils.animation.Animation
import com.stario.launcher.utils.Utils
import java.util.function.Supplier

abstract class RecyclerApplicationAdapter : AsyncRecyclerAdapter<RecyclerApplicationAdapter.ApplicationViewHolder> {
    companion object {
        private const val TAG = "RecyclerApplicationAdapter"
        protected const val ONLY_ICON_LAYOUT = 1
    }

    private val showLabels: Boolean
    private val activity: ThemedActivity
    private val itemTouchHelper: ItemTouchHelper?

    constructor(activity: ThemedActivity) : this(activity, true, null, InflationType.ASYNC)
    constructor(activity: ThemedActivity, showLabel: Boolean) : this(activity, showLabel, null, InflationType.ASYNC)
    constructor(activity: ThemedActivity, itemTouchHelper: ItemTouchHelper?) : this(activity, true, itemTouchHelper, InflationType.ASYNC)
    constructor(activity: ThemedActivity, showLabel: Boolean, itemTouchHelper: ItemTouchHelper?) : this(activity, showLabel, itemTouchHelper, InflationType.ASYNC)
    constructor(activity: ThemedActivity, type: InflationType) : this(activity, true, null, type)
    constructor(activity: ThemedActivity, showLabel: Boolean, type: InflationType) : this(activity, showLabel, null, type)
    constructor(activity: ThemedActivity, itemTouchHelper: ItemTouchHelper?, type: InflationType) : this(activity, true, itemTouchHelper, type)
    constructor(activity: ThemedActivity, showLabel: Boolean, itemTouchHelper: ItemTouchHelper?, type: InflationType) : super(activity, type) {
        showLabels = showLabel
        this.activity = activity
        this.itemTouchHelper = itemTouchHelper
        setHasStableIds(true)
    }

    open inner class ApplicationViewHolder : AsyncViewHolder {
        internal var iconView: AdaptiveIconView? = null
        internal var dialog: PopupWindow? = null
        internal var notification: View? = null
        internal var label: TextView? = null

        constructor() : super()
        constructor(viewType: Int) : super(viewType)

        @SuppressLint("ClickableViewAccessibility")
        override fun onInflated() {
            itemView.isHapticFeedbackEnabled = false
            label = itemView.findViewById(R.id.label)
            iconView = itemView.findViewById(R.id.icon)
            notification = itemView.findViewById(R.id.notification_dot)
            label?.setLines(getLabelLineCount())
            itemView.setOnTouchListener(SheetsFocusController.createClickTouchListener(
                getOnClickListener(), getOnLongClickListener(),
                object : SheetsFocusController.OnLongClickEventListener {
                    private var iconAnimationCallback: Choreographer.FrameCallback? = null

                    override fun onDown(duration: Long) {
                        val icon = requireNotNull(iconView)
                        icon.animate().cancel()
                        val startScale = icon.scaleX
                        val endScale = AdaptiveIconView.MAX_SCALE
                        iconAnimationCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
                        iconAnimationCallback = null
                        if (duration <= 0) {
                            icon.scaleX = endScale
                            icon.scaleY = endScale
                            return
                        }
                        val startTime = SystemClock.uptimeMillis()
                        val callback = object : Choreographer.FrameCallback {
                            override fun doFrame(frameTimeNanos: Long) {
                                val elapsed = SystemClock.uptimeMillis() - startTime
                                val fraction = minOf(1f, elapsed.toFloat() / duration)
                                val scale = startScale + fraction * (endScale - startScale)
                                icon.scaleX = scale
                                icon.scaleY = scale
                                if (fraction < 1f) Choreographer.getInstance().postFrameCallback(this)
                                else iconAnimationCallback = null
                            }
                        }
                        iconAnimationCallback = callback
                        Choreographer.getInstance().postFrameCallback(callback)
                    }

                    override fun onFinished() {
                        iconAnimationCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
                        iconAnimationCallback = null
                        iconView?.animate()?.scaleY(1f)?.scaleX(1f)
                            ?.setInterpolator(DecelerateInterpolator())
                            ?.setDuration(Animation.SHORT.getDuration().toLong())
                    }
                }, this, itemTouchHelper, SheetsFocusController.OnDragStartListener { dialog?.dismiss() }))
        }

        private fun showPopup(application: LauncherApplication) {
            val launcherApps = activity.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val menu = PopupMenu(activity)
            if (Utils.isProfileAvailable(activity, application.getProfile())) {
                menu.addShortcuts(launcherApps, getShortcutForApplication(launcherApps, application) as java.util.List<ShortcutInfo?>)
            }
            val resources = activity.resources
            menu.add(PopupMenu.Item(resources.getString(R.string.app_info),
                ResourcesCompat.getDrawable(resources, R.drawable.ic_info, activity.theme)) {
                val activities = launcherApps.getActivityList(application.getInfo().packageName, application.getProfile())
                if (activities.isNotEmpty()) {
                    try {
                        activity.getSystemService(LauncherApps::class.java)
                            .startAppDetailsActivity(activities[0].componentName, application.getProfile(), null, null)
                    } catch (exception: Exception) {
                        Log.e(TAG, "Unable to launch settings", exception)
                    }
                }
            })
            if (allowApplicationStateEditing()) {
                menu.add(PopupMenu.Item(resources.getString(R.string.customize),
                    ResourcesCompat.getDrawable(resources, R.drawable.ic_edit, activity.theme)) {
                    ApplicationCustomizationDialog(activity, application).show()
                })
                menu.add(PopupMenu.Item(resources.getString(R.string.hide),
                    ResourcesCompat.getDrawable(resources, R.drawable.ic_hide, activity.theme)) {
                    ProfileManager.getInstance().getProfile(application.getProfile())?.hideApplication(application)
                })
                if (!application.systemPackage) {
                    menu.add(PopupMenu.Item(resources.getString(R.string.uninstall),
                        ResourcesCompat.getDrawable(resources, R.drawable.ic_delete, activity.theme)) {
                        val info = application.getInfo()
                        try {
                            val intent = Intent.parseUri("#Intent;action=android.intent.action.DELETE;launchFlags=0x10800000;end", 0)
                                .setData(Uri.fromParts("package", info.packageName, info.name))
                                .putExtra(Intent.EXTRA_USER, application.getProfile())
                            activity.startActivity(intent)
                        } catch (exception: Exception) {
                            Log.e(TAG, "Unable to uninstall application", exception)
                        }
                    })
                }
            }
            (itemView as ViewGroup).requestDisallowInterceptTouchEvent(true)
            val icon = requireNotNull(iconView)
            val label = label
            val inset = if (Measurements.isLandscape())
                ((label?.measuredWidth ?: 0) - icon.measuredWidth) / 2 else 0
            dialog = menu.show(activity, icon, Rect(inset,
                if (Measurements.isLandscape()) 0 else
                    (if (label == null) 0 else label.measuredHeight * label.lineCount / label.maxLines) + Measurements.dpToPx(10f),
                inset, 0),
                if (Measurements.isLandscape()) PopupMenu.PIVOT_CENTER_VERTICAL else PopupMenu.PIVOT_DEFAULT,
                itemTouchHelper == null)
        }

        open fun getOnLongClickListener(): View.OnLongClickListener? = View.OnLongClickListener {
            Vibrations.getInstance().vibrate()
            val index = bindingAdapterPosition
            if (index == RecyclerView.NO_POSITION) return@OnLongClickListener false
            val application = getApplication(index)
            if (application !== LauncherApplication.FALLBACK_APP && application != null) {
                showPopup(application)
                true
            } else false
        }

        open fun getOnClickListener(): View.OnClickListener = View.OnClickListener {
            Vibrations.getInstance().vibrate()
            val index = bindingAdapterPosition
            if (index != RecyclerView.NO_POSITION) {
                val application = getApplication(index)
                if (application !== LauncherApplication.FALLBACK_APP) application?.launch(activity)
            }
        }

        fun setIcon(drawable: Drawable?) { iconView?.setIcon(drawable) }
        fun setLabel(sequence: CharSequence?) { label?.text = sequence }
        fun hideLabel() { label?.animate()?.alpha(0f)?.setDuration(Animation.SHORT.getDuration().toLong()) }
        fun showLabel() { label?.animate()?.alpha(1f)?.setDuration(Animation.SHORT.getDuration().toLong()) }
    }

    private fun getShortcutForApplication(launcherApps: LauncherApps, application: LauncherApplication): kotlin.collections.List<ShortcutInfo?> {
        val shortcutQuery = LauncherApps.ShortcutQuery()
        shortcutQuery.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        shortcutQuery.setPackage(application.getInfo().packageName)
        return try {
            launcherApps.getShortcuts(shortcutQuery, application.getProfile()) ?: emptyList()
        } catch (exception: SecurityException) {
            emptyList()
        }
    }

    override fun onBind(holder: ApplicationViewHolder, position: Int) {
        val application = getApplication(position)
        if (application !== LauncherApplication.FALLBACK_APP && application != null) {
            holder.setLabel(application.getLabel())
            holder.notification?.visibility = View.GONE
            holder.iconView?.setApplication(application)
            holder.iconView?.transitionName = application.getInfo().packageName + application.getProfile()
        }
        holder.iconView?.setTag(R.id.stagger_order_tag, position)
    }

    override fun getItemViewType(position: Int): Int = if (!showLabels) ONLY_ICON_LAYOUT else super.getItemViewType(position)
    override fun getLayout(viewType: Int): Int = if (viewType == ONLY_ICON_LAYOUT)
        R.layout.recycler_application_item_only_icon else R.layout.recycler_application_item
    override fun getHolderSupplier(viewType: Int): Supplier<ApplicationViewHolder> = Supplier { ApplicationViewHolder(viewType) }
    override fun getItemId(position: Int): Long = getApplication(position)?.getInfo()?.packageName?.hashCode()?.toLong() ?: 0L
    protected open fun getLabelLineCount(): Int = 2
    protected abstract fun getApplication(index: Int): LauncherApplication?
    protected abstract fun allowApplicationStateEditing(): Boolean
}
