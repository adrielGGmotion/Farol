package com.farol.launcher

import android.view.View
import android.widget.TextView
import com.farol.launcher.activities.settings.Settings
import com.farol.launcher.apps.ProfileManager
import com.farol.launcher.preferences.Entry
import com.farol.launcher.preferences.Vibrations
import com.farol.launcher.themes.Theme
import com.farol.launcher.themes.ThemedActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SettingsNavigationTest.TestApplication::class,
    qualifiers = "w440dp-h800dp-port-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsNavigationTest {
    class TestApplication : Farol() {
        override fun onCreate() {
            Vibrations.from(this)
            ProfileManager.from(this, false)
        }
    }

    private lateinit var controller: ActivityController<Settings>
    private val activity get() = controller.get()

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication() as Farol
        application.getSharedPreferences(Entry.THEME).edit()
            .putString(ThemedActivity.THEME, Theme.THEME_BLUE.toString()).commit()
        controller = Robolectric.buildActivity(Settings::class.java).setup()
    }

    @After
    fun tearDown() {
        if (::controller.isInitialized) controller.pause().stop().destroy()
    }

    @Test
    fun openingAndSystemBackFollowThePageHierarchy() {
        openStartScreen()
        activity.onBackPressedDispatcher.onBackPressed()
        assertPage(R.id.group_home, R.string.home_appearance)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.start_screen_settings).visibility)
        activity.onBackPressedDispatcher.onBackPressed()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.overview).visibility)
        assertFalse(activity.isFinishing)
    }

    @Test
    fun recreationRestoresStartScreenAndItsBackDestination() {
        openStartScreen()
        controller.recreate()
        assertPage(R.id.start_screen_settings, R.string.home)
        activity.onBackPressedDispatcher.onBackPressed()
        assertPage(R.id.group_home, R.string.home_appearance)
        assertFalse(activity.isFinishing)
    }

    private fun openStartScreen() {
        assertTrue(activity.findViewById<View>(R.id.category_home).performClick())
        assertPage(R.id.group_home, R.string.home_appearance)
        assertTrue(activity.findViewById<View>(R.id.home).performClick())
        assertPage(R.id.start_screen_settings, R.string.home)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.group_home).visibility)
    }

    private fun assertPage(id: Int, title: Int) {
        assertEquals(View.VISIBLE, activity.findViewById<View>(id).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.content).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.overview).visibility)
        assertEquals(activity.getString(title), activity.findViewById<TextView>(R.id.title_portrait).text)
    }
}
