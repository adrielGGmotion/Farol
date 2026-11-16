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

package com.farol.launcher.activities.launcher.widgets.glance

import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.app.Dialog
import android.content.SharedPreferences
import android.view.View
import android.widget.LinearLayout
import androidx.fragment.app.FragmentActivity
import com.farol.launcher.R
import com.farol.launcher.sheet.SheetsFocusController
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.ui.Measurements
import com.farol.launcher.ui.common.glance.GlanceConstraintLayout
import com.farol.launcher.ui.common.grid.DraggableGridItem
import com.farol.launcher.ui.common.grid.DynamicGridLayout
import com.farol.launcher.ui.common.grid.GridItemVisibility
import com.farol.launcher.ui.utils.animation.Animation
import java.util.ArrayList

open class Glance(private val activity: ThemedActivity) {
    private val extensions = ArrayList<GlanceExtension>()
    private lateinit var extensionContainer: LinearLayout
    private var root: GlanceConstraintLayout? = null
    private val preferences: SharedPreferences = activity.applicationContext.getSettings()
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun attach(container: DynamicGridLayout) {
        val gridItem = DraggableGridItem(activity)
        gridItem.itemId = GLANCE_TAG

        root = activity.layoutInflater.inflate(R.layout.glance, gridItem, false) as GlanceConstraintLayout
        extensionContainer = root!!.findViewById(R.id.extensions)

        val transition = LayoutTransition()
        val changeIn = ObjectAnimator.ofFloat(null, "alpha", 0f, 1f)
        val changeOut = ObjectAnimator.ofFloat(null, "alpha", 1f, 0f)

        transition.setAnimator(LayoutTransition.APPEARING, changeIn)
        transition.setAnimator(LayoutTransition.DISAPPEARING, changeOut)
        transition.setAnimator(LayoutTransition.CHANGE_APPEARING, changeIn)
        transition.setAnimator(LayoutTransition.CHANGE_DISAPPEARING, changeOut)
        transition.setAnimator(LayoutTransition.CHANGING, changeIn)

        gridItem.addView(root!!)

        val defaultLayoutData = DynamicGridLayout.ItemLayoutData(GLANCE_TAG, 0, 0, 3, 1)
        defaultLayoutData.minColSpan = 3
        defaultLayoutData.minWidth = Measurements.dpToPx(330f)
        defaultLayoutData.maxColSpan = 4
        defaultLayoutData.maxRowSpan = 1

        val visibility = GridItemVisibility(container, gridItem, defaultLayoutData)
        listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (GLANCE_VISIBLE_KEY == key) {
                visibility.update(sharedPreferences.getBoolean(GLANCE_VISIBLE_KEY, true))
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)

        visibility.update(preferences.getBoolean(GLANCE_VISIBLE_KEY, true))
    }

    fun detach() {
        listener?.let { preferences.unregisterOnSharedPreferenceChangeListener(it) }
    }

    @JvmOverloads
    fun attachViewExtension(extension: GlanceViewExtension, additionalClickListener: View.OnClickListener? = null) {
        val root = root ?: throw RuntimeException("Glance should attach itself first before attaching extensions.")
        val view = extension.inflate(activity, extensionContainer)

        extensionContainer.addView(view)
        view.isHapticFeedbackEnabled = false
        view.setOnTouchListener(SheetsFocusController.createClickTouchListener { view1 ->
            extension.getClickListener()?.onClick(view1)
            additionalClickListener?.onClick(view1)
        })

        extensions.add(extension)
    }

    fun attachDialogExtension(extension: GlanceDialogExtension, listener: GlanceDialogExtension.TransitionListener?) {
        val root = root ?: throw RuntimeException("Glance should attach itself first before attaching extensions.")

        extension.addTransitionListener { progress ->
            root.alpha = 1f - progress
            listener?.onProgressFraction(progress)

            if (progress == 0f) {
                extensionContainer.animate().alpha(1f).setDuration(Animation.SHORT.getDuration().toLong())
            } else {
                extensionContainer.animate().cancel()
                extensionContainer.alpha = 0f
            }
        }

        extension.attach(this)
        extensions.add(extension)
    }

    fun getRootView(): View? = root

    fun updateSheetSystemUI(value: Boolean) {
        for (extension in extensions) {
            if (extension is GlanceDialogExtension) {
                extension.updateSheetSystemUI(value)
            }
        }
    }

    fun getActivity(): FragmentActivity = activity

    fun post(runnable: Runnable) {
        root!!.post(runnable)
    }

    fun getHeight(): Float = root!!.height.toFloat()

    fun getWidth(): Float = root!!.width.toFloat()

    fun update() {
        for (extension in extensions) {
            extension.update()
        }
    }

    fun hasFocus(): Boolean {
        for (extension in extensions) {
            if (extension is GlanceDialogExtension) {
                val dialog: Dialog? = extension.dialog
                if (dialog != null && dialog.isShowing) {
                    return true
                }
            }
        }

        return false
    }

    companion object {
        const val GLANCE_VISIBLE_KEY = "com.stario.HOMESCREEN_GLANCE_VISIBLE"
        private const val GLANCE_TAG = "GridGlance"
    }
}
