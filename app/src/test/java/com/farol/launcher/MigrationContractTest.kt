package com.farol.launcher

import com.google.gson.JsonParser
import com.farol.launcher.preferences.Entry
import com.farol.launcher.sheet.widgets.Widget
import com.farol.launcher.sheet.widgets.WidgetSize
import com.farol.launcher.utils.Casing
import com.farol.launcher.utils.objects.ObjectDelegate
import com.farol.launcher.utils.objects.ObservableObject
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationContractTest {
    @Test
    fun compiledDrawingAndWeatherPathsPreserveJavaSemantics() {
        val classes = File(requireNotNull(Widget::class.java.protectionDomain?.codeSource?.location).toURI())
        val process = ProcessBuilder(
            File(System.getProperty("java.home"), "bin/javap").path,
            "-c", "-p", "-classpath", classes.path,
            "com.farol.launcher.ui.common.text.WaveUnderlineTextView",
            "com.farol.launcher.ui.common.text.PulsingTextView",
            "com.farol.launcher.activities.launcher.widgets.glance.extensions.weather.Weather"
        ).redirectErrorStream(true).start()
        val bytecode = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(bytecode, 0, process.waitFor())

        val underline = bytecode.substringAfter("protected void onDraw(android.graphics.Canvas);")
            .substringBefore("protected void onAttachedToWindow();")
        val gradient = bytecode.substringAfter("private final float[] generatePositions();")
            .substringBefore("Compiled from \"Weather.kt\"")
        val ipLookup = "getPublicIPAddress:()Ljava/lang/String;"
        val locationLookup = "loadApproximatedLocation:(Ljava/lang/String;)V"
        assertTrue(bytecode.contains(ipLookup))
        assertTrue(bytecode.contains(locationLookup))
        val missingIpPath = bytecode.substringAfter(ipLookup).substringBefore(locationLookup)
        val checks = mapOf(
            "underline uses TextView paint" to underline.contains("getPaint:()Landroid/text/TextPaint;"),
            "gradient uses floating-point division" to
                (gradient.contains("fdiv") && !Regex("\\bidiv\\b").containsMatchIn(gradient)),
            "missing IP preserves cached coordinates" to
                !Regex("\\breturn\\b").containsMatchIn(missingIpPath)
        )
        assertEquals(emptyMap<String, Boolean>(), checks.filterValues { !it })
    }

    @Test
    fun preferenceKeysAndPrefixValidation() {
        val names = listOf(
            "CATEGORY_APPLICATION_MAP", "GRID_TEMPLATE_MANAGER", "APPLICATION_LABELS",
            "PINNED_CATEGORY", "CATEGORY_NAMES", "CATEGORY_MAP", "HIDDEN_APPS",
            "CATEGORIES", "BRIEFING", "WEATHER", "WIDGETS", "SEARCH", "STARIO",
            "DRAWER", "SHEET", "THEME", "ICONS", "CLOCK"
        )
        assertEquals(names.map { "com.stario.$it" }, Entry.entries.map { it.toString() })
        assertEquals("com.stario.CLOCK.face", Entry.CLOCK.toSubPreference("face"))
        assertEquals("com.stario.CLOCK.null", Entry.CLOCK.toSubPreference(null))
        assertTrue(Entry.isValid("com.stario.CLOCKface"))
        assertTrue(Entry.isValid("com.stario.CLOCK.face"))
        for (invalid in listOf(null, "", "com.stario.", "COM.STARIO.CLOCK", "xcom.stario.CLOCK")) {
            assertFalse(Entry.isValid(invalid))
        }
    }

    @Test
    fun casingKeepsJavaSeparatorAndDigitBehavior() {
        assertEquals("Hello. World/again (still). 123abc", Casing.toSentenceCase("hELLO. wORLD/AGAIN (STILL). 123ABC"))
        assertEquals("Hello.World/Foo_Bar (Baz)\tand", Casing.toTitleCase("hELLO.wORLD/foo_BAR (bAZ)\tAND"))
        assertEquals("123abc", Casing.toTitleCase("123ABC"))
        assertEquals("", Casing.toSentenceCase(""))
    }

    @Test
    fun observableSuppressesEqualUpdatesAndSharesClosedState() {
        val initial = String(charArrayOf('a'))
        val events = mutableListOf<String?>()
        val observable = ObservableObject(initial, ObservableObject.OnSet<String> { events.add(it) })
        val closed = observable.close()
        observable.updateObject(String(charArrayOf('a')))
        assertSame(initial, closed.getObject())
        assertTrue(events.isEmpty())

        closed.addListener { events.add("second:$it") }
        assertEquals(2, observable.getListenerCount())
        observable.updateObject("b")
        observable.updateObject(null)
        observable.updateObject(null)
        assertEquals(listOf("b", "second:b", null, "second:null"), events)
        assertNull(closed.getObject())

        observable.removeListeners()
        assertEquals(0, closed.getListenerCount())
        observable.updateObject("c")
        assertEquals("c", closed.getObject())
        assertEquals(4, events.size)
    }

    @Test
    fun delegateNotifiesEvenForEqualAndNullValues() {
        val events = mutableListOf<String?>()
        val delegate = ObjectDelegate<String> { events.add(it) }
        assertNull(delegate.getValue())
        delegate.setValue(null)
        delegate.setValue("same")
        delegate.setValue("same")
        assertEquals(listOf(null, "same", "same"), events)
        assertEquals("same", delegate.getValue())

        val seeded = ObjectDelegate("initial", ObjectDelegate.ObjectDelegateAction<String> { events.add(it) })
        assertEquals("initial", seeded.getValue())
        assertEquals(3, events.size)
    }

    @Test
    fun widgetJsonKeepsFieldNamesEnumAndMissingValueDefaults() {
        val widget = Widget(17, 3, WidgetSize.MEDIUM)
        assertEquals(
            JsonParser.parseString("""{"size":"MEDIUM","id":17,"position":3}"""),
            JsonParser.parseString(widget.serialize())
        )
        val restored = requireNotNull(Widget.deserialize("""{"size":"MEDIUM","id":17,"position":3}"""))
        assertEquals(17, restored.id)
        assertEquals(3, restored.position)
        assertEquals(WidgetSize.MEDIUM, restored.size)
        assertEquals(0, requireNotNull(Widget.deserialize("""{"size":"SMALL"}""")).id)
        assertNull(Widget.deserialize("""{"id":17,"position":3}"""))
        assertNull(Widget.deserialize("""{"size":"SMALL","id":-1,"position":3}"""))
    }
}
