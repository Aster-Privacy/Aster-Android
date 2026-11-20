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
import org.astermail.android.R

internal object settings_search_fixture {

    val main_dir: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }

    val res_dir: File = File(main_dir, "res")

    val settings_source_dir: File = File(main_dir, "kotlin/org/astermail/android/ui/settings")

    val string_ids: Map<String, Int> = R.string::class.java.fields
        .filter { it.type == Int::class.javaPrimitiveType }
        .associate { it.name to it.getInt(null) }

    val string_names: Map<Int, String> = string_ids.entries.associate { it.value to it.key }

    private val string_pattern = Regex("""<string\s+name="([a-z0-9_]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    fun read_strings(folder: String): Map<String, String> {
        val dir = File(res_dir, folder)
        val out = mutableMapOf<String, String>()
        dir.listFiles { file -> file.name.endsWith(".xml") }.orEmpty().sortedBy { it.name }.forEach { file ->
            string_pattern.findAll(file.readText()).forEach { match ->
                out[match.groupValues[1]] = unescape(match.groupValues[2])
            }
        }
        return out
    }

    private fun unescape(raw: String): String = raw
        .replace(Regex("<[^>]+>"), "")
        .replace("\'", "'")
        .replace("\\\"", "\"")
        .replace("\n", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .trim()

    val english: Map<String, String> by lazy { read_strings("values") }

    fun text(res: Int): String {
        val name = string_names[res] ?: error("unknown string id $res")
        return english[name] ?: error("no English value for $name")
    }

    val candidates: List<settings_search_candidate> by lazy {
        settings_search_index.map { entry ->
            settings_search_candidate(
                screen_id = entry.screen_id,
                label = text(entry.label_res),
                parent = text(entry.screen_title_res),
                keywords = settings_keyword_resources(entry).flatMap { split_settings_search_keywords(text(it)) },
                is_screen_title = entry.is_screen_title,
                is_description = is_settings_description_name(string_names.getValue(entry.label_res)),
            )
        }
    }

    val prepared: List<settings_search_prepared> by lazy { prepare_settings_search(candidates) }

    fun search(query: String): List<settings_search_scored> = rank_settings_search(query, prepared)
}
