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

package com.farol.launcher.sheet.widgets.configurator

import android.annotation.SuppressLint
import android.appwidget.AppWidgetProviderInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.farol.launcher.R
import com.farol.launcher.sheet.widgets.WidgetSize
import com.farol.launcher.sheet.widgets.dialog.WidgetsDialog
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.ui.Measurements
import com.farol.launcher.ui.icons.AdaptiveIconView
import com.farol.launcher.ui.utils.animation.Animation
import com.farol.launcher.ui.widgets.RoundedWidgetHost
import com.farol.launcher.utils.Casing
import com.farol.launcher.utils.Utils
import androidx.core.view.size

open class WidgetItemAdapter(
    private val activity: ThemedActivity,
    private val entry: WidgetListAdapter.WidgetGroupEntry,
    private val requestListener: WidgetConfigurator.Request
) : RecyclerView.Adapter<WidgetItemAdapter.ViewHolder>() {
    private var targetHolder: ViewHolder? = null

    internal fun reset() {
        val holder = targetHolder ?: return

        holder.preview.animate().alpha(1f)
            .setDuration(Animation.SHORT.getDuration().toLong())
        holder.options.setVisibility(View.INVISIBLE)

        targetHolder = null
    }

    open class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        internal val preview: ConstraintLayout = itemView.findViewById(R.id.preview)
        internal val label: TextView = itemView.findViewById(R.id.label)
        internal val options: View = itemView.findViewById(R.id.options)
        internal val small: View = itemView.findViewById(R.id.small)
        internal val medium: View = itemView.findViewById(R.id.medium)
        internal val large: View = itemView.findViewById(R.id.large)
        internal val xlarge: View = itemView.findViewById(R.id.xlarge)
    }

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(container.context)
        return ViewHolder(inflater.inflate(R.layout.widget_picker_preview, container, false))
    }

    @SuppressLint("ResourceType")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val info = entry.widgets[position] ?: return

        holder.label.setText(Casing.toTitleCase(info.loadLabel(activity.packageManager)))

        holder.itemView.setOnClickListener {
            if (holder.options.visibility != View.VISIBLE) {
                holder.preview.animate().alpha(0.3f)
                    .setDuration(Animation.SHORT.getDuration().toLong())

                holder.options.setVisibility(View.VISIBLE)

                holder.small.setOnClickListener {
                    requestListener.requestAddition(info, WidgetSize.SMALL)
                }

                holder.medium.setOnClickListener {
                    requestListener.requestAddition(info, WidgetSize.MEDIUM)
                }

                holder.large.setOnClickListener {
                    requestListener.requestAddition(info, WidgetSize.LARGE)
                }

                holder.xlarge.setOnClickListener {
                    requestListener.requestAddition(info, WidgetSize.XLARGE)
                }

                if (holder != targetHolder) {
                    reset()
                }

                targetHolder = holder
            } else {
                holder.preview.animate().alpha(1f)
                    .setDuration(Animation.SHORT.getDuration().toLong())

                holder.options.setVisibility(View.INVISIBLE)
            }
        }

        holder.small.setVisibility(View.VISIBLE)
        holder.medium.setVisibility(View.VISIBLE)
        holder.large.setVisibility(View.VISIBLE)
        holder.xlarge.setVisibility(View.VISIBLE)

        if (Utils.isMinimumSDK(Build.VERSION_CODES.S) &&
            info.targetCellHeight > 0 && info.targetCellWidth > 0
        ) {
            if (info.targetCellHeight > 3) {
                holder.small.setVisibility(View.GONE)
                holder.medium.setVisibility(View.GONE)
            }

            if (info.targetCellWidth > 3) {
                holder.small.setVisibility(View.GONE)
            }
        }

        if (info.minHeight > WidgetsDialog.getWidgetCellSize()) {
            holder.small.setVisibility(View.GONE)
            holder.medium.setVisibility(View.GONE)
        }

        if (info.minWidth > WidgetsDialog.getWidgetCellSize()) {
            holder.small.setVisibility(View.GONE)
        }

        val params = ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        params.constrainedHeight = true
        params.matchConstraintMaxHeight = Measurements.dpToPx(200f)

        var previewImage: Drawable? = info.loadPreviewImage(activity, Measurements.getDotsPerInch())

        if (Utils.isMinimumSDK(Build.VERSION_CODES.S)) {
            val previewLayout = info.previewLayout

            if (previewLayout != 0) {
                val previewInfo = info.clone()
                previewInfo.initialLayout = info.previewLayout

                if (previewInfo.targetCellHeight > 0 && previewInfo.targetCellWidth > 0) {
                    params.height = 0
                    params.dimensionRatio = "W,${previewInfo.targetCellHeight * 2 + 1}:${previewInfo.targetCellWidth * 2}"
                } else if (previewImage != null) {
                    params.height = 0
                    params.dimensionRatio = "W,${previewImage.intrinsicHeight.toFloat() / previewImage.intrinsicWidth}f"
                } else {
                    params.height = params.matchConstraintMaxHeight
                }

                val host = RoundedWidgetHost(activity, params)
                host.setAppWidget(-1, previewInfo)
                host.updateAppWidget(null)

                host.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    val child = host.getChildAt(0)

                    if (child != null) {
                        val scale = minOf(
                            host.measuredWidth.toFloat() / child.measuredWidth,
                            host.measuredHeight.toFloat() / child.measuredHeight
                        )

                        if (!scale.isNaN()) {
                            child.setScaleY(scale)
                            child.setScaleX(scale)
                        }
                    }

                    forwardGroupClicks(host, holder.itemView)
                }

                holder.preview.addView(host)
                return
            }
        }

        if (previewImage == null) {
            previewImage = entry.icon
            params.height = AdaptiveIconView.getMaxIconSize()
        } else {
            params.height = 0
            params.dimensionRatio = "W,${previewImage.intrinsicHeight.toFloat() / previewImage.intrinsicWidth}f"
            params.matchConstraintMaxHeight = minOf(
                params.matchConstraintMaxHeight,
                maxOf(AdaptiveIconView.getMaxIconSize(), previewImage.intrinsicHeight)
            )
        }

        val imageView = ImageView(activity)
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER)
        imageView.setImageDrawable(previewImage)
        imageView.setLayoutParams(params)

        holder.preview.addView(imageView)
    }

    override fun getItemCount(): Int = entry.widgets.size

    companion object {
        @JvmStatic
        internal fun forwardGroupClicks(viewGroup: ViewGroup, forwardTarget: View) {
            for (index in 0 until viewGroup.childCount) {
                val view = viewGroup.getChildAt(index)

                view.setHapticFeedbackEnabled(false)
                view.setOnTouchListener(null)
                view.setOnClickListener { forwardTarget.performClick() }

                if (view is ViewGroup) {
                    forwardGroupClicks(view, forwardTarget)
                }
            }
        }
    }
}
