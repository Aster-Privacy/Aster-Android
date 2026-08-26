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

package org.astermail.android.templates

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateLoadOutcomeTest {

    private fun sample(id: String) = DecryptedTemplate(
        id = id,
        name = "name_$id",
        category = "general",
        content = "body",
        sort_order = 0,
    )

    @Test
    fun `undecryptable rows are counted rather than silently dropped`() {
        val outcome = template_load_outcome(listOf(sample("a"), null, sample("b"), null))

        assertEquals(2, outcome.items.size)
        assertEquals(2, outcome.undecryptable_count)
    }

    @Test
    fun `all rows undecryptable reports a non empty count with no items`() {
        val outcome = template_load_outcome(listOf(null, null, null))

        assertEquals(0, outcome.items.size)
        assertEquals(3, outcome.undecryptable_count)
    }

    @Test
    fun `fully decryptable list reports no failures`() {
        val outcome = template_load_outcome(listOf(sample("a"), sample("b")))

        assertEquals(2, outcome.items.size)
        assertEquals(0, outcome.undecryptable_count)
    }
}
