package com.farol.launcher

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class SettingsLayoutTest {
    @Test
    fun everySettingBelongsToOneCategoryAndHomeOnlyListsCategories() {
        val folder = File("src/main/res/settings/layout")
        fun elements(name: String): List<Element> {
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File(folder, name)).getElementsByTagName("*")
            return (0 until nodes.length).map { nodes.item(it) as Element }
        }

        val options = mapOf(
            "home" to setOf("home", "pages", "theme"),
            "drawer" to setOf("hidden_apps", "icons"),
            "search" to setOf("search_engine", "search_results_container", "search_hidden_apps_container"),
            "general" to setOf("low_spec_container", "vibrations_container", "restart", "def_launcher"),
            "about" to setOf("creator_profile", "creator_avatar", "creator_github", "creator_telegram", "creator_instagram", "creator_website", "github", "app_info_action", "licenses")
        )
        val details = elements("settings_content.xml")
        options.forEach { (category, ids) ->
            val group = details.single { it.getAttribute("android:id") == "@+id/group_$category" }
            val children = group.getElementsByTagName("*")
            val found = (0 until children.length).map {
                (children.item(it) as Element).getAttribute("android:id").removePrefix("@+id/")
            }.toSet()
            assertTrue("Missing options in $category: ${ids - found}", found.containsAll(ids))
        }
        options.values.flatten().forEach { id ->
            assertEquals(1, details.count { it.getAttribute("android:id") == "@+id/$id" })
        }
        val homeIds = elements("settings_overview.xml").map { it.getAttribute("android:id") }.toSet()
        assertEquals(options.keys.map { "@+id/category_$it" }.toSet(), homeIds - setOf("", "@+id/overview", "@+id/overview_landscape"))
    }

    @Test
    fun categoryTitleAlignsExpandedAndClearsCollapsedNavigationIcon() {
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val title = parser.parse(File("src/main/res/settings/layout/settings.xml"))
            .getElementsByTagName("TextView").let { nodes ->
                (0 until nodes.length).map { nodes.item(it) as Element }
                    .single { it.getAttribute("android:id") == "@+id/title_portrait" }
            }
        assertEquals("30dp", title.getAttribute("android:layout_marginStart"))

        val source = File("src/main/java/com/farol/launcher/activities/settings/Settings.kt").readText()
        assertTrue(source.contains("30f + 42f * titleCollapseFraction"))
        assertTrue(source.contains("if (selectedCategory == -1) 30f"))
    }

    @Test
    fun overviewCardsFormOneRoundedStack() {
        val folder = File("src/main/res/settings")
        fun root(path: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(folder, path)).documentElement
        assertEquals("4dp", root("layout/settings_category_card.xml").getAttribute("android:layout_marginBottom"))
        assertEquals("76dp", root("layout/settings_category_card.xml").getAttribute("android:minHeight"))
        fun radii(position: String): Pair<String, String> {
            val corners = root("drawable/grouped_category_$position.xml")
                .getElementsByTagName("corners").item(0) as Element
            return corners.getAttribute("android:topLeftRadius") to corners.getAttribute("android:bottomLeftRadius")
        }
        assertEquals("26dp" to "8dp", radii("top"))
        assertEquals("8dp" to "8dp", radii("middle"))
        assertEquals("8dp" to "26dp", radii("bottom"))
    }

    @Test
    fun farolCardPrecedesCreatorAndAboutActions() {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/settings/layout/settings_content.xml")).documentElement
        val about = root.getElementsByTagName("*")
        val group = (0 until about.length).map { about.item(it) as Element }
            .single { it.getAttribute("android:id") == "@+id/group_about" }
        val sections = group.childNodes
        val cards = (0 until sections.length).mapNotNull { sections.item(it) as? Element }
        assertEquals(3, cards.size)
        assertEquals("@+id/about_hero", cards[0].getAttribute("android:id"))
        assertEquals("?attr/colorPrimaryContainer", cards[0].getAttribute("android:background"))
        val beacon = cards[0].getElementsByTagName("com.farol.launcher.activities.settings.FarolBeaconView").item(0) as Element
        assertEquals("@+id/about_beacon", beacon.getAttribute("android:id"))
        assertEquals("match_parent", beacon.getAttribute("android:layout_width"))
        assertEquals("172dp", beacon.getAttribute("android:layout_height"))
        assertEquals("@string/replay_beacon_light", beacon.getAttribute("android:contentDescription"))
        val heroText = cards[0].getElementsByTagName("TextView")
        assertTrue((0 until heroText.length).any { (heroText.item(it) as Element).let { text ->
            text.getAttribute("android:text") == "@string/app_name" && text.getAttribute("android:textSize") == "48sp"
        } })
        assertTrue((0 until heroText.length).any { (heroText.item(it) as Element).getAttribute("android:text") == "@string/farol_headline" })
        assertEquals("com.google.android.material.card.MaterialCardView", cards[1].tagName)
        assertEquals("?attr/colorSurfaceContainerHighest", cards[1].getAttribute("app:cardBackgroundColor"))
        assertEquals("28dp", cards[1].getAttribute("app:cardCornerRadius"))
        assertEquals("1dp", cards[1].getAttribute("app:strokeWidth"))
        assertEquals("?attr/colorOutlineVariant", cards[1].getAttribute("app:strokeColor"))
        assertEquals("0dp", cards[1].getAttribute("app:cardElevation"))
        val cardContent = cards[1].getElementsByTagName("LinearLayout").item(0) as Element
        assertEquals("20dp", cardContent.getAttribute("android:padding"))
        val actionIds = cards[2].getElementsByTagName("*").let { nodes ->
            (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("android:id") }.toSet()
        }
        assertTrue(actionIds.containsAll(setOf("@+id/github", "@+id/app_info_action", "@+id/licenses")))
    }

    @Test
    fun creatorLinksUseAccessibleCompactControls() {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/settings/layout/settings_content.xml")).documentElement
        val nodes = root.getElementsByTagName("*")
        fun element(id: String) = (0 until nodes.length).map { nodes.item(it) as Element }
            .single { it.getAttribute("android:id") == "@+id/$id" }

        val profile = element("creator_profile")
        assertEquals("@string/farol_creator_profile", profile.getAttribute("android:contentDescription"))
        assertEquals("horizontal", profile.getAttribute("android:orientation"))
        assertEquals("true", profile.getAttribute("android:clickable"))
        val avatar = element("creator_avatar")
        assertEquals("64dp", avatar.getAttribute("android:layout_width"))
        assertEquals("64dp", avatar.getAttribute("android:layout_height"))
        assertEquals("@null", avatar.getAttribute("android:contentDescription"))
        assertEquals("no", avatar.getAttribute("android:importantForAccessibility"))
        val textColumn = profile.getElementsByTagName("LinearLayout").item(0) as Element
        assertEquals("0dp", textColumn.getAttribute("android:layout_width"))
        assertEquals("1", textColumn.getAttribute("android:layout_weight"))
        assertEquals("16dp", textColumn.getAttribute("android:layout_marginStart"))
        val profileLabels = profile.getElementsByTagName("TextView")
        assertEquals(3, profileLabels.length)
        listOf("@string/farol_creator_name" to "24sp", "@string/farol_creator_handle" to "14sp", "@string/farol_creator_role" to "14sp")
            .forEachIndexed { index, (label, size) ->
                val text = profileLabels.item(index) as Element
                assertEquals("no", text.getAttribute("android:importantForAccessibility"))
                assertEquals("wrap_content", text.getAttribute("android:layout_height"))
                assertEquals(label, text.getAttribute("android:text"))
                assertEquals(size, text.getAttribute("android:textSize"))
            }
        listOf("creator_github", "creator_telegram", "creator_instagram").forEach { id ->
            val link = element(id)
            assertEquals("androidx.appcompat.widget.AppCompatImageButton", link.tagName)
            assertEquals("48dp", link.getAttribute("android:layout_width"))
            assertEquals("48dp", link.getAttribute("android:layout_height"))
            assertTrue(link.getAttribute("android:contentDescription").isNotEmpty())
            assertTrue(link.getAttribute("android:tooltipText").isNotEmpty())
        }
        val website = element("creator_website")
        assertEquals("androidx.appcompat.widget.AppCompatTextView", website.tagName)
        assertEquals("@string/creator_website", website.getAttribute("android:text"))
        assertEquals("@string/creator_website_description", website.getAttribute("android:contentDescription"))
        assertEquals("wrap_content", website.getAttribute("android:layout_width"))
        assertEquals("wrap_content", website.getAttribute("android:layout_height"))
        assertEquals("48dp", website.getAttribute("android:minHeight"))
        assertEquals("?attr/colorPrimary", website.getAttribute("android:textColor"))
        val actions = website.parentNode as Element
        assertEquals("com.google.android.flexbox.FlexboxLayout", actions.tagName)
        assertEquals("match_parent", actions.getAttribute("android:layout_width"))
        assertEquals("wrap", actions.getAttribute("app:flexWrap"))
        assertEquals("space_between", actions.getAttribute("app:justifyContent"))
        assertEquals("center", actions.getAttribute("app:alignItems"))
        val children = (0 until actions.childNodes.length).mapNotNull { actions.childNodes.item(it) as? Element }
        assertEquals(2, children.size)
        assertTrue(children[0] === website)
        assertEquals("wrap_content", children[1].getAttribute("android:layout_width"))
        assertEquals("horizontal", children[1].getAttribute("android:orientation"))
        assertTrue(children[1] === element("creator_github").parentNode)
        assertTrue(children[1] === element("creator_telegram").parentNode)
        assertTrue(children[1] === element("creator_instagram").parentNode)
    }

    @Test
    fun aboutSurfacesRemainDistinctFromEveryThemeBackground() {
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val resources = File("src/main/res")
        val card = parser.parse(File(resources, "settings/layout/settings_content.xml"))
            .getElementsByTagName("com.google.android.material.card.MaterialCardView").item(0) as Element
        val surfaces = listOf(card.getAttribute("app:cardBackgroundColor")) + listOf("about_footer_top", "grouped_category_middle", "about_footer_bottom").map {
            val fill = parser.parse(File(resources, "settings/drawable/$it.xml"))
                .getElementsByTagName("solid").item(0) as Element
            fill.getAttribute("android:color")
        }
        var checked = 0
        File(resources, "themes").walkTopDown().filter { it.extension == "xml" }.forEach { file ->
            val styles = parser.parse(file).getElementsByTagName("style")
            for (index in 0 until styles.length) {
                val items = (styles.item(index) as Element).getElementsByTagName("item")
                val colors = (0 until items.length).associate {
                    val item = items.item(it) as Element
                    item.getAttribute("name") to item.textContent
                }
                val background = colors["colorSurface"] ?: continue
                surfaces.forEach { surface ->
                    val color = colors[surface.removePrefix("?attr/")]
                    assertTrue("Invisible About surface $surface in $file", color != null && color != background)
                }
                checked++
            }
        }
        assertTrue("No themes checked", checked > 0)
    }

    @Test
    fun settingsGearColorIsOwnedByEachCaller() {
        val folder = File("src/main/res/settings")
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val card = parser.parse(File(folder, "layout/settings_category_card.xml"))
        val icon = card.getElementsByTagName("androidx.appcompat.widget.AppCompatImageView").item(0) as Element
        assertEquals("?attr/colorOnSurface", icon.getAttribute("app:tint"))
        val general = parser.parse(File(folder, "drawable/ic_settings.xml"))
        val path = general.getElementsByTagName("path").item(0) as Element
        assertEquals("?attr/colorSecondary", path.getAttribute("android:fillColor"))
    }
}
