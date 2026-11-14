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
package org.astermail.android.mail

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimaryCategoryNameTest {

    private fun resource_root(): File {
        var candidate = File("src/main/res")
        if (!candidate.isDirectory) {
            candidate = File("app/src/main/res")
        }
        return candidate
    }

    private fun strings_in(directory: File): Map<String, String> {
        val pattern = Regex("<string name=\"([^\"]+)\"[^>]*>([^<]*)</string>")
        val out = mutableMapOf<String, String>()
        val files = directory.listFiles { file -> file.extension == "xml" } ?: return out
        for (file in files) {
            for (match in pattern.findAll(file.readText())) {
                out[match.groupValues[1]] = match.groupValues[2].trim()
            }
        }
        return out
    }

    @Test
    fun primary_tab_is_named_apart_from_the_inbox_folder() {
        val root = resource_root()
        assertTrue("resource root not found: ${root.absolutePath}", root.isDirectory)

        val base = strings_in(File(root, "values"))
        val directories = listOf(File(root, "values")) + (root.listFiles { file ->
            file.isDirectory && file.name.startsWith("values-") && file.name != "values-night"
        } ?: emptyArray()).sortedBy { it.name }

        val clashes = mutableListOf<String>()
        for (directory in directories) {
            val strings = base + strings_in(directory)
            val tab = strings["rules_category_primary"]?.lowercase() ?: continue
            val folders = listOf("folder_inbox", "folder_all_mail", "all_mail")
                .mapNotNull { strings[it]?.lowercase() }
            if (tab.isEmpty() || tab in folders) {
                clashes.add("${directory.name}: $tab")
            }
        }

        assertEquals(emptyList<String>(), clashes)
    }
}
