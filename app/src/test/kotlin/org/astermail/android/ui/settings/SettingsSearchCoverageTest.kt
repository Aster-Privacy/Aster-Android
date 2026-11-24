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

package org.astermail.android.ui.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchCoverageTest {

    private val helpers = listOf(
        "detail_scaffold", "embeddable_detail_scaffold", "section_label", "settings_group",
        "settings_toggle_row", "choice_group_title", "choice_option_row", "section_header_action",
        "detail_row", "access_toggle_row", "behavior_toggle", "switch_row", "theme_option_row",
        "compose_choice_row", "security_choice_row", "badge_toggle_row", "illustrated_option_card",
        "preference_option", "billing_expander_row", "allowlist_kind_option", "devices_list_action_row",
    )

    private val text_arguments = setOf("title", "label", "subtitle", "text", "description")

    private val not_settings = setOf(
        "failed_to_load", "retry", "no_aliases", "no_aliases_subtitle", "no_results_found",
        "recently_deleted_aliases_empty", "no_custom_domains", "no_custom_domains_subtitle",
        "no_directories_yet", "no_directories_subtitle", "no_ghost_aliases", "no_ghost_aliases_subtitle",
        "alias_detail_unavailable", "delete_alias", "email_address", "allowlist_entire_domain",
        "font_preview_sample", "special_offer_entry", "special_offer_title", "delete_account_warning_body",
        "notif_blocked_title", "notif_blocked_subtitle", "member_since", "passkey_adding", "show_less",
        "security_activity_empty_filter", "smtp_token_address_label", "storage_locked_title",
        "storage_almost_full_title", "storage_locked_description", "storage_almost_full_body",
        "storage_nothing_to_clean", "fix_billing_history_empty", "billing_crypto_resume_title",
        "billing_crypto_resume_expires", "no_other_devices", "glass_mode_reduced_subtitle",
        "color_theme_default", "color_theme_show_all", "font_option_default", "font_option_system",
        "font_option_system_mono", "integrations", "connected_apps", "slack", "slack_subtitle", "calendar",
        "calendar_subtitle", "zapier", "zapier_subtitle", "webhooks", "manage_webhooks",
    )

    private val locales = listOf(
        "ar", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt-rBR", "pt", "ru", "tr", "zh-rCN",
    )

    private fun skip_string_literal(src: String, start: Int): Int {
        var k = start + 1
        while (k < src.length && src[k] != '"') {
            if (src[k] == '\\') k++
            k++
        }
        return k
    }

    private fun closing_index(src: String, open: Int): Int {
        var depth = 0
        var i = open
        while (i < src.length) {
            when (src[i]) {
                '"' -> i = skip_string_literal(src, i)
                '(', '[', '{' -> depth++
                ')', ']', '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return src.length
    }

    private fun split_arguments(body: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '"') {
                val end = skip_string_literal(body, i)
                current.append(body, i, minOf(end + 1, body.length))
                i = end + 1
                continue
            }
            if (c == '(' || c == '[' || c == '{') depth++
            if (c == ')' || c == ']' || c == '}') depth--
            if (c == ',' && depth == 0) {
                out += current.toString()
                current.setLength(0)
            } else {
                current.append(c)
            }
            i++
        }
        if (current.isNotBlank()) out += current.toString()
        return out
    }

    private fun shown_setting_strings(): Map<String, String> {
        val call = Regex("(?<![\\w.])(" + helpers.joinToString("|") + ")\\(")
        val named = Regex("^\\s*(\\w+)\\s*=(?!=)")
        val string_ref = Regex("R\\.string\\.(\\w+)")
        val declaration = Regex("fun\\s+$")
        val found = linkedMapOf<String, String>()
        settings_search_fixture.settings_source_dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val src = file.readText()
                call.findAll(src).forEach { match ->
                    val before = src.substring(maxOf(0, match.range.first - 30), match.range.first)
                    if (!declaration.containsMatchIn(before)) {
                        val open = match.range.last
                        val close = closing_index(src, open)
                        val line = src.substring(0, match.range.first).count { it == '\n' } + 1
                        split_arguments(src.substring(open + 1, close)).forEachIndexed { index, argument ->
                            val name = named.find(argument)?.groupValues?.get(1)
                            val is_text = when {
                                name != null -> name in text_arguments
                                else -> index == 0 && !argument.contains('{')
                            }
                            if (is_text) {
                                string_ref.findAll(argument).forEach { ref ->
                                    found.putIfAbsent(ref.groupValues[1], "${file.name}:$line")
                                }
                            }
                        }
                    }
                }
            }
        return found
    }

    @Test
    fun every_setting_shown_on_a_settings_screen_is_searchable() {
        val shown = shown_setting_strings()
        assertTrue("scan found too few settings strings: ${shown.size}", shown.size > 300)
        val indexed = settings_search_index
            .flatMap { listOf(it.label_res, it.screen_title_res) }
            .mapNotNull { settings_search_fixture.string_names[it] }
            .toSet()
        val missing = shown.filter { (name, _) ->
            name !in indexed &&
                name !in not_settings &&
                !settings_search_fixture.english.getValue(name).contains('%')
        }
        assertTrue("not in settings search: $missing", missing.isEmpty())
    }

    @Test
    fun every_main_settings_row_has_a_search_entry() {
        val titled = settings_search_index.filter { it.is_screen_title }.map { it.screen_id }.toSet()
        build_settings_sections(is_family = true)
            .flatMap { it.rows }
            .filter { it.id != "contact_support" }
            .forEach { row -> assertTrue(row.id, row.id in titled) }
    }

    @Test
    fun every_search_result_opens_a_real_screen() {
        val main_activity = File(settings_search_fixture.main_dir, "kotlin/org/astermail/android/MainActivity.kt").readText()
        val routes = Regex("routes\\.settings_detail\\(\"([a-z_]+)\"\\)").findAll(main_activity)
            .map { it.groupValues[1] }
            .toSet()
        settings_search_index.map { it.screen_id }.toSet().forEach { id ->
            assertTrue("no route for $id", id in routes)
        }
    }

    @Test
    fun every_entry_resolves_to_english_text() {
        settings_search_index.forEach { entry ->
            assertTrue(settings_search_fixture.text(entry.label_res).isNotBlank())
            assertTrue(settings_search_fixture.text(entry.screen_title_res).isNotBlank())
        }
    }

    @Test
    fun keywords_are_translated_for_every_locale() {
        val english = settings_search_fixture.english.filterKeys {
            it.startsWith("search_keywords_") && !it.startsWith("search_keywords_more_")
        }
        assertTrue(english.size > 40)
        locales.forEach { locale ->
            val translated = settings_search_fixture.read_strings("values-$locale")
            english.keys.forEach { name ->
                val value = translated[name]
                assertTrue("$locale is missing $name", !value.isNullOrBlank())
                assertTrue("$locale $name is empty", split_settings_search_keywords(value.orEmpty()).isNotEmpty())
            }
        }
    }
}
