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

package org.astermail.android.mail

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionRestrictionStringsTest {

    @Test
    fun every_restriction_resolves_to_a_non_blank_message() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val seen = mutableSetOf<String>()

        ReactionRestriction.values().forEach { restriction ->
            val message = context.getString(reaction_restriction_string(restriction))
            assertTrue(restriction.name, message.isNotBlank())
            assertTrue(restriction.name, seen.add(message))
        }
    }
}
