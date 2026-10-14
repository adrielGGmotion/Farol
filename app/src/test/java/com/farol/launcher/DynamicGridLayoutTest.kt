package com.farol.launcher

import android.appwidget.AppWidgetManager
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.RelativeLayout
import com.farol.launcher.preferences.Entry
import com.farol.launcher.themes.Theme
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.ui.Measurements
import com.farol.launcher.ui.common.grid.DraggableGridItem
import com.farol.launcher.ui.common.grid.DynamicGridLayout
import com.farol.launcher.ui.widgets.HomeWidgetHostView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = DynamicGridLayoutTest.TestApplication::class,
    qualifiers = "w440dp-h800dp-port-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class DynamicGridLayoutTest {
    class TestApplication : Farol() {
        override fun onCreate() = Unit
    }

    class TestActivity : ThemedActivity() {
        override fun isOpaque() = false
        override fun isAffectedByBackGesture() = false
    }

    private lateinit var controller: ActivityController<TestActivity>
    private lateinit var grid: DynamicGridLayout
    private val application get() = RuntimeEnvironment.getApplication() as Farol
    private val gridPreferences get() = application.getSharedPreferences(
        Entry.GRID_TEMPLATE_MANAGER, "dynamic-grid-test"
    )

    @Before
    fun setUp() {
        gridPreferences.edit().clear().commit()
        application.getSharedPreferences(Entry.THEME).edit().clear()
            .putString(ThemedActivity.THEME, Theme.THEME_BLUE.toString()).commit()
        controller = Robolectric.buildActivity(TestActivity::class.java).create()
        val activity = controller.get()
        val root = FrameLayout(activity)
        activity.setContentView(root)
        Measurements.measure(root, null)
        grid = DynamicGridLayout(activity, Robolectric.buildAttributeSet()
            .addAttribute(R.attr.grid_preference_key, "dynamic-grid-test").build())
        root.addView(grid, FrameLayout.LayoutParams(440, 550))
        controller.start().resume().visible()
        layoutGrid()
        assertEquals(4, grid.getColumnCount())
        assertEquals(5, grid.getRowCount())
        assertEquals(110, grid.getCellWidth())
        assertEquals(110, grid.getCellHeight())
    }

    @After
    fun tearDown() {
        if (::controller.isInitialized) controller.pause().stop().destroy()
        gridPreferences.edit().clear().commit()
        application.getSharedPreferences(Entry.THEME).edit().clear().commit()
    }

    @Test
    fun heldWidgetCornerEntersEditingAndDragsWithoutResizing() {
        val item = addItem("widget", 0, 0, 1, 1)
        val host = HomeWidgetHostView(controller.get(),
            RelativeLayout.LayoutParams(-1, -1),
            AppWidgetManager.getInstance(application), 1)
        item.addView(host)
        var enteredEditing = false
        var holdDragged = false
        host.setOnLongClickListener {
            enteredEditing = true
            grid.setRearrangeable(true)
            true
        }
        host.onHoldDrag = { holdDragged = true }
        layoutGrid()
        val originalWidth = item.width
        val originalHeight = item.height
        val x = host.width - 1f
        val y = host.height - 1f

        touch(host, MotionEvent.ACTION_DOWN, x, y)
        assertFalse(enteredEditing)
        assertTrue(host.performLongClick())
        assertTrue(enteredEditing)
        touch(host, MotionEvent.ACTION_MOVE, x + 110, y + 110)

        assertTrue(holdDragged)
        assertEquals(true, item.getTag(R.id.is_dragging_tag))
        assertEquals(110, item.left)
        assertEquals(110, item.top)
        touch(host, MotionEvent.ACTION_UP, x, y)
        settle()
        assertEquals(originalWidth, item.width)
        assertEquals(originalHeight, item.height)
        assertRectangles(item to Rect(1, 1, 2, 2))
    }

    @Test
    fun freshCornerTouchWhileEditingStillResizes() {
        val item = addItem("active", 0, 0, 1, 1)
        growFromCorner(item)
        assertNull(item.getTag(R.id.is_dragging_tag))
        assertRectangles(item to Rect(0, 0, 2, 2))
    }

    @Test
    fun crowdedResizeFallsBackToVerticalGrowth() {
        val active = addItem("active", 0, 0, 1, 1)
        val blocker = addItem("blocker", 1, 0, 3, 5)
        assertRectangles(active to Rect(0, 0, 1, 1), blocker to Rect(1, 0, 4, 5))

        growFromCorner(active)

        assertRectangles(active to Rect(0, 0, 1, 2), blocker to Rect(1, 0, 4, 5))
    }

    @Test
    fun fullyBlockedGrowthLeavesEveryRectangleUnchanged() {
        val active = addItem("active", 0, 0, 1, 1)
        val right = addItem("right", 1, 0, 3, 5)
        val below = addItem("below", 0, 1, 1, 4)
        val expected = arrayOf(active to Rect(0, 0, 1, 1),
            right to Rect(1, 0, 4, 5), below to Rect(0, 1, 1, 5))
        assertRectangles(*expected)

        growFromCorner(active)

        assertRectangles(*expected)
    }

    private fun addItem(id: String, col: Int, row: Int, cols: Int, rows: Int): DraggableGridItem {
        val item = DraggableGridItem(controller.get()).apply { itemId = id }
        grid.addItem(item, DynamicGridLayout.ItemLayoutData(id, col, row, cols, rows))
        layoutGrid()
        return item
    }

    private fun growFromCorner(item: DraggableGridItem) {
        grid.setRearrangeable(true)
        val x = item.right - 1f
        val y = item.bottom - 1f
        touch(grid, MotionEvent.ACTION_DOWN, x, y)
        touch(grid, MotionEvent.ACTION_MOVE, x + grid.getCellWidth(), y + grid.getCellHeight())
        touch(grid, MotionEvent.ACTION_UP, x + grid.getCellWidth(), y + grid.getCellHeight())
        settle()
    }

    private fun touch(view: View, action: Int, x: Float, y: Float) {
        val origin = IntArray(2)
        view.getLocationOnScreen(origin)
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x + origin[0], y + origin[1], 0)
        event.offsetLocation(-origin[0].toFloat(), -origin[1].toFloat())
        try {
            view.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun layoutGrid() {
        grid.measure(View.MeasureSpec.makeMeasureSpec(440, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(550, View.MeasureSpec.EXACTLY))
        grid.layout(0, 0, grid.measuredWidth, grid.measuredHeight)
    }

    private fun settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        layoutGrid()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        layoutGrid()
    }

    private fun assertRectangles(vararg expected: Pair<DraggableGridItem, Rect>) {
        assertEquals(expected.size, grid.childCount)
        val bounds = expected.map { (item, cells) ->
            val actual = Rect(item.left, item.top, item.right, item.bottom)
            assertEquals(item.itemId, Rect(cells.left * grid.getCellWidth(),
                cells.top * grid.getCellHeight(), cells.right * grid.getCellWidth(),
                cells.bottom * grid.getCellHeight()), actual)
            actual
        }
        for (i in bounds.indices) {
            for (j in i + 1 until bounds.size) {
                assertFalse("Items $i and $j overlap", Rect.intersects(bounds[i], bounds[j]))
            }
        }
    }
}
