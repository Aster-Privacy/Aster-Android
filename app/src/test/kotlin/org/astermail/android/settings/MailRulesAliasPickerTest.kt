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
import org.astermail.android.api.mail_rules.MatchMode
import org.astermail.android.api.mail_rules.TextOp
import org.astermail.android.ui.settings.mail_rules.condition_offers_alias_picker
import org.astermail.android.ui.settings.mail_rules.insert_condition_values
import org.astermail.android.ui.settings.mail_rules.normalize_address_values
import org.astermail.android.ui.settings.mail_rules.pickers.alias_option
import org.astermail.android.ui.settings.mail_rules.pickers.filter_alias_options
import org.astermail.android.ui.settings.mail_rules.set_condition_value
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailRulesAliasPickerTest {

    private fun to(value: String, op: AddressOp = AddressOp.IS) = Condition.To(op = op, value = value)

    private fun values_of(conditions: List<Condition>) = conditions.map {
        when (it) {
            is Condition.To -> it.value
            is Condition.From -> it.value
            is Condition.Cc -> it.value
            is Condition.Subject -> it.value
            else -> ""
        }
    }

    @Test
    fun `blank and repeated values are dropped while order is kept`() {
        val cleaned = normalize_address_values(
            listOf(" shop@astermail.org ", "", "   ", "SHOP@astermail.org", "news@astermail.org"),
        )

        assertEquals(listOf("shop@astermail.org", "news@astermail.org"), cleaned)
    }

    @Test
    fun `picking several aliases expands into one condition each`() {
        val result = insert_condition_values(
            conditions = listOf(to("")),
            index = 0,
            template = to(""),
            values = listOf("a@astermail.org", "b@astermail.org", "c@astermail.org"),
            case = false,
            match_mode = MatchMode.ALL,
        )

        assertEquals(3, result.inserted)
        assertEquals(0, result.skipped_duplicates)
        assertEquals(
            listOf("a@astermail.org", "b@astermail.org", "c@astermail.org"),
            values_of(result.conditions),
        )
    }

    @Test
    fun `expanding several aliases switches an all rule to any`() {
        val result = insert_condition_values(
            conditions = listOf(to("")),
            index = 0,
            template = to(""),
            values = listOf("a@astermail.org", "b@astermail.org"),
            case = false,
            match_mode = MatchMode.ALL,
        )

        assertEquals(MatchMode.ANY, result.match_mode)
    }

    @Test
    fun `a single alias leaves the match mode alone`() {
        val result = insert_condition_values(
            conditions = listOf(to("")),
            index = 0,
            template = to(""),
            values = listOf("a@astermail.org"),
            case = false,
            match_mode = MatchMode.ALL,
        )

        assertEquals(MatchMode.ALL, result.match_mode)
        assertEquals(listOf("a@astermail.org"), values_of(result.conditions))
    }

    @Test
    fun `an existing condition on another field blocks the switch to any`() {
        val existing = listOf(
            Condition.Subject(op = TextOp.CONTAINS, value = "invoice"),
            to(""),
        )
        val result = insert_condition_values(
            conditions = existing,
            index = 1,
            template = to(""),
            values = listOf("a@astermail.org", "b@astermail.org"),
            case = false,
            match_mode = MatchMode.ALL,
        )

        assertEquals(MatchMode.ALL, result.match_mode)
        assertEquals(3, result.conditions.size)
    }

    @Test
    fun `negated operators never switch to any`() {
        val result = insert_condition_values(
            conditions = listOf(to("", AddressOp.IS_NOT)),
            index = 0,
            template = to("", AddressOp.IS_NOT),
            values = listOf("a@astermail.org", "b@astermail.org"),
            case = false,
            match_mode = MatchMode.ALL,
        )

        assertEquals(MatchMode.ALL, result.match_mode)
        assertEquals(2, result.inserted)
    }

    @Test
    fun `an any rule stays on any`() {
        val result = insert_condition_values(
            conditions = listOf(to("")),
            index = 0,
            template = to(""),
            values = listOf("a@astermail.org", "b@astermail.org"),
            case = false,
            match_mode = MatchMode.ANY,
        )

        assertEquals(MatchMode.ANY, result.match_mode)
    }

    @Test
    fun `aliases already in the rule are skipped instead of duplicated`() {
        val result = insert_condition_values(
            conditions = listOf(to("a@astermail.org"), to("")),
            index = 1,
            template = to(""),
            values = listOf("A@astermail.org", "b@astermail.org"),
            case = false,
            match_mode = MatchMode.ANY,
        )

        assertEquals(1, result.inserted)
        assertEquals(1, result.skipped_duplicates)
        assertEquals(listOf("a@astermail.org", "b@astermail.org"), values_of(result.conditions))
    }

    @Test
    fun `a duplicate typed into an incomplete condition removes that condition`() {
        val result = insert_condition_values(
            conditions = listOf(to("a@astermail.org"), to("")),
            index = 1,
            template = to(""),
            values = listOf("a@astermail.org"),
            case = false,
            match_mode = MatchMode.ANY,
        )

        assertEquals(0, result.inserted)
        assertEquals(1, result.skipped_duplicates)
        assertEquals(listOf("a@astermail.org"), values_of(result.conditions))
    }

    @Test
    fun `a duplicate typed into a saved condition leaves it untouched`() {
        val result = insert_condition_values(
            conditions = listOf(to("a@astermail.org"), to("b@astermail.org")),
            index = 1,
            template = to("b@astermail.org"),
            values = listOf("a@astermail.org"),
            case = false,
            match_mode = MatchMode.ANY,
        )

        assertEquals(0, result.inserted)
        assertEquals(1, result.skipped_duplicates)
        assertEquals(listOf("a@astermail.org", "b@astermail.org"), values_of(result.conditions))
    }

    @Test
    fun `clearing the value blanks the condition rather than removing it`() {
        val result = insert_condition_values(
            conditions = listOf(to("a@astermail.org")),
            index = 0,
            template = to("a@astermail.org"),
            values = listOf("   "),
            case = false,
            match_mode = MatchMode.ALL,
        )

        assertEquals(0, result.inserted)
        assertEquals(listOf(""), values_of(result.conditions))
    }

    @Test
    fun `editing a condition in place keeps the surrounding order`() {
        val result = insert_condition_values(
            conditions = listOf(to("a@astermail.org"), to("b@astermail.org"), to("c@astermail.org")),
            index = 1,
            template = to("b@astermail.org"),
            values = listOf("z@astermail.org"),
            case = false,
            match_mode = MatchMode.ANY,
        )

        assertEquals(
            listOf("a@astermail.org", "z@astermail.org", "c@astermail.org"),
            values_of(result.conditions),
        )
    }

    @Test
    fun `case sensitivity is carried onto every inserted condition`() {
        val result = insert_condition_values(
            conditions = listOf(to("")),
            index = 0,
            template = to(""),
            values = listOf("a@astermail.org", "b@astermail.org"),
            case = true,
            match_mode = MatchMode.ANY,
        )

        assertTrue(result.conditions.all { (it as Condition.To).case_sensitive == true })
    }

    @Test
    fun `the operator and field of the edited condition are preserved`() {
        val result = insert_condition_values(
            conditions = listOf(Condition.Cc(op = AddressOp.CONTAINS, value = "")),
            index = 0,
            template = Condition.Cc(op = AddressOp.CONTAINS, value = ""),
            values = listOf("a@astermail.org", "b@astermail.org"),
            case = false,
            match_mode = MatchMode.ANY,
        )

        assertTrue(result.conditions.all { it is Condition.Cc && it.op == AddressOp.CONTAINS })
    }

    @Test
    fun `set_condition_value updates value and case together`() {
        val updated = set_condition_value(to(""), "a@astermail.org", true)

        assertEquals("a@astermail.org", (updated as Condition.To).value)
        assertTrue(updated.case_sensitive == true)
    }

    @Test
    fun `the alias picker is offered only for address style operators`() {
        assertTrue(condition_offers_alias_picker(to("", AddressOp.IS)))
        assertTrue(condition_offers_alias_picker(to("", AddressOp.IS_NOT)))
        assertTrue(condition_offers_alias_picker(to("", AddressOp.CONTAINS)))
        assertFalse(condition_offers_alias_picker(to("", AddressOp.MATCHES_DOMAIN)))
        assertFalse(condition_offers_alias_picker(to("", AddressOp.MATCHES_REGEX)))
        assertFalse(condition_offers_alias_picker(Condition.Subject(op = TextOp.IS, value = "")))
    }

    @Test
    fun `alias search matches on address and on display name`() {
        val options = listOf(
            alias_option(address = "shop@astermail.org", display_name = "Online orders"),
            alias_option(address = "news@astermail.org", display_name = null),
        )

        assertEquals(2, filter_alias_options(options, "  ").size)
        assertEquals(listOf("shop@astermail.org"), filter_alias_options(options, "SHOP").map { it.address })
        assertEquals(listOf("shop@astermail.org"), filter_alias_options(options, "orders").map { it.address })
        assertEquals(listOf("news@astermail.org"), filter_alias_options(options, "new").map { it.address })
        assertTrue(filter_alias_options(options, "nothing").isEmpty())
    }
}
