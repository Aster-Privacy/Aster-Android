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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipientLimitsTest {

    private fun addresses(count: Int): List<String> =
        (1..count).map { "person$it@example.com" }

    @Test
    fun `a full field is allowed`() {
        assertNull(
            recipient_limit_violation(addresses(MAX_RECIPIENTS_PER_FIELD), emptyList(), emptyList()),
        )
    }

    @Test
    fun `one address over the field cap is a field violation`() {
        assertEquals(
            RecipientLimitViolation.FIELD,
            recipient_limit_violation(
                addresses(MAX_RECIPIENTS_PER_FIELD + 1),
                emptyList(),
                emptyList(),
            ),
        )
    }

    @Test
    fun `an over-full cc field is a field violation`() {
        assertEquals(
            RecipientLimitViolation.FIELD,
            recipient_limit_violation(
                emptyList(),
                addresses(MAX_RECIPIENTS_PER_FIELD + 1),
                emptyList(),
            ),
        )
    }

    @Test
    fun `an over-full bcc field is a field violation`() {
        assertEquals(
            RecipientLimitViolation.FIELD,
            recipient_limit_violation(
                emptyList(),
                emptyList(),
                addresses(MAX_RECIPIENTS_PER_FIELD + 1),
            ),
        )
    }

    @Test
    fun `fields within their caps that exceed the message cap are a total violation`() {
        assertEquals(
            RecipientLimitViolation.TOTAL,
            recipient_limit_violation(
                addresses(MAX_RECIPIENTS_PER_FIELD),
                addresses(MAX_RECIPIENTS_PER_FIELD),
                addresses(1),
            ),
        )
    }

    @Test
    fun `exactly the message cap is allowed`() {
        assertNull(
            recipient_limit_violation(
                addresses(MAX_RECIPIENTS_PER_FIELD),
                addresses(MAX_RECIPIENTS_PER_FIELD),
                emptyList(),
            ),
        )
    }

    @Test
    fun `an empty message has no violation`() {
        assertNull(recipient_limit_violation(emptyList(), emptyList(), emptyList()))
    }
}
