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

package org.astermail.android.ui.common

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardProviderGuardTest {

    private val read_only_selection_providers = setOf("detail_context_chips.kt")

    @Test
    fun text_fields_keep_the_platform_clipboard() {
        val offenders = File("src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("LocalClipboard provides") }
            .map { it.name }
            .filterNot { it in read_only_selection_providers }
            .sorted()
            .toList()
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun read_only_selection_providers_hold_no_text_field() {
        File("src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.name in read_only_selection_providers }
            .forEach { file ->
                val body = file.readText()
                assertEquals(file.name, false, Regex("""\b(Basic)?TextField\(""").containsMatchIn(body))
            }
    }
}
