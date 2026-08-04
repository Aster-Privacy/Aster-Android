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

package org.astermail.android.settings

import org.astermail.android.api.mail_rules.AddressOp
import org.astermail.android.api.mail_rules.Condition
import org.astermail.android.api.mail_rules.TextOp
import org.astermail.android.ui.settings.mail_rules.condition_is_address_field
import org.astermail.android.ui.settings.mail_rules.duplicate_condition_indices
import org.astermail.android.ui.settings.mail_rules.duplicates_condition_at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailRulesDuplicateConditionTest {

    private fun to(value: String, op: AddressOp = AddressOp.IS) = Condition.To(op = op, value = value)

    @Test
    fun `same address twice on the same field is a duplicate`() {
        val existing = listOf(to("shop@astermail.org"))
        assertTrue(duplicates_condition_at(existing + to(""), 1, to("shop@astermail.org")))
    }

    @Test
    fun `case and whitespace differences still count as duplicates`() {
        val existing = listOf(to("Shop@Astermail.org"))
        assertTrue(duplicates_condition_at(existing + to(""), 1, to("  shop@astermail.org ")))
    }

    @Test
    fun `different address is not a duplicate`() {
        val existing = listOf(to("shop@astermail.org"))
        assertFalse(duplicates_condition_at(existing + to(""), 1, to("news@astermail.org")))
    }

    @Test
    fun `different operator on the same address is not a duplicate`() {
        val existing = listOf(to("shop@astermail.org", AddressOp.IS))
        assertFalse(
            duplicates_condition_at(existing + to(""), 1, to("shop@astermail.org", AddressOp.CONTAINS)),
        )
    }

    @Test
    fun `different field with the same address is not a duplicate`() {
        val existing = listOf(to("shop@astermail.org"))
        val candidate = Condition.From(op = AddressOp.IS, value = "shop@astermail.org")
        assertFalse(duplicates_condition_at(existing + candidate, 1, candidate))
    }

    @Test
    fun `editing a condition back to its own value is not a duplicate`() {
        val existing = listOf(to("shop@astermail.org"), to("news@astermail.org"))
        assertFalse(duplicates_condition_at(existing, 0, to("shop@astermail.org")))
    }

    @Test
    fun `blank values are never duplicates`() {
        val existing = listOf(to(""), to(""))
        assertFalse(duplicates_condition_at(existing, 1, to("   ")))
        assertTrue(duplicate_condition_indices(existing).isEmpty())
    }

    @Test
    fun `header conditions key on name and value`() {
        val a = Condition.Header(name = "X-Mailer", op = TextOp.IS, value = "acme")
        val b = Condition.Header(name = "x-mailer", op = TextOp.IS, value = "ACME")
        val c = Condition.Header(name = "X-Other", op = TextOp.IS, value = "acme")
        assertTrue(duplicates_condition_at(listOf(a, b), 1, b))
        assertFalse(duplicates_condition_at(listOf(a, c), 1, c))
    }

    @Test
    fun `duplicate indices report every repeat after the first`() {
        val conditions = listOf(
            to("shop@astermail.org"),
            to("news@astermail.org"),
            to("shop@astermail.org"),
            to("SHOP@astermail.org"),
        )
        assertEquals(setOf(2, 3), duplicate_condition_indices(conditions))
    }

    @Test
    fun `address fields are recognised for the alias specific message`() {
        assertTrue(condition_is_address_field(to("shop@astermail.org")))
        assertFalse(
            condition_is_address_field(Condition.Subject(op = TextOp.CONTAINS, value = "invoice")),
        )
    }
}
