package org.astermail.android.theme

import org.astermail.android.design.ColorThemeId
import org.astermail.android.ui.theme.ThemeCategory
import org.astermail.android.ui.theme.theme_manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeManifestParseTest {

    private fun item(
        slug: String = "southern_ring",
        category: String = "space",
        accent: String = "orange",
        tint: String = "FF221617",
        credit: String = "NASA, public domain",
    ) = """{"slug":"$slug","category":"$category","accent":"$accent","tint":"$tint","credit":"$credit"}"""

    private fun manifest(vararg items: String) = """{"version":1,"items":[${items.joinToString(",")}]}"""

    @Test
    fun parses_a_well_formed_entry() {
        val parsed = theme_manifest.parse(manifest(item()))
        assertEquals(1, parsed.size)
        val entry = parsed.first()
        assertEquals("southern_ring", entry.id)
        assertEquals(ThemeCategory.space, entry.category)
        assertEquals(ColorThemeId.orange, entry.color_theme)
        assertEquals("NASA, public domain", entry.credit)
        assertEquals(0xFF221617.toInt(), entry.tint.value.toLong().ushr(32).toInt())
    }

    @Test
    fun drops_entries_the_app_cannot_render() {
        val parsed = theme_manifest.parse(
            manifest(
                item(slug = "good"),
                item(slug = "bad slug"),
                item(slug = "unknown_category", category = "underwater_basket"),
                item(slug = "reserved_category", category = "yours"),
                item(slug = "no_credit", credit = ""),
            ),
        )
        assertEquals(listOf("good"), parsed.map { it.id })
    }

    @Test
    fun keeps_the_first_of_a_duplicated_slug() {
        val parsed = theme_manifest.parse(
            manifest(item(slug = "dupe", credit = "first"), item(slug = "dupe", credit = "second")),
        )
        assertEquals(1, parsed.size)
        assertEquals("first", parsed.first().credit)
    }

    @Test
    fun falls_back_on_an_unusable_accent_or_tint() {
        val parsed = theme_manifest.parse(manifest(item(accent = "chartreuse", tint = "nonsense")))
        assertEquals(ColorThemeId.default, parsed.first().color_theme)
        assertTrue(parsed.first().tint.alpha > 0.99f)
    }

    @Test
    fun preserves_manifest_order_so_covers_stay_deliberate() {
        val parsed = theme_manifest.parse(
            manifest(item(slug = "cover"), item(slug = "second"), item(slug = "third")),
        )
        assertEquals(listOf("cover", "second", "third"), parsed.map { it.id })
    }

    @Test
    fun returns_nothing_when_items_are_absent() {
        assertTrue(theme_manifest.parse("""{"version":1}""").isEmpty())
    }
}
