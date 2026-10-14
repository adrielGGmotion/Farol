package com.farol.launcher.activities.launcher.widgets.home

import android.app.Activity
import android.app.ActivityOptions
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import android.view.View
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.farol.launcher.Farol
import com.farol.launcher.R
import com.farol.launcher.activities.launcher.Launcher
import com.farol.launcher.preferences.Entry
import com.farol.launcher.preferences.Vibrations
import com.farol.launcher.sheet.widgets.WidgetSize
import com.farol.launcher.sheet.widgets.configurator.WidgetConfigurator
import com.farol.launcher.ui.Measurements
import com.farol.launcher.ui.common.grid.DraggableGridItem
import com.farol.launcher.ui.common.grid.DynamicGridLayout
import com.farol.launcher.ui.popup.PopupMenu
import com.farol.launcher.ui.utils.animation.Animation
import com.farol.launcher.ui.widgets.HomeWidgetHost
import com.farol.launcher.ui.widgets.HomeWidgetHostView
import com.farol.launcher.utils.Utils

open class HomeWidgets(
    private val activity: Launcher,
    private val container: DynamicGridLayout,
    private val editMode: (Boolean) -> Unit
) {
    private val manager: AppWidgetManager = AppWidgetManager.getInstance(activity)
    private val userManager: UserManager = activity.getSystemService(UserManager::class.java)
    private val settings: SharedPreferences = (activity.applicationContext as Farol).getSettings()
    private val store: SharedPreferences = activity.applicationContext.getSharedPreferences(Entry.WIDGETS, STORE_NAME)
    private val host = HomeWidgetHost(activity, HOST_ID, manager, ::removeWidgetAfterHostRemoval)
    private val items = HashMap<Int, DraggableGridItem>()
    private var configurator: WidgetConfigurator? = null
    private var openMenu: PopupMenu? = null
    private var isConfiguratorVisible = false
    private var profileReceiver: BroadcastReceiver? = null
    private var restoreQueued = false
    private var lastCellWidth = -1
    private var lastCellHeight = -1
    private var lastColumnCount = -1
    private var lastRowCount = -1
    private val gridLayoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> onGridGeometryChanged() }

    fun attach(savedInstanceState: Bundle?) {
        val restoredRequestId = savedInstanceState?.getInt(SAVED_REQUEST_KEY, AppWidgetManager.INVALID_APPWIDGET_ID)
        loadPending()?.let { pending ->
            if (!pending.canRestore(restoredRequestId)) cancelPending(pending)
        }
        activity.addOnActivityResultListener(REQUEST_CODE) { resultCode, data -> handleActivityResult(resultCode, data) }
        container.addOnLayoutChangeListener(gridLayoutListener)
        registerProfileReceiver()
        startListening()
        reconcileHostIds()
        onGridGeometryChanged()
    }

    fun saveInstanceState(outState: Bundle) {
        val pending = loadPending() ?: return
        if (pending.phase != HomeWidgetPending.PHASE_COMMIT) outState.putInt(SAVED_REQUEST_KEY, pending.id)
    }

    fun showPicker() {
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS)) {
            Toast.makeText(activity, R.string.unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        if (configurator == null) {
            configurator = WidgetConfigurator(
                activity,
                WidgetConfigurator.Request { info, size -> addWidget(info, size) },
                showSizes = false
            ).also { picker ->
                picker.setOnDismissListener { isConfiguratorVisible = false }
            }
        }
        if (!isConfiguratorVisible) {
            configurator?.show()
            isConfiguratorVisible = true
        }
    }

    fun reload() {
        if (!isGridReady()) {
            restoreQueued = true
            return
        }
        val visible = HashSet<Int>()
        for (record in loadRecords()) {
            val info = getAvailableInfo(record) ?: continue
            visible.add(record.id)
            val item = items[record.id]
            if (item == null) {
                attachRecord(record, info)
            } else {
                container.updateItemLayoutData(item, layoutDataFor(record, info))
            }
        }
        val iterator = items.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!visible.contains(entry.key)) {
                container.removeItem(entry.value)
                iterator.remove()
            }
        }
    }

    private fun onGridGeometryChanged() {
        if (!isGridReady()) {
            restoreQueued = true
            return
        }
        val changed = container.getCellWidth() != lastCellWidth ||
            container.getCellHeight() != lastCellHeight ||
            container.getColumnCount() != lastColumnCount ||
            container.getRowCount() != lastRowCount
        if (!changed && !restoreQueued) return
        lastCellWidth = container.getCellWidth()
        lastCellHeight = container.getCellHeight()
        lastColumnCount = container.getColumnCount()
        lastRowCount = container.getRowCount()
        restoreQueued = false
        resumePendingPlacement()
        reload()
    }

    private fun isGridReady(): Boolean = container.isGridMeasured()

    fun startListening() {
        try {
            host.startListening()
        } catch (exception: RuntimeException) {
            android.util.Log.w(TAG, "Could not start home widget host", exception)
        }
    }

    fun stopListening() {
        try {
            host.stopListening()
        } catch (exception: RuntimeException) {
            android.util.Log.w(TAG, "Could not stop home widget host", exception)
        }
    }

    fun detach() {
        activity.removeOnActivityResultListener(REQUEST_CODE)
        container.removeOnLayoutChangeListener(gridLayoutListener)
        stopListening()
        val receiver = profileReceiver
        if (receiver != null) {
            try {
                activity.unregisterReceiver(receiver)
            } catch (exception: RuntimeException) {
                android.util.Log.w(TAG, "Profile receiver was not registered", exception)
            }
        }
        profileReceiver = null
    }

    private fun addWidget(info: AppWidgetProviderInfo, size: WidgetSize) {
        configurator?.dismiss()
        if (!isGridReady()) {
            restoreQueued = true
            Toast.makeText(activity, R.string.something_went_wrong, Toast.LENGTH_SHORT).show()
            return
        }
        loadPending()?.let { cancelPending(it) }
        val identifier = try {
            host.allocateAppWidgetId()
        } catch (exception: RuntimeException) {
            Toast.makeText(activity, R.string.something_went_wrong, Toast.LENGTH_SHORT).show()
            return
        }
        val record = createPendingRecord(identifier, info, size)
        if (record == null || !savePending(record)) {
            deleteWidgetId(identifier)
            Toast.makeText(activity, R.string.something_went_wrong, Toast.LENGTH_SHORT).show()
            return
        }
        val options = optionsForSpan(HomeWidgetSpan(record.spanX, record.spanY))
        val bound = try {
            manager.bindAppWidgetIdIfAllowed(identifier, info.profile, info.provider, options)
        } catch (exception: RuntimeException) {
            false
        }
        if (bound) {
            configureOrCommit(record)
        } else {
            launchBind(record, info, options)
        }
    }

    private fun createPendingRecord(identifier: Int, info: AppWidgetProviderInfo, size: WidgetSize): HomeWidgetPending? {
        val provider = info.provider?.flattenToString() ?: return null
        val serial = userManager.getSerialNumberForUser(info.profile)
        if (serial < 0) return null
        val span = HomeWidgetSizing.initialSpan(
            HomeWidgetSpan(size.width, size.height),
            limitsFor(info),
            container.getCellWidth(),
            container.getCellHeight(),
            container.getColumnCount(),
            container.getRowCount()
        )
        return HomeWidgetPending(identifier, provider, serial, span.columns, span.rows, HomeWidgetPending.PHASE_BIND)
    }

    private fun launchBind(pending: HomeWidgetPending, info: AppWidgetProviderInfo, options: Bundle) {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending.id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
        try {
            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent, REQUEST_CODE, getActivityOptionsBundle())
        } catch (exception: ActivityNotFoundException) {
            cancelPending(pending)
        } catch (exception: RuntimeException) {
            cancelPending(pending)
        }
    }

    private fun handleActivityResult(resultCode: Int, data: Intent?) {
        val pending = loadPending() ?: return
        val resultId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending.id) ?: pending.id
        if (resultId != pending.id) return
        if (resultCode != Activity.RESULT_OK) {
            cancelPending(pending)
            return
        }
        when (pending.phase) {
            HomeWidgetPending.PHASE_BIND -> configureOrCommit(pending)
            HomeWidgetPending.PHASE_CONFIGURE,
            HomeWidgetPending.PHASE_COMMIT -> commitPending(pending)
        }
    }

    private fun configureOrCommit(pending: HomeWidgetPending) {
        val info = try {
            manager.getAppWidgetInfo(pending.id)
        } catch (exception: RuntimeException) {
            null
        }
        if (info == null || !matchesPending(pending, info)) {
            cancelPending(pending)
            return
        }
        if (!requiresInitialConfiguration(info)) {
            commitPending(pending)
            return
        }
        val configuring = pending.withPhase(HomeWidgetPending.PHASE_CONFIGURE)
        if (!savePending(configuring)) {
            cancelPending(configuring)
            return
        }
        try {
            host.startAppWidgetConfigureActivityForResult(
                activity,
                pending.id,
                Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
                REQUEST_CODE,
                getActivityOptionsBundle()
            )
        } catch (exception: ActivityNotFoundException) {
            cancelPending(configuring)
        } catch (exception: RuntimeException) {
            cancelPending(configuring)
        }
    }

    private fun commitPending(pending: HomeWidgetPending) {
        val record = pending.toRecord()
        val info = try {
            manager.getAppWidgetInfo(record.id)
        } catch (exception: RuntimeException) {
            null
        }
        if (info == null || !matchesPending(pending, info)) {
            cancelPending(pending)
            return
        }
        if (pending.phase != HomeWidgetPending.PHASE_COMMIT &&
            !savePending(pending.withPhase(HomeWidgetPending.PHASE_COMMIT))
        ) {
            cancelPending(pending)
            return
        }
        if (!isGridReady()) {
            restoreQueued = true
            return
        }
        val item = attachRecord(record, info)
        if (item.parent === container && saveCommittedRecord(record)) return
        items.remove(record.id)
        container.removeItem(item, true)
        deleteWidgetId(record.id)
        clearPending()
    }

    private fun attachRecord(record: HomeWidgetRecord, info: AppWidgetProviderInfo): DraggableGridItem {
        val item = DraggableGridItem(activity)
        item.itemId = record.itemId
        val hostView = try {
            host.createView(activity, record.id, info)
        } catch (exception: RuntimeException) {
            android.util.Log.w(TAG, "Could not create home widget ${record.id}", exception)
            return item
        }
        hostView.contentDescription = info.loadLabel(activity.packageManager)
        // Holding a widget picks it up: edit mode on, the finger keeps dragging it (see HomeWidgetHostView).
        hostView.setOnLongClickListener {
            Vibrations.getInstance().vibrate()
            editMode(true)
            showWidgetMenu(record, hostView)
            true
        }
        (hostView as? HomeWidgetHostView)?.onHoldDrag = { dismissMenu() }
        item.addView(hostView)
        container.addItem(item, layoutDataFor(record, info))
        if (item.parent === container) items[record.id] = item
        return item
    }

    fun dismissMenu() {
        openMenu?.dismiss()
    }

    private fun showWidgetMenu(record: HomeWidgetRecord, anchor: View) {
        val resources = activity.resources
        val menu = PopupMenu(activity)
        val info = try {
            manager.getAppWidgetInfo(record.id)
        } catch (exception: RuntimeException) {
            null
        }
        if (info != null && canReconfigure(info)) {
            menu.add(PopupMenu.Item(
                resources.getString(R.string.configure_widget),
                AppCompatResources.getDrawable(activity, R.drawable.ic_edit),
                View.OnClickListener { reconfigure(record.id) }
            ))
        }
        menu.add(PopupMenu.Item(
            resources.getString(R.string.remove),
            AppCompatResources.getDrawable(activity, R.drawable.ic_delete),
            View.OnClickListener { removeWidget(record.id) }
        ))
        menu.setOnDismissListener {
            if (openMenu === menu) openMenu = null
            anchor.animate().scaleY(1f).scaleX(1f).alpha(1f)
                .setDuration(Animation.SHORT.getDuration().toLong())
        }
        openMenu = menu
        // Touches stay enabled: the finger that opened the menu is still down and may start dragging the widget.
        // The margins keep the popup's transparent edge (20dp, less the 9dp gap) off the widget's bottom/right,
        // where the resize handle is.
        val clear = Rect(Measurements.dpToPx(11f), Measurements.dpToPx(11f), 0, 0)
        menu.show(activity, anchor, clear, PopupMenu.PIVOT_CENTER_HORIZONTAL, false)?.apply {
            // Not modal: a touch outside closes the menu and still reaches the grid (handle, drag, tap-to-finish).
            isFocusable = false
            isOutsideTouchable = true
            update()
        }
    }

    private fun reconfigure(identifier: Int) {
        try {
            host.startAppWidgetConfigureActivityForResult(
                activity,
                identifier,
                Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
                REQUEST_CODE,
                getActivityOptionsBundle()
            )
        } catch (exception: RuntimeException) {
            Toast.makeText(activity, R.string.something_went_wrong, Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeWidget(identifier: Int) {
        removeRecord(identifier)
        items.remove(identifier)?.let { container.removeItem(it, true) }
        deleteWidgetId(identifier)
    }

    private fun removeWidgetAfterHostRemoval(identifier: Int) {
        removeRecord(identifier)
        val pending = loadPending()
        if (pending?.id == identifier) clearPending()
        items.remove(identifier)?.let { container.removeItem(it, true) }
    }

    private fun cancelPending(pending: HomeWidgetPending) {
        clearPending()
        deleteWidgetId(pending.id)
    }

    private fun resumePendingPlacement() {
        val pending = loadPending() ?: return
        if (pending.phase == HomeWidgetPending.PHASE_COMMIT) commitPending(pending)
    }

    private fun reconcileHostIds() {
        val known = loadRecords().mapTo(HashSet()) { it.id }
        loadPending()?.let { known.add(it.id) }
        val hostedIds = try {
            host.appWidgetIds
        } catch (exception: RuntimeException) {
            IntArray(0)
        }
        for (identifier in hostedIds) {
            if (!known.contains(identifier)) deleteWidgetId(identifier)
        }
    }

    private fun getAvailableInfo(record: HomeWidgetRecord): AppWidgetProviderInfo? {
        val user = userManager.getUserForSerialNumber(record.userSerial) ?: return null
        if (isProfileUnavailable(user)) return null
        val info = try {
            manager.getAppWidgetInfo(record.id)
        } catch (exception: RuntimeException) {
            null
        } ?: return null
        return if (matchesRecord(record, info)) info else null
    }

    private fun matchesPending(pending: HomeWidgetPending, info: AppWidgetProviderInfo): Boolean =
        info.provider.flattenToString() == pending.provider &&
            userManager.getSerialNumberForUser(info.profile) == pending.userSerial

    private fun matchesRecord(record: HomeWidgetRecord, info: AppWidgetProviderInfo): Boolean =
        info.provider.flattenToString() == record.provider &&
            userManager.getSerialNumberForUser(info.profile) == record.userSerial

    private fun HomeWidgetPending.withPhase(phase: String): HomeWidgetPending = HomeWidgetPending(
        id,
        provider,
        userSerial,
        spanX,
        spanY,
        phase
    )

    private fun isProfileUnavailable(user: UserHandle): Boolean = try {
        userManager.isQuietModeEnabled(user) || !userManager.isUserUnlocked(user)
    } catch (exception: RuntimeException) {
        true
    }

    private fun layoutDataFor(record: HomeWidgetRecord, info: AppWidgetProviderInfo): DynamicGridLayout.ItemLayoutData {
        val limits = limitsFor(info)
        val span = HomeWidgetSizing.initialSpan(
            HomeWidgetSpan(record.spanX, record.spanY),
            limits,
            container.getCellWidth(),
            container.getCellHeight(),
            container.getColumnCount(),
            container.getRowCount()
        )
        val bounds = HomeWidgetSizing.bounds(
            span,
            limits,
            container.getCellWidth(),
            container.getCellHeight(),
            container.getColumnCount(),
            container.getRowCount(),
            settings.getBoolean(FORCE_RESIZE_KEY, true)
        )
        return DynamicGridLayout.ItemLayoutData(record.itemId, 0, 0, span.columns, span.rows).apply {
            minColSpan = bounds.minColSpan
            minRowSpan = bounds.minRowSpan
            maxColSpan = bounds.maxColSpan
            maxRowSpan = bounds.maxRowSpan
            minWidth = bounds.minWidth
            minHeight = bounds.minHeight
            maxWidth = bounds.maxWidth
            maxHeight = bounds.maxHeight
        }
    }

    private fun limitsFor(info: AppWidgetProviderInfo): HomeWidgetProviderLimits {
        val resizeMode = info.resizeMode
        val targetWidth = if (Utils.isMinimumSDK(Build.VERSION_CODES.S)) info.targetCellWidth else 0
        val targetHeight = if (Utils.isMinimumSDK(Build.VERSION_CODES.S)) info.targetCellHeight else 0
        val padding = AppWidgetHostView.getDefaultPaddingForWidget(activity, info.provider, null)
        val horizontalPadding = padding.left + padding.right + Measurements.dpToPx(DraggableGridItem.PADDING_DP.toFloat()) * 2
        val verticalPadding = padding.top + padding.bottom + Measurements.dpToPx(DraggableGridItem.PADDING_DP.toFloat()) * 2
        return HomeWidgetProviderLimits(
            outerPixels(info.minWidth, horizontalPadding),
            outerPixels(info.minHeight, verticalPadding),
            outerPixels(info.minResizeWidth, horizontalPadding),
            outerPixels(info.minResizeHeight, verticalPadding),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) outerPixels(info.maxResizeWidth, horizontalPadding) else 0,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) outerPixels(info.maxResizeHeight, verticalPadding) else 0,
            targetWidth,
            targetHeight,
            (resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0,
            (resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL) != 0
        )
    }

    private fun outerPixels(value: Int, padding: Int): Int = if (value > 0) value + padding else 0

    private fun optionsForSpan(span: HomeWidgetSpan): Bundle {
        val density = Measurements.getDensity()
        val widthDp = maxOf(1, (maxOf(1, container.getCellWidth()) * span.columns / density).toInt())
        val heightDp = maxOf(1, (maxOf(1, container.getCellHeight()) * span.rows / density).toInt())
        return Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                putParcelableArrayList(
                    AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    arrayListOf(android.util.SizeF(widthDp.toFloat(), heightDp.toFloat()))
                )
            }
        }
    }

    private fun requiresInitialConfiguration(info: AppWidgetProviderInfo): Boolean {
        if (info.configure == null) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) == 0
    }

    private fun canReconfigure(info: AppWidgetProviderInfo): Boolean {
        if (info.configure == null) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
            (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0
    }

    private fun loadRecords(): List<HomeWidgetRecord> {
        val records = ArrayList<HomeWidgetRecord>()
        val stale = ArrayList<String>()
        for ((key, value) in store.all) {
            if (!key.startsWith(WIDGET_PREFIX)) continue
            val record = HomeWidgetRecord.deserialize(value as? String)
            if (record == null) {
                stale.add(key)
            } else {
                records.add(record)
            }
        }
        if (stale.isNotEmpty()) {
            val edit = store.edit()
            for (key in stale) edit.remove(key)
            edit.apply()
        }
        return records
    }

    private fun saveCommittedRecord(record: HomeWidgetRecord): Boolean = store.edit()
        .putString(WIDGET_PREFIX + record.id, record.serialize())
        .remove(PENDING_KEY)
        .commit()

    private fun removeRecord(identifier: Int) {
        store.edit().remove(WIDGET_PREFIX + identifier).apply()
    }

    private fun savePending(pending: HomeWidgetPending): Boolean = store.edit()
        .putString(PENDING_KEY, pending.serialize())
        .commit()

    private fun loadPending(): HomeWidgetPending? = HomeWidgetPending.deserialize(store.getString(PENDING_KEY, null))

    private fun clearPending() {
        store.edit().remove(PENDING_KEY).apply()
    }

    private fun deleteWidgetId(identifier: Int) {
        try {
            host.deleteAppWidgetId(identifier)
        } catch (exception: RuntimeException) {
            android.util.Log.w(TAG, "Could not delete home widget $identifier", exception)
        }
    }

    private fun registerProfileReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
        }
        profileReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE) configurator?.dismiss()
                reload()
            }
        }
        ContextCompat.registerReceiver(activity, profileReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    companion object {
        const val FORCE_RESIZE_KEY = "com.stario.HomeWidgets.FORCE_RESIZE"
        private const val TAG = "HomeWidgets"
        private const val STORE_NAME = "home"
        private const val WIDGET_PREFIX = "widget_"
        private const val PENDING_KEY = "pending"
        private const val SAVED_REQUEST_KEY = "home_widgets.pending_request_id"
        private const val HOST_ID = 403219
        private const val REQUEST_CODE = 403220

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
