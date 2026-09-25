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

package com.stario.launcher.sheet.widgets.dialog

import android.app.Activity
import android.app.ActivityOptions
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import com.stario.launcher.R
import com.stario.launcher.preferences.Entry
import com.stario.launcher.preferences.Vibrations
import com.stario.launcher.sheet.SheetDialogFragment
import com.stario.launcher.sheet.SheetType
import com.stario.launcher.sheet.widgets.Widget
import com.stario.launcher.sheet.widgets.WidgetSize
import com.stario.launcher.sheet.widgets.configurator.WidgetConfigurator
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.Measurements
import com.stario.launcher.ui.common.FadingEdgeLayout
import com.stario.launcher.ui.popup.PopupMenu
import com.stario.launcher.ui.utils.animation.Animation
import com.stario.launcher.ui.widgets.WidgetContainer
import com.stario.launcher.ui.widgets.WidgetGrid
import com.stario.launcher.ui.widgets.WidgetHost
import com.stario.launcher.ui.widgets.WidgetScroller
import java.util.ArrayList
import java.util.PriorityQueue
import androidx.core.view.isEmpty
import androidx.core.view.size
import androidx.core.content.edit

open class WidgetsDialog : SheetDialogFragment {
    private var bindWidgetRequest: ActivityResultLauncher<Intent>? = null
    private var configurator: WidgetConfigurator? = null
    private var isConfiguratorVisible = false
    private lateinit var widgetStore: SharedPreferences
    private var pendingWidgetSize: WidgetSize? = null
    private lateinit var manager: AppWidgetManager
    private lateinit var scroller: WidgetScroller
    private lateinit var addWidgetContainer: View
    private lateinit var activity: ThemedActivity
    private lateinit var placeholder: ViewGroup
    private lateinit var content: LinearLayout
    private var host: WidgetHost? = null
    private lateinit var grid: WidgetGrid

    constructor() : super()

    constructor(type: SheetType) : super(type)

    override fun onAttach(context: Context) {
        super.onAttach(context)

        activity = context as ThemedActivity
        manager = AppWidgetManager.getInstance(activity)
        widgetStore = activity.applicationContext
            .getSharedPreferences(Entry.WIDGETS)

        bindWidgetRequest = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                if (data != null) {
                    val identifier = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    val size = pendingWidgetSize
                    if (identifier != -1 && size != null) {
                        setupWidget(manager, identifier, size)
                    }
                }
            }

            pendingWidgetSize = null
        }
    }

    override fun requiresEagerInitialization(): Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.widget_grid, container, false)

        content = view.findViewById(R.id.content)
        placeholder = view.findViewById(R.id.placeholder)
        grid = view.findViewById(R.id.grid)
        scroller = view.findViewById(R.id.scroller)
        addWidgetContainer = view.findViewById(R.id.add_widget_container)
        val fader: FadingEdgeLayout = view.findViewById(R.id.fader)

        val showConfiguratorListener = View.OnClickListener { showWidgetPicker() }

        placeholder.setOnClickListener(showConfiguratorListener)
        placeholder.findViewById<View>(R.id.add_widget_placeholder)
            .setOnClickListener(showConfiguratorListener)
        addWidgetContainer.findViewById<View>(R.id.add_widget)
            .setOnClickListener(showConfiguratorListener)

        content.setOnLongClickListener {
            showWidgetPicker()
            true
        }

        Measurements.addNavListener { value ->
            val inset = value ?: return@addNavListener
            fader.setFadeSizes(
                Measurements.getSysUIHeight() +
                    (if (Measurements.isLandscape()) 0 else Measurements.getDefaultPadding()),
                0,
                inset + Measurements.getDefaultPadding(),
                0
            )

            content.setPadding(
                content.paddingLeft,
                content.paddingBottom,
                content.paddingRight,
                inset
            )
        }

        Measurements.addStatusBarListener { value ->
            val inset = value ?: return@addStatusBarListener
            fader.setFadeSizes(
                inset + (if (Measurements.isLandscape()) 0 else Measurements.getDefaultPadding()),
                0,
                Measurements.getNavHeight() + Measurements.getDefaultPadding(),
                0
            )

            content.setPadding(
                content.paddingLeft,
                inset,
                content.paddingRight,
                content.paddingBottom
            )
        }

        setOnBackPressed {
            hide(true)
            false
        }

        val widgets = PriorityQueue<Widget>()
        for (identifier in requireWidgetHost().getAppWidgetIds()) {
            val key = identifier.toString()
            if (widgetStore.contains(key)) {
                val widget = Widget.deserialize(widgetStore.getString(key, null))
                if (widget != null) {
                    if (widgets.size < MAX_COUNT) {
                        widgets.add(widget)
                    } else {
                        requireWidgetHost().deleteAppWidgetId(identifier)
                    }
                } else {
                    widgetStore.edit {remove(key)}
                }
            } else {
                requireWidgetHost().deleteAppWidgetId(identifier)
            }
        }

        while (!widgets.isEmpty()) {
            val widget = widgets.poll()
            if (widget != null) {
                val widgetHost = createWidgetView(AppWidgetManager.getInstance(activity), widget)
                grid.attach(widgetHost, widget)
                updatePlaceholderVisibility(View.GONE)
            }
        }

        grid.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            columnSize = grid.computeCellSize()
        }

        return view
    }

    private fun updatePlaceholderVisibility(visibility: Int) {
        placeholder.setVisibility(visibility)

        addWidgetContainer.setVisibility(
            if (visibility == View.VISIBLE || Measurements.isLandscape()) View.GONE else View.VISIBLE
        )
    }

    private fun showWidgetPicker() {
        if (configurator == null) {
            val picker = WidgetConfigurator(
                activity,
                WidgetConfigurator.Request { info, size -> addWidget(info, size) }
            )
            picker.setOnDismissListener { isConfiguratorVisible = false }
            configurator = picker
        }

        if (!isConfiguratorVisible) {
            configurator?.show()
            isConfiguratorVisible = true
        }
    }

    private fun addWidget(info: AppWidgetProviderInfo, size: WidgetSize) {
        if (grid.childCount <= MAX_COUNT) {
            val identifier = requireWidgetHost().allocateAppWidgetId()
            val allowed = manager.bindAppWidgetIdIfAllowed(
                identifier,
                info.profile,
                info.provider,
                null
            )

            if (!allowed) {
                bindWidgetRequest?.let { launcher ->
                    val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, identifier)
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    pendingWidgetSize = size
                    launcher.launch(intent)
                }
            } else {
                setupWidget(manager, identifier, size)
            }
        }

        configurator?.dismiss()
    }

    private fun setupWidget(manager: AppWidgetManager, identifier: Int, size: WidgetSize) {
        val widget = Widget(identifier, grid.allocatePosition(), size)
        val widgetHost = createWidgetView(manager, widget)

        if (widgetHost.appWidgetInfo.configure == null) {
            completeWidgetSetup(widget, widgetHost)
        } else {
            try {
                val result = activity.addOnActivityResultListener(CONFIGURATION_CODE) { resultCode, _ ->
                    if (resultCode == Activity.RESULT_OK) {
                        completeWidgetSetup(widget, widgetHost)
                        widgetHost.forceLayout()
                    } else {
                        requireWidgetHost().deleteAppWidgetId(widgetHost.appWidgetId)
                    }

                    activity.removeOnActivityResultListener(CONFIGURATION_CODE)
                }

                if (!result) {
                    requireWidgetHost().deleteAppWidgetId(widgetHost.appWidgetId)
                    activity.removeOnActivityResultListener(CONFIGURATION_CODE)
                } else {
                    requireWidgetHost().startAppWidgetConfigureActivityForResult(
                        activity,
                        identifier,
                        Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS or
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
                        CONFIGURATION_CODE,
                        getActivityOptionsBundle()
                    )
                }
            } catch (exception: ActivityNotFoundException) {
                completeWidgetSetup(widget, widgetHost)
                activity.removeOnActivityResultListener(CONFIGURATION_CODE)
                Log.w(TAG, "No configure activity found for identifier $identifier")
            }
        }
    }

    private fun completeWidgetSetup(widget: Widget, host: AppWidgetHostView) {
        widgetStore.edit {
                putString(widget.id.toString(), widget.serialize())
            }

        grid.attach(host, widget)
        updatePlaceholderVisibility(View.GONE)
    }

    private fun createWidgetView(manager: AppWidgetManager, widget: Widget): AppWidgetHostView {
        val info = manager.getAppWidgetInfo(widget.id)
        val widgetHost = requireWidgetHost()
            .createView(activity.applicationContext, widget.id, info)

        widgetHost.setOnLongClickListener {
            Vibrations.getInstance().vibrate()

            val menu = PopupMenu(activity)
            val resources: Resources = getResources()

            menu.add(
                PopupMenu.Item(
                    resources.getString(R.string.remove),
                    AppCompatResources.getDrawable(activity, R.drawable.ic_delete),
                    View.OnClickListener { deleteWidget(widgetHost) }
                )
            )

            menu.add(
                PopupMenu.Item(
                    resources.getString(R.string.create_a_widget),
                    AppCompatResources.getDrawable(activity, R.drawable.ic_add),
                    View.OnClickListener { showWidgetPicker() }
                )
            )

            if (info.configure != null) {
                menu.add(
                    PopupMenu.Item(
                        resources.getString(R.string.configure_widget),
                        AppCompatResources.getDrawable(activity, R.drawable.ic_edit),
                        View.OnClickListener {
                            if (activity.addOnActivityResultListener(CONFIGURATION_CODE) { _, _ ->
                                    widgetHost.forceLayout()
                                    activity.removeOnActivityResultListener(CONFIGURATION_CODE)
                                }
                            ) {
                                requireWidgetHost().startAppWidgetConfigureActivityForResult(
                                    activity,
                                    widget.id,
                                    Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS or
                                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
                                    CONFIGURATION_CODE,
                                    getActivityOptionsBundle()
                                )
                            }
                        }
                    )
                )
            }

            if (widget.size == WidgetSize.SMALL) {
                menu.add(
                    PopupMenu.Item(
                        resources.getString(R.string.move_left),
                        AppCompatResources.getDrawable(activity, R.drawable.ic_move_left),
                        View.OnClickListener { moveWidgetLeftRight(widget, +1) }
                    )
                )
                menu.add(
                    PopupMenu.Item(
                        resources.getString(R.string.move_right),
                        AppCompatResources.getDrawable(activity, R.drawable.ic_move_right),
                        View.OnClickListener { moveWidgetLeftRight(widget, -1) }
                    )
                )
            }

            menu.add(
                PopupMenu.Item(
                    resources.getString(R.string.move_up),
                    AppCompatResources.getDrawable(activity, R.drawable.ic_move_up),
                    View.OnClickListener { moveWidgetUpDown(widget, +1) }
                )
            )
            menu.add(
                PopupMenu.Item(
                    resources.getString(R.string.move_down),
                    AppCompatResources.getDrawable(activity, R.drawable.ic_move_down),
                    View.OnClickListener { moveWidgetUpDown(widget, -1) }
                )
            )

            menu.setOnDismissListener {
                widgetHost.animate().scaleY(1f).scaleX(1f).alpha(1f)
                    .setDuration(Animation.SHORT.getDuration().toLong())
            }

            menu.show(activity, widgetHost, PopupMenu.PIVOT_CENTER_HORIZONTAL, true)
            true
        }

        return widgetHost
    }

    private fun moveWidgetLeftRight(widget: Widget, direction: Int) {
        if (widget.size != WidgetSize.SMALL) return

        val list = ArrayList<WidgetContainer>()
        for (i in 0 until grid.childCount) {
            list.add(grid.getChildAt(i) as WidgetContainer)
        }
        list.sortBy { it.getPosition() }

        val targetIndex = list.indexOfFirst { it.getWidget().id == widget.id }
        if (targetIndex == -1) return

        val wc = list[targetIndex]
        val swapIndex = targetIndex + direction
        if (swapIndex >= 0 && swapIndex < list.size) {
            val swapWc = list[swapIndex]
            if (swapWc.getSize() == WidgetSize.SMALL && swapWc.getOriginRow() == wc.getOriginRow()) {
                val swapWidget = swapWc.getWidget()
                val tempPos = widget.position
                widget.position = swapWidget.position
                swapWidget.position = tempPos

                widgetStore.edit {
                        putString(widget.id.toString(), widget.serialize())
                        .putString(swapWidget.id.toString(), swapWidget.serialize())
                    }

                grid.reorder()
            }
        }
    }

    private fun moveWidgetUpDown(widget: Widget, direction: Int) {
        val list = ArrayList<WidgetContainer>()
        for (i in 0 until grid.childCount) {
            list.add(grid.getChildAt(i) as WidgetContainer)
        }
        list.sortBy { it.getPosition() }

        val targetIndex = list.indexOfFirst { it.getWidget().id == widget.id }
        if (targetIndex == -1) return

        var chunkStart = targetIndex
        var chunkEnd = targetIndex
        val wc = list[targetIndex]

        if (wc.getSize() == WidgetSize.SMALL) {
            val row = wc.getOriginRow()
            while (chunkStart > 0) {
                val previous = list[chunkStart - 1]
                if (previous.getSize() == WidgetSize.SMALL && previous.getOriginRow() == row) {
                    chunkStart--
                } else {
                    break
                }
            }
            while (chunkEnd < list.size - 1) {
                val next = list[chunkEnd + 1]
                if (next.getSize() == WidgetSize.SMALL && next.getOriginRow() == row) {
                    chunkEnd++
                } else {
                    break
                }
            }
        }

        var swapStart: Int
        var swapEnd: Int
        if (direction == -1) {
            if (chunkStart == 0) return
            swapEnd = chunkStart - 1
            swapStart = swapEnd
            val previous = list[swapEnd]
            if (previous.getSize() == WidgetSize.SMALL) {
                val row = previous.getOriginRow()
                while (swapStart > 0) {
                    val item = list[swapStart - 1]
                    if (item.getSize() == WidgetSize.SMALL && item.getOriginRow() == row) {
                        swapStart--
                    } else {
                        break
                    }
                }
            }
        } else {
            if (chunkEnd == list.size - 1) return
            swapStart = chunkEnd + 1
            swapEnd = swapStart
            val next = list[swapStart]
            if (next.getSize() == WidgetSize.SMALL) {
                val row = next.getOriginRow()
                while (swapEnd < list.size - 1) {
                    val item = list[swapEnd + 1]
                    if (item.getSize() == WidgetSize.SMALL && item.getOriginRow() == row) {
                        swapEnd++
                    } else {
                        break
                    }
                }
            }
        }

        val rangeStart = minOf(chunkStart, swapStart)
        val rangeEnd = maxOf(chunkEnd, swapEnd)
        val positions = ArrayList<Int>()
        for (i in rangeStart..rangeEnd) {
            positions.add(list[i].getPosition())
        }

        val newOrder = ArrayList<WidgetContainer>()
        if (direction == -1) {
            for (i in chunkStart..chunkEnd) newOrder.add(list[i])
            for (i in swapStart..swapEnd) newOrder.add(list[i])
        } else {
            for (i in swapStart..swapEnd) newOrder.add(list[i])
            for (i in chunkStart..chunkEnd) newOrder.add(list[i])
        }

        widgetStore.edit {
            for (i in newOrder.indices) {
                val movedWidget = newOrder[i].getWidget()
                movedWidget.position = positions[i]
                putString(movedWidget.id.toString(), movedWidget.serialize())
            }
        }

        grid.reorder()
    }

    private fun deleteWidget(host: AppWidgetHostView) {
        val identifier = host.appWidgetId.toString()
        if (widgetStore.contains(identifier) &&
            Widget.deserialize(widgetStore.getString(identifier, null)) != null
        ) {
            widgetStore.edit {remove(identifier)}
        }

        requireWidgetHost().deleteAppWidgetId(host.appWidgetId)
        grid.removeView(host.parent as View)
        updatePlaceholderVisibility(if (grid.isEmpty()) View.VISIBLE else View.GONE)
    }

    fun requireWidgetHost(): WidgetHost {
        val existing = host
        if (existing != null) {
            return existing
        }

        return WidgetHost(activity, HOST_ID).also {
            host = it
            it.startListening()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updatePlaceholderVisibility(placeholder.visibility)
    }

    override fun onStart() {
        super.onStart()
        host?.startListening()
    }

    override fun onStop() {
        scroller.scrollTo(0, 0)

        host?.let {
            try {
                it.stopListening()
            } catch (exception: Exception) {
                Log.e(TAG, "onStop: ${exception.message}")
            }
        }

        super.onStop()
    }

    companion object {
        private const val TAG = "WidgetsDialog"
        private const val HOST_ID = 219672
        private const val MAX_COUNT = 15
        private const val CONFIGURATION_CODE = 3264614
        private var columnSize = 0

        @JvmStatic
        fun getName(): String = "Widgets"

        @JvmStatic
        fun getWidgetCellSize(): Int = columnSize

        @Suppress("DEPRECATION")
        private fun getActivityOptionsBundle(): Bundle {
            val activityOptions = ActivityOptions.makeBasic()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                activityOptions.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                activityOptions.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
            }

            return activityOptions.toBundle()
        }
    }
}
