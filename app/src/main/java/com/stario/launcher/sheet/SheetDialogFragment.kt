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

package com.stario.launcher.sheet

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import com.stario.launcher.sheet.behavior.SheetBehavior
import com.stario.launcher.sheet.briefing.dialog.BriefingDialog
import com.stario.launcher.sheet.drawer.dialog.ApplicationsDialog
import com.stario.launcher.sheet.widgets.dialog.WidgetsDialog
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.dialogs.PersistentFullscreenDialog
import com.stario.launcher.ui.utils.HomeWatcher
import com.stario.launcher.ui.utils.UiUtils
import java.util.concurrent.atomic.AtomicInteger

abstract class SheetDialogFragment() : DialogFragment() {
    private val destroyListeners = ArrayList<OnDestroyListener>()
    private val onShowListeners = ArrayList<OnShowListener>()

    private var slideListener: SheetDialog.OnSlideListener? = null
    private lateinit var activity: ThemedActivity
    private lateinit var homeWatcher: HomeWatcher
    private var receivedDragEvents = 0
    private var dialog: SheetDialog? = null
    private var sheetType: SheetType? = null

    protected constructor(type: SheetType) : this() {
        this.sheetType = type
    }

    override fun onAttach(context: Context) {
        if (context !is ThemedActivity) {
            throw RuntimeException("Parent activity is not of type ThemedActivity.")
        }

        activity = context

        homeWatcher = HomeWatcher(context)
        homeWatcher.setOnHomePressedListener { hide(true) }
        homeWatcher.startWatch()

        super.onAttach(context as Context)
    }

    override fun onDetach() {
        homeWatcher.stopWatch()

        super.onDetach()
    }

    override fun onDestroy() {
        for (listener in destroyListeners) {
            listener.onDestroy(sheetType)
        }

        super.onDestroy()
    }

    fun getSheetDialog(): SheetDialog? = dialog

    override fun onStop() {
        hide(false)

        super.onStop()
    }

    fun hide(animate: Boolean) {
        val behavior = getBehavior()

        if (behavior != null) {
            behavior.setState(SheetBehavior.STATE_COLLAPSED, animate)
        }

        if (!animate) {
            dialog?.hide()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            sheetType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getSerializable(TYPE_KEY, SheetType::class.java)
            } else {
                @Suppress("DEPRECATION")
                savedInstanceState.getSerializable(TYPE_KEY) as SheetType?
            }
        }

        if (sheetType == null) {
            throw RuntimeException("SheetDialogFragment type cannot be null.")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(TYPE_KEY, sheetType)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = SheetDialogFactory.forType(sheetType!!, activity, theme)!!
        this.dialog = dialog

        if (slideListener != null) {
            dialog.setOnSlideListener(slideListener)

            slideListener = null
        }

        dialog.setOnShowListener {
            val state = AtomicInteger(SheetBehavior.STATE_COLLAPSED)

            dialog.getBehavior().addSheetCallback(object : SheetBehavior.SheetCallback {
                override fun onStateChanged(sheet: View, newState: Int) {
                    state.set(newState)

                    if (newState == SheetBehavior.STATE_DRAGGING && receivedDragEvents < Int.MAX_VALUE) {
                        receivedDragEvents++
                    }
                }
            })

            if (receivedDragEvents == 0 && state.get() == SheetBehavior.STATE_COLLAPSED) {
                dialog.getBehavior().setState(SheetBehavior.STATE_EXPANDED)
            }

            for (listener in onShowListeners) {
                listener.onShow()
            }

            onShowListeners.clear()
        }

        UiUtils.Notch.applyNotchMargin(dialog.getSheetContainer(), UiUtils.Notch.Treatment.CENTER)

        return dialog
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val container: ViewGroup = dialog!!.getSheetContainer()

        container.requestLayout()
        snapScrollingViews(container)
    }

    private fun snapScrollingViews(view: View) {
        if (view is RecyclerView) {
            if (view.computeVerticalScrollOffset() == 0) {
                view.post { view.scrollBy(0, -view.computeVerticalScrollOffset()) }
            }

            if (view.computeHorizontalScrollOffset() == 0) {
                view.post { view.scrollBy(-view.computeHorizontalScrollOffset(), 0) }
            }
        } else if (view is ScrollView) {
            if (view.scrollY == 0) {
                view.post { view.scrollBy(0, -view.scrollY) }
            }

            if (view.scrollX == 0) {
                view.post { view.scrollBy(-view.scrollX, 0) }
            }
        } else if (view is NestedScrollView) {
            if (view.scrollY == 0) {
                view.post { view.scrollBy(0, -view.scrollY) }
            }

            if (view.scrollX == 0) {
                view.post { view.scrollBy(-view.scrollX, 0) }
            }
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                snapScrollingViews(view.getChildAt(index))
            }
        }
    }

    fun getType(): SheetType? = sheetType

    fun getBehavior(): SheetBehavior<*>? = dialog?.getBehavior()

    fun onMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && receivedDragEvents < Int.MAX_VALUE) {
            receivedDragEvents++
        }

        return dialog!!.onMotionEvent(event)
    }

    fun show() {
        dialog?.showDialog()
    }

    fun expand() {
        val dialog = dialog
        if (dialog != null) {
            dialog.showDialog()

            val container = dialog.getSheetContainer()
            container.post {
                val behavior = getBehavior()

                if (behavior != null) {
                    behavior.setState(SheetBehavior.STATE_EXPANDED)
                }
            }
        }
    }

    /**
     * Add a one time show listener
     */
    protected fun addOnShowListener(listener: OnShowListener?) {
        if (listener != null) {
            onShowListeners.add(listener)
        }
    }

    fun addOnDestroyListener(listener: OnDestroyListener?) {
        if (listener != null) {
            destroyListeners.add(listener)
        }
    }

    protected fun setOnBackPressed(listener: PersistentFullscreenDialog.OnBackPressed?) {
        dialog?.setOnBackPressed(listener)
    }

    fun setOnSlideListener(listener: SheetDialog.OnSlideListener?) {
        if (dialog != null) {
            dialog?.setOnSlideListener(listener)
        } else {
            slideListener = listener
        }
    }

    fun updateSheetSystemUI(lightMode: Boolean) {
        val dialog = dialog ?: return
        val window: Window? = dialog.window

        if (window != null) {
            UiUtils.setLightSystemBars(window, !lightMode)
        }
    }

    abstract fun requiresEagerInitialization(): Boolean

    fun interface OnDestroyListener {
        fun onDestroy(type: SheetType?)
    }

    fun interface OnShowListener {
        fun onShow()
    }

    companion object {
        private const val TYPE_KEY = "DialogSheetType"

        @JvmField
        val IMPLEMENTATIONS: List<Class<out SheetDialogFragment>> = listOf(
            ApplicationsDialog::class.java,
            WidgetsDialog::class.java,
            BriefingDialog::class.java
        )
    }
}
