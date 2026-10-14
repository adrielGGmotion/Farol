package com.farol.launcher

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.farol.launcher.preferences.Entry
import com.farol.launcher.sheet.widgets.WidgetSize
import com.farol.launcher.sheet.widgets.configurator.WidgetConfigurator
import com.farol.launcher.sheet.widgets.configurator.WidgetItemAdapter
import com.farol.launcher.sheet.widgets.configurator.WidgetListAdapter
import com.farol.launcher.themes.Theme
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.ui.Measurements
import com.farol.launcher.ui.widgets.RoundedWidgetHost
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowViewGroup
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = WidgetItemAdapterTest.TestApplication::class,
    shadows = [WidgetItemAdapterTest.PlatformWidgetHostShadow::class],
    qualifiers = "w440dp-h800dp-port-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class WidgetItemAdapterTest {
    class TestApplication : Farol() {
        override fun onCreate() = Unit
    }

    class TestActivity : ThemedActivity() {
        override fun isOpaque() = false
        override fun isAffectedByBackGesture() = false
    }

    @Implements(AppWidgetHostView::class)
    class PlatformWidgetHostShadow : ShadowViewGroup()

    private lateinit var controller: ActivityController<TestActivity>
    private lateinit var root: FrameLayout
    private val application get() = RuntimeEnvironment.getApplication() as Farol
    private val icon = ColorDrawable(Color.BLUE)

    @Before
    fun setUp() {
        application.getSharedPreferences(Entry.THEME).edit().clear()
            .putString(ThemedActivity.THEME, Theme.THEME_BLUE.toString()).commit()
        controller = Robolectric.buildActivity(TestActivity::class.java).create()
        root = FrameLayout(controller.get())
        controller.get().setContentView(root)
        Measurements.measure(root, null)
        controller.start().resume().visible()
    }

    @After
    fun tearDown() {
        if (::controller.isInitialized) controller.pause().stop().destroy()
        application.getSharedPreferences(Entry.THEME).edit().clear().commit()
    }

    @Test
    fun unsupportedLayoutFallsBackToPreviewImage() {
        val info = provider(R.layout.widget_picker, android.R.drawable.ic_dialog_info)
        assertNotNull(info.loadPreviewImage(controller.get(), 160))
        val adapter = adapter(info)
        val holder = adapter.onCreateViewHolder(root, 0)

        adapter.onBindViewHolder(holder, 0)

        assertEquals(1, holder.preview.childCount)
        assertTrue(holder.preview.getChildAt(0) is ImageView)
        val image = holder.preview.getChildAt(0) as ImageView
        assertNotNull(image.drawable)
        assertNotSame(icon, image.drawable)
        holder.itemView.measure(View.MeasureSpec.makeMeasureSpec(440, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        holder.itemView.layout(0, 0, 440, holder.itemView.measuredHeight)
        assertTrue(image.width > 0 && image.height > 0)
    }

    @Test
    fun unsupportedLayoutWithoutImageUsesIconAndRemainsSelectable() {
        val info = provider(R.layout.widget_picker, 0)
        var selected: AppWidgetProviderInfo? = null
        val adapter = adapter(info, WidgetConfigurator.Request { chosen, size ->
            selected = chosen
            assertEquals(WidgetSize.SMALL, size)
        })
        val holder = adapter.onCreateViewHolder(root, 0)

        adapter.onBindViewHolder(holder, 0)

        assertEquals(1, holder.preview.childCount)
        assertTrue(holder.preview.getChildAt(0) is ImageView)
        assertSame(icon, (holder.preview.getChildAt(0) as ImageView).drawable)
        holder.itemView.performClick()
        assertSame(info, selected)
    }

    @Test
    fun supportedLayoutKeepsItsHostedPreview() {
        val adapter = adapter(provider(android.R.layout.simple_list_item_1, 0))
        val holder = adapter.onCreateViewHolder(root, 0)

        adapter.onBindViewHolder(holder, 0)

        assertEquals(1, holder.preview.childCount)
        val host = holder.preview.getChildAt(0) as RoundedWidgetHost
        assertEquals(android.R.id.text1, host.getChildAt(0).id)
    }

    @Test
    fun rebindingReplacesThePreviousPreviewAndClearsSelection() {
        val adapter = adapter(provider(0, android.R.drawable.ic_dialog_info))
        val holder = adapter.onCreateViewHolder(root, 0)
        adapter.onBindViewHolder(holder, 0)
        holder.preview.alpha = 0.3f
        holder.options.visibility = View.VISIBLE

        adapter.onBindViewHolder(holder, 0)

        assertEquals(1, holder.preview.childCount)
        assertEquals(1f, holder.preview.alpha)
        assertEquals(View.INVISIBLE, holder.options.visibility)
    }

    private fun adapter(
        info: AppWidgetProviderInfo,
        request: WidgetConfigurator.Request = WidgetConfigurator.Request { _, _ -> }
    ): WidgetItemAdapter {
        val entry = WidgetListAdapter.WidgetGroupEntry(application.packageName, "Widgets", icon)
        entry.addWidget(info)
        return WidgetItemAdapter(controller.get(), entry, request, showSizes = false)
    }

    private fun provider(layout: Int, image: Int): AppWidgetProviderInfo {
        val receiver = ActivityInfo().apply {
            applicationInfo = application.applicationInfo
            packageName = application.packageName
            name = "PreviewProvider"
            nonLocalizedLabel = "Preview widget"
        }
        return AppWidgetProviderInfo().apply {
            provider = ComponentName(application.packageName, receiver.name)
            previewLayout = layout
            previewImage = image
            ReflectionHelpers.setField(this, "providerInfo", receiver)
        }
    }
}
