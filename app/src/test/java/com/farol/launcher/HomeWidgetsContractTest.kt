package com.farol.launcher

import com.google.gson.JsonParser
import com.farol.launcher.activities.launcher.widgets.home.HomeWidgetBounds
import com.farol.launcher.activities.launcher.widgets.home.HomeWidgetPending
import com.farol.launcher.activities.launcher.widgets.home.HomeWidgetProviderLimits
import com.farol.launcher.activities.launcher.widgets.home.HomeWidgetRecord
import com.farol.launcher.activities.launcher.widgets.home.HomeWidgetSizing
import com.farol.launcher.activities.launcher.widgets.home.HomeWidgetSpan
import com.farol.launcher.ui.common.grid.DynamicGridLayout
import com.farol.launcher.ui.common.grid.GridTemplateManager
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class HomeWidgetsContractTest {
    @Test
    fun homeWidgetRecordKeepsProviderUserAndResizeLimits() {
        val record = HomeWidgetRecord(42, "com.example/.Clock", 10L, 2, 1)
        assertEquals(
            JsonParser.parseString("""{"id":42,"provider":"com.example/.Clock","userSerial":10,"spanX":2,"spanY":1}"""),
            JsonParser.parseString(record.serialize())
        )
        val restored = requireNotNull(HomeWidgetRecord.deserialize(record.serialize()))
        assertEquals("HomeWidget:42", restored.itemId)
        assertEquals("com.example/.Clock", restored.provider)
        assertEquals(10L, restored.userSerial)
        assertNull(HomeWidgetRecord.deserialize("""{"id":42,"spanX":2,"spanY":1}"""))

        val pending = HomeWidgetPending(42, "com.example/.Clock", 10L, 2, 1, HomeWidgetPending.PHASE_BIND)
        assertEquals(record.serialize(), pending.toRecord().serialize())
        assertNull(HomeWidgetPending.deserialize(pending.serialize().replace(HomeWidgetPending.PHASE_BIND, "done")))

        val limits = HomeWidgetProviderLimits(
            minWidthPx = 250,
            minHeightPx = 80,
            minResizeWidthPx = 150,
            minResizeHeightPx = 80,
            maxResizeWidthPx = 350,
            maxResizeHeightPx = 0,
            targetCellWidth = 3,
            targetCellHeight = 1,
            resizeHorizontal = true,
            resizeVertical = false
        )
        val initial = HomeWidgetSizing.initialSpan(HomeWidgetSpan(1, 1), limits, 100, 100, 5, 5)
        assertEquals(HomeWidgetSpan(3, 1), initial)
        val bounds = HomeWidgetSizing.bounds(initial, limits, 100, 100, 5, 5)
        assertEquals(2, bounds.minColSpan)
        assertEquals(3, bounds.maxColSpan)
        assertEquals(1, bounds.minRowSpan)
        assertEquals(1, bounds.maxRowSpan)
        assertEquals(350, bounds.maxWidth)
    }

    @Test
    fun sizingClampsFixedAxesMaxAndUndersizedGrid() {
        val fixed = HomeWidgetProviderLimits(
            minWidthPx = 80,
            minHeightPx = 80,
            minResizeWidthPx = 40,
            minResizeHeightPx = 40,
            maxResizeWidthPx = 500,
            maxResizeHeightPx = 500,
            targetCellWidth = 0,
            targetCellHeight = 0,
            resizeHorizontal = false,
            resizeVertical = false
        )
        val fixedInitial = HomeWidgetSizing.initialSpan(HomeWidgetSpan(2, 3), fixed, 100, 100, 4, 5)
        val fixedBounds = HomeWidgetSizing.bounds(fixedInitial, fixed, 100, 100, 4, 5)
        assertEquals(2, fixedBounds.minColSpan)
        assertEquals(2, fixedBounds.maxColSpan)
        assertEquals(3, fixedBounds.minRowSpan)
        assertEquals(3, fixedBounds.maxRowSpan)

        val maxed = HomeWidgetProviderLimits(
            minWidthPx = 151,
            minHeightPx = 151,
            minResizeWidthPx = 151,
            minResizeHeightPx = 151,
            maxResizeWidthPx = 249,
            maxResizeHeightPx = 249,
            targetCellWidth = 0,
            targetCellHeight = 0,
            resizeHorizontal = true,
            resizeVertical = true
        )
        val maxedInitial = HomeWidgetSizing.initialSpan(HomeWidgetSpan(4, 4), maxed, 100, 100, 5, 5)
        val maxedBounds = HomeWidgetSizing.bounds(maxedInitial, maxed, 100, 100, 5, 5)
        assertEquals(2, maxedBounds.minColSpan)
        assertEquals(2, maxedBounds.maxColSpan)
        assertEquals(2, maxedBounds.minRowSpan)
        assertEquals(2, maxedBounds.maxRowSpan)

        val oversized = HomeWidgetSizing.initialSpan(HomeWidgetSpan(5, 5), maxed, 100, 100, 1, 1)
        val oversizedBounds = HomeWidgetSizing.bounds(oversized, maxed, 100, 100, 1, 1)
        assertEquals(HomeWidgetSpan(1, 1), oversized)
        assertEquals(1, oversizedBounds.minColSpan)
        assertEquals(1, oversizedBounds.maxColSpan)
        assertEquals(1, oversizedBounds.minRowSpan)
        assertEquals(1, oversizedBounds.maxRowSpan)
    }

    @Test
    fun forcedResizeIgnoresProviderLimits() {
        val fixed = HomeWidgetProviderLimits(
            minWidthPx = 250,
            minHeightPx = 250,
            minResizeWidthPx = 0,
            minResizeHeightPx = 0,
            maxResizeWidthPx = 399,
            maxResizeHeightPx = 399,
            targetCellWidth = 0,
            targetCellHeight = 0,
            resizeHorizontal = false,
            resizeVertical = false
        )
        val initial = HomeWidgetSizing.initialSpan(HomeWidgetSpan(1, 1), fixed, 100, 100, 5, 5)
        assertEquals(HomeWidgetSpan(3, 3), initial)
        val declared = HomeWidgetSizing.bounds(initial, fixed, 100, 100, 5, 5)
        assertEquals(3, declared.minColSpan)
        assertEquals(3, declared.maxColSpan)
        val forced = HomeWidgetSizing.bounds(initial, fixed, 100, 100, 5, 5, forceResizable = true)
        assertEquals(HomeWidgetBounds(1, 1, -1, -1, -1, -1, -1, -1), forced)
    }

    @Test
    fun pendingStateRejectsCorruptionButKeepsCommitPhase() {
        assertNull(HomeWidgetPending.deserialize("""{"id":1,"provider":"","userSerial":0,"spanX":1,"spanY":1,"phase":"bind"}"""))
        assertNull(HomeWidgetPending.deserialize("""{"id":1,"provider":"com.example/.Clock","userSerial":0,"spanX":1,"spanY":1,"phase":"done"}"""))
        val pending = HomeWidgetPending(1, "com.example/.Clock", 0, 1, 1, HomeWidgetPending.PHASE_COMMIT)
        assertEquals(HomeWidgetPending.PHASE_COMMIT, HomeWidgetPending.deserialize(pending.serialize())?.phase)
    }

    @Test
    fun coldStartRejectsUnfinishedSetupButKeepsReadyPlacement() {
        for (phase in listOf(HomeWidgetPending.PHASE_BIND, HomeWidgetPending.PHASE_CONFIGURE)) {
            val pending = HomeWidgetPending(42, "com.example/.Clock", 0, 2, 1, phase)
            val restored = requireNotNull(HomeWidgetPending.deserialize(pending.serialize()))
            assertFalse(phase, restored.canRestore(null))
        }
        val ready = HomeWidgetPending(42, "com.example/.Clock", 0, 2, 1, HomeWidgetPending.PHASE_COMMIT)
        val restored = requireNotNull(HomeWidgetPending.deserialize(ready.serialize()))
        assertTrue(restored.canRestore(null))
    }

    @Test
    fun activityRecreationPreservesOnlyMatchingSetup() {
        for (phase in listOf(HomeWidgetPending.PHASE_BIND, HomeWidgetPending.PHASE_CONFIGURE)) {
            val pending = HomeWidgetPending(42, "com.example/.Clock", 10, 2, 1, phase)
            val restored = requireNotNull(HomeWidgetPending.deserialize(pending.serialize()))
            assertTrue(phase, restored.canRestore(42))
            assertFalse(phase, restored.canRestore(43))
            assertFalse(phase, restored.canRestore(0))
        }
    }

    @Test
    fun launcherKeepsActivityStateForWidgetSetup() {
        val activities = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml")).getElementsByTagName("activity")
        val launcher = (0 until activities.length).map { activities.item(it) as Element }
            .single { it.getAttribute("android:name") == ".activities.launcher.Launcher" }
        assertFalse(launcher.getAttribute("android:stateNotNeeded") == "true")
    }

    @Test
    fun gridPersistenceRetainsUnattachedAndForgetsExplicitRemoval() {
        val first = DynamicGridLayout.ItemLayoutData("HomeWidget:1", 0, 0, 1, 1)
        val later = DynamicGridLayout.ItemLayoutData("HomeWidget:2", 1, 0, 1, 1)
        val movedFirst = DynamicGridLayout.ItemLayoutData("HomeWidget:1", 2, 0, 1, 1)
        val existing = mapOf(first.id to first, later.id to later)
        val merged = GridTemplateManager.mergeLayout(existing, mapOf(movedFirst.id to movedFirst))
        assertEquals(2, merged.size)
        assertEquals(2, merged.getValue("HomeWidget:1").col)
        assertEquals(1, merged.getValue("HomeWidget:2").col)

        val forgotten = GridTemplateManager.forgetLayoutItem(merged, "HomeWidget:1")
        assertNull(forgotten["HomeWidget:1"])
        assertEquals(1, forgotten.getValue("HomeWidget:2").col)
    }
}
