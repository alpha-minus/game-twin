package com.twinlore.app

import android.util.Xml
import com.twinlore.app.data.AppIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * The icon pack's appfilter against [AppIcon].
 *
 * assets/appfilter.xml is read by OTHER apps, so it is a published contract with
 * no compile-time link to the Kotlin it mirrors. Add an edition and forget the
 * appfilter and that edition silently never themes; rename an alias and every
 * launcher silently falls back to the default icon. Neither failure is visible
 * from inside this app, which is why it is pinned here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppFilterCoverageTest {

    private val context = RuntimeEnvironment.getApplication()

    private data class Entry(val component: String, val drawable: String)

    /**
     * Pull-parses appfilter.xml for real, the same way a launcher's
     * XmlPullParser would. A malformed document (unclosed tag, bad entity, an
     * item hidden inside an XML comment) throws here instead of silently
     * satisfying a regex, since every launcher's parser would abort on it too.
     */
    private fun appFilterEntries(): List<Entry> {
        val entries = mutableListOf<Entry>()
        context.assets.open("appfilter.xml").use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, null)
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (component != null && drawable != null) {
                        entries.add(Entry(component, drawable))
                    }
                }
                eventType = parser.next()
            }
            // KXmlParser (what Xml.newPullParser() actually returns) does not
            // throw at EOF just because a tag was left open: it reports
            // END_DOCUMENT regardless, and only parser.depth reveals the
            // dangling element. Every real launcher's XmlPullParser aborts on
            // that malformed document, so this must too.
            check(parser.depth == 0) {
                "appfilter.xml is not well-formed: reached end of document with an unclosed tag"
            }
        }
        return entries
    }

    @Test
    fun every_app_icon_has_exactly_one_appfilter_item() {
        val byComponent = appFilterEntries().groupBy { it.component }
        AppIcon.entries.forEach { icon ->
            val expected = "ComponentInfo{${context.packageName}/${icon.aliasClass}}"
            val matches = byComponent[expected].orEmpty()
            assertEquals("appfilter items for ${icon.id} ($expected)", 1, matches.size)
            assertEquals(
                "appfilter drawable for ${icon.id} ($expected)",
                "edition_${icon.id}",
                matches.single().drawable,
            )
        }
    }

    @Test
    fun the_appfilter_has_no_items_beyond_the_app_icons() {
        val known = AppIcon.entries
            .map { "ComponentInfo{${context.packageName}/${it.aliasClass}}" }
            .toSet()
        val strays = appFilterEntries().map { it.component }.filterNot { it in known }
        assertTrue("appfilter names components that are not AppIcon aliases: $strays", strays.isEmpty())
    }

    @Test
    fun every_referenced_drawable_resolves() {
        // A typo here is invisible until a launcher silently falls back.
        appFilterEntries().forEach { entry ->
            val id = context.resources.getIdentifier(entry.drawable, "drawable", context.packageName)
            assertTrue("drawable '${entry.drawable}' does not resolve", id != 0)
        }
    }

    @Test
    fun the_parse_actually_found_items() {
        // Guards the parser itself: a malformed appfilter that parses to zero
        // entries would make every other test in this class vacuously pass.
        assertEquals(AppIcon.entries.size, appFilterEntries().size)
    }
}
