//
// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.util

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocaleFormatArgsTest {

    private val string_entry = Regex("<string\\s+name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
    private val plurals_entry = Regex("<plurals\\s+name=\"([^\"]+)\"[^>]*>(.*?)</plurals>", RegexOption.DOT_MATCHES_ALL)
    private val plural_item = Regex("<item\\s+quantity=\"([a-z]+)\"\\s*>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
    private val format_arg = Regex("%(?:(\\d+)\\$)?[-#+0,(]*\\d*(?:\\.\\d+)?([sdfxXc])")

    private data class resources(
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>,
    )

    private fun resource_root(): File {
        var candidate = File("src/main/res")
        if (!candidate.isDirectory) {
            candidate = File("app/src/main/res")
        }
        return candidate
    }

    private fun load(directory: File): resources {
        val strings = mutableMapOf<String, String>()
        val plurals = mutableMapOf<String, Map<String, String>>()
        val files = directory.listFiles { file -> file.extension == "xml" } ?: emptyArray()
        for (file in files) {
            val text = file.readText()
            for (match in string_entry.findAll(text)) {
                strings[match.groupValues[1]] = match.groupValues[2]
            }
            for (match in plurals_entry.findAll(text)) {
                plurals[match.groupValues[1]] = plural_item.findAll(match.groupValues[2])
                    .associate { it.groupValues[1] to it.groupValues[2] }
            }
        }
        return resources(strings, plurals)
    }

    private fun args_of(value: String): Map<Int, Char> {
        val out = mutableMapOf<Int, Char>()
        var sequential = 0
        for (match in format_arg.findAll(value.replace("%%", ""))) {
            val position = match.groupValues[1].toIntOrNull() ?: ++sequential
            out[position] = match.groupValues[2].single()
        }
        return out
    }

    private fun locales(root: File): List<File> =
        (root.listFiles { file ->
            file.isDirectory && file.name.startsWith("values-") && file.name != "values-night"
        } ?: emptyArray()).sortedBy { it.name }

    @Test
    fun every_translated_string_keeps_the_base_format_args() {
        val root = resource_root()
        assertTrue("resource root not found: ${root.absolutePath}", root.isDirectory)
        val base = load(File(root, "values"))
        assertTrue("base strings not found", base.strings.size > 100)

        val problems = mutableListOf<String>()
        for (locale in locales(root)) {
            val translated = load(locale)
            for ((name, value) in translated.strings) {
                val expected = base.strings[name] ?: continue
                val want = args_of(expected)
                val got = args_of(value)
                if (want != got) {
                    problems.add("${locale.name}/$name: expected $want, found $got")
                }
            }
        }

        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun every_translated_plural_uses_only_base_format_args() {
        val root = resource_root()
        val base = load(File(root, "values"))
        assertTrue("base plurals not found", base.plurals.isNotEmpty())

        val problems = mutableListOf<String>()
        for (locale in locales(root)) {
            val translated = load(locale)
            for ((name, items) in translated.plurals) {
                val expected_items = base.plurals[name] ?: continue
                val want = args_of(expected_items["other"] ?: continue)
                if ("other" !in items) {
                    problems.add("${locale.name}/$name: missing the other quantity")
                }
                for ((quantity, value) in items) {
                    val got = args_of(value)
                    val stray = got.filter { (position, conversion) -> want[position] != conversion }
                    if (stray.isNotEmpty()) {
                        problems.add("${locale.name}/$name[$quantity]: expected args from $want, found $got")
                    }
                    if (quantity == "other" && got != want) {
                        problems.add("${locale.name}/$name[other]: expected $want, found $got")
                    }
                }
            }
        }

        assertEquals(emptyList<String>(), problems)
    }
}
