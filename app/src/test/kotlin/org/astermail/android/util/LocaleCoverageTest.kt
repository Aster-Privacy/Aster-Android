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

class LocaleCoverageTest {

    private val resource_name = Regex("<(?:string|plurals) name=\"([^\"]+)\"")

    private fun resource_root(): File {
        var candidate = File("src/main/res")
        if (!candidate.isDirectory) {
            candidate = File("app/src/main/res")
        }
        return candidate
    }

    private fun keys_in(directory: File): Set<String> {
        val out = mutableSetOf<String>()
        val files = directory.listFiles { file -> file.extension == "xml" } ?: return out
        for (file in files) {
            for (match in resource_name.findAll(file.readText())) {
                out.add(match.groupValues[1])
            }
        }
        return out
    }

    @Test
    fun every_locale_carries_every_base_string() {
        val root = resource_root()
        assertTrue("resource root not found: ${root.absolutePath}", root.isDirectory)

        val base = keys_in(File(root, "values"))
        assertTrue("base strings not found", base.size > 100)

        val locales = root.listFiles { file ->
            file.isDirectory && file.name.startsWith("values-") && file.name != "values-night"
        } ?: emptyArray()
        assertTrue("no locale directories found", locales.isNotEmpty())

        val gaps = mutableMapOf<String, List<String>>()
        for (locale in locales.sortedBy { it.name }) {
            val missing = (base - keys_in(locale)).sorted()
            if (missing.isNotEmpty()) {
                gaps[locale.name] = missing
            }
        }

        assertEquals(emptyMap<String, List<String>>(), gaps)
    }
}
