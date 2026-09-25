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

package com.stario.launcher.sheet.widgets.configurator

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.stario.launcher.R
import com.stario.launcher.apps.LauncherApplication
import com.stario.launcher.apps.ProfileApplicationManager
import com.stario.launcher.apps.ProfileManager
import com.stario.launcher.preferences.Vibrations
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.Measurements
import com.stario.launcher.ui.icons.AdaptiveIconView
import com.stario.launcher.ui.utils.animation.Animation
import com.stario.launcher.utils.Utils
import java.util.ArrayList
import androidx.core.view.size

open class WidgetListAdapter(
    private val activity: ThemedActivity,
    private val recycler: RecyclerView,
    private val requestListener: WidgetConfigurator.Request
) : RecyclerView.Adapter<WidgetListAdapter.ViewHolder>() {
    private val entries = WidgetEntries()
    private var targetHolder: ViewHolder? = null

    init {
        setHasStableIds(true)
    }

    fun update() {
        reset()

        val packageManager = activity.packageManager
        val widgets = AppWidgetManager.getInstance(activity).getInstalledProviders()

        val oldSize = entries.size
        entries.clear()
        if (oldSize > 0) notifyItemRangeRemoved(0, oldSize)

        val mainProfile: ProfileApplicationManager? = ProfileManager.getInstance().getProfile(null)
        if (mainProfile != null) {
            for (info in widgets) {
                val packageName = info.provider.packageName
                var entry = entries.contains(packageName)

                if (entry == null) {
                    val application: LauncherApplication? = mainProfile.get(info.provider.packageName)

                    if (application != null) {
                        entry = WidgetGroupEntry(packageName, application.getLabel(), application.getIcon())
                    } else {
                        var label: String
                        var icon: Drawable

                        if (Utils.isMinimumSDK(Build.VERSION_CODES.S)) {
                            val activityInfo: ActivityInfo = info.getActivityInfo()
                            label = activityInfo.loadLabel(packageManager).toString()
                            icon = activityInfo.loadIcon(packageManager)
                        } else {
                            try {
                                val applicationInfo: ApplicationInfo =
                                    packageManager.getApplicationInfo(packageName, 0)
                                label = applicationInfo.loadLabel(packageManager).toString()
                                icon = applicationInfo.loadIcon(packageManager)
                            } catch (exception: PackageManager.NameNotFoundException) {
                                label = info.loadLabel(packageManager)
                                icon = info.loadIcon(activity, Measurements.getDotsPerInch())
                            }
                        }

                        entry = WidgetGroupEntry(packageName, label, icon)
                    }

                    var index = 0
                    while (index < entries.size && entries[index].compareTo(entry) < 0) {
                        index++
                    }

                    entries.add(index, entry)
                }

                entry.addWidget(info)
            }
        }

        if (entries.isNotEmpty()) notifyItemRangeInserted(0, entries.size)
    }

    private fun reset() {
        val holder = targetHolder ?: return

        holder.adapter?.reset()
        holder.widgets.setVisibility(View.GONE)
        targetHolder = null
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        internal val icon: AdaptiveIconView = itemView.findViewById(R.id.preview)
        internal val label: TextView = itemView.findViewById(R.id.label)
        internal val count: TextView = itemView.findViewById(R.id.count)
        internal val widgets: RecyclerView = itemView.findViewById(R.id.prebuilt)
        internal var adapter: WidgetItemAdapter? = null

        init {
            itemView.setHapticFeedbackEnabled(false)

            widgets.setLayoutManager(object : LinearLayoutManager(activity) {
                override fun canScrollVertically(): Boolean = false
            })
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, index: Int) {
        val data = entries[index]

        holder.icon.setIcon(data.icon)
        holder.label.setText(data.label)

        val size = data.widgets.size
        holder.count.setText(
            " - $size " + if (size == 1) {
                activity.resources.getString(R.string.widget_one)
            } else {
                activity.resources.getString(R.string.widget_many)
            }
        )

        holder.itemView.setOnClickListener {
            Vibrations.getInstance().vibrate()

            holder.adapter = WidgetItemAdapter(activity, data, requestListener)
            holder.widgets.setAdapter(holder.adapter)

            if (holder.widgets.visibility != View.VISIBLE) {
                holder.widgets.setVisibility(View.VISIBLE)

                reset()

                targetHolder = holder
            } else {
                reset()
            }

            TransitionManager.beginDelayedTransition(
                recycler,
                ChangeBounds()
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .setDuration(Animation.MEDIUM.getDuration().toLong())
            )
        }
    }

    override fun getItemCount(): Int = entries.size

    override fun getItemId(position: Int): Long = entries[position].packageName.hashCode().toLong()

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(container.context)
        return ViewHolder(inflater.inflate(R.layout.widget_picker_group, container, false))
    }

    private class WidgetEntries : ArrayList<WidgetGroupEntry>() {
        fun contains(packageName: String): WidgetGroupEntry? {
            for (entry in this) {
                if (entry.packageName == packageName) {
                    return entry
                }
            }

            return null
        }
    }

    open class WidgetGroupEntry internal constructor(
        @JvmField val packageName: String,
        @JvmField val label: String?,
        @JvmField val icon: Drawable?
    ) : Comparable<WidgetGroupEntry> {
        @JvmField
        val widgets: MutableList<AppWidgetProviderInfo?> = ArrayList()

        internal fun addWidget(info: AppWidgetProviderInfo?) {
            widgets.add(info)
        }

        override fun compareTo(other: WidgetGroupEntry): Int {
            return if (label == null) 0 else label!!.compareTo(other.label!!)
        }
    }
}
