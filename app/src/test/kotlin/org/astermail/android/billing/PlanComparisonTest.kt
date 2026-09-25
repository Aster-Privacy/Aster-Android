//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.billing

import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanComparisonTest {
    private fun asset(locale: String): String =
        File("src/main/assets/plan_comparison/$locale.json").readText()

    private fun en(): plan_comparison_feed = parse_plan_comparison(asset("en"))!!

    @Test
    fun bundled_english_feed_matches_web_matrix() {
        val feed = en()
        assertEquals("en", feed.locale)
        assertEquals(listOf("free", "star", "nova", "supernova", "duo", "family"), feed.plans.map { it.code })
        assertEquals(9, feed.groups.size)
        assertEquals(80, feed.row_count)
        assertEquals("10 GB", feed.row("storage")?.value_for("free")?.text)
        assertEquals(plan_comparison_value(false, null), feed.row("external_accounts")?.value_for("free"))
        assertEquals(plan_comparison_value(true, null), feed.row("key_rotation")?.value_for("nova"))
    }

    @Test
    fun every_bundled_locale_parses_with_the_same_rows() {
        val en_ids = en().groups.flatMap { group -> group.rows.map { it.id } }
        for (locale in plan_comparison_bundled_locales) {
            val feed = parse_plan_comparison(asset(locale))
            assertNotNull(locale, feed)
            assertEquals(locale, feed!!.locale)
            assertEquals(locale, en_ids, feed.groups.flatMap { group -> group.rows.map { it.id } })
        }
    }

    @Test
    fun family_group_is_hidden_for_individual_plans() {
        val feed = en()
        val individual = feed.groups_for(listOf("free", "star", "nova", "supernova"), include_family = false)
        assertFalse(individual.any { it.id == PLAN_COMPARISON_FAMILY_GROUP })
        val family = feed.groups_for(listOf("free", "duo", "family"), include_family = true)
        assertTrue(family.any { it.id == PLAN_COMPARISON_FAMILY_GROUP })
    }

    @Test
    fun rejects_wrong_schema_and_unknown_locale() {
        assertNull(parse_plan_comparison(asset("en").replace("\"schema\":1", "\"schema\":2")))
        assertNull(parse_plan_comparison(asset("en").replace("\"locale\":\"en\"", "\"locale\":\"xx\"")))
        assertNull(parse_plan_comparison("not json"))
        assertNull(parse_plan_comparison("{}"))
    }

    @Test
    fun drops_unsafe_ids_and_cleans_text() {
        val raw = """
            {"schema":1,"locale":"en",
             "plans":[{"code":"free","name":"Free","family":false},{"code":"Bad Code","name":"x","family":false}],
             "groups":[{"id":"general","title":"General","rows":[
               {"id":"storage","label":"Stor\u0007age","tip":null,"values":{"free":"10 GB","Bad Code":"1"}},
               {"id":"../etc","label":"Evil","tip":null,"values":{"free":true}},
               {"id":"flag","label":"Flag","tip":"Tip","values":{"free":"","other":true}},
               {"id":"num","label":"Num","tip":null,"values":{"free":5}}
             ]}]}
        """.trimIndent()
        val feed = parse_plan_comparison(raw)!!
        assertEquals(listOf("free"), feed.plans.map { it.code })
        val rows = feed.groups.single().rows
        assertEquals(listOf("storage", "flag", "num"), rows.map { it.id })
        assertEquals("Storage", rows[0].label)
        assertEquals(setOf("free"), rows[0].values.keys)
        assertEquals(plan_comparison_value(false, null), rows[1].value_for("free"))
        assertEquals("Tip", rows[1].tip)
        assertEquals(plan_comparison_value(false, null), rows[2].value_for("free"))
    }

    @Test
    fun caps_long_text() {
        val long = "a".repeat(1000)
        val raw = """{"schema":1,"locale":"en","plans":[{"code":"free","name":"$long"}],"groups":[{"id":"g","title":"G","rows":[{"id":"r","label":"L","tip":null,"values":{"free":true}}]}]}"""
        assertEquals(400, parse_plan_comparison(raw)!!.plans.single().name.length)
    }

    @Test
    fun maps_device_locales_to_web_locales() {
        assertEquals("en", plan_comparison_locale(Locale.US))
        assertEquals("de", plan_comparison_locale(Locale.GERMANY))
        assertEquals("he", plan_comparison_locale(Locale.forLanguageTag("iw-IL")))
        assertEquals("id", plan_comparison_locale(Locale.forLanguageTag("in-ID")))
        assertEquals("zh-TW", plan_comparison_locale(Locale.TRADITIONAL_CHINESE))
        assertEquals("zh-TW", plan_comparison_locale(Locale.forLanguageTag("zh-HK")))
        assertEquals("zh-TW", plan_comparison_locale(Locale.Builder().setLanguage("zh").setScript("Hant").build()))
        assertEquals("zh-CN", plan_comparison_locale(Locale.SIMPLIFIED_CHINESE))
        assertEquals("es-419", plan_comparison_locale(Locale.forLanguageTag("es-MX")))
        assertEquals("es", plan_comparison_locale(Locale.forLanguageTag("es-ES")))
        assertEquals("en", plan_comparison_locale(Locale.forLanguageTag("it-IT")))
        assertEquals("en", plan_comparison_locale(Locale.forLanguageTag("pl")))
    }
}
