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

import org.astermail.android.api.mail_rules.MailRule
import org.astermail.android.ui.settings.mail_rules.rule_summary_line
import org.astermail.android.ui.settings.mail_rules.sort_rules_for_display
import org.junit.Assert.assertEquals
import org.junit.Test

class MailRulesSummaryLineTest {

    private fun summary(
        conditions: List<String>,
        actions: List<String>,
        joiner: String = "and",
        limit: Int = 2,
    ) = rule_summary_line(
        when_label = "When",
        then_label = "Then",
        joiner = joiner,
        conditions = conditions,
        actions = actions,
        limit = limit,
    )

    @Test
    fun `one condition and one action read as a single line`() {
        assertEquals(
            "When From is ada@example.com · Then Move to Work",
            summary(listOf("From is ada@example.com"), listOf("Move to Work")),
        )
    }

    @Test
    fun `conditions are joined with the match word`() {
        assertEquals(
            "When From is ada or Subject contains report",
            summary(
                conditions = listOf("From is ada", "Subject contains report"),
                actions = emptyList(),
                joiner = "or",
            ),
        )
    }

    @Test
    fun `extra segments collapse into a count`() {
        assertEquals(
            "When a and b +2 · Then x, y +1",
            summary(listOf("a", "b", "c", "d"), listOf("x", "y", "z")),
        )
    }

    @Test
    fun `blank segments are dropped`() {
        assertEquals("Then Star", summary(listOf("  ", ""), listOf(" Star ")))
    }

    @Test
    fun `no segments produce an empty line`() {
        assertEquals("", summary(emptyList(), emptyList()))
    }

    @Test
    fun `a limit below one still shows one segment`() {
        assertEquals("When a +1", summary(listOf("a", "b"), emptyList(), limit = 0))
    }

    @Test
    fun `enabled rules sort first and then by name`() {
        val rules = listOf(
            MailRule(id = "1", name = "zebra", enabled = true),
            MailRule(id = "2", name = "Apple", enabled = false),
            MailRule(id = "3", name = "beta", enabled = true),
        )
        assertEquals(
            listOf("3", "1", "2"),
            sort_rules_for_display(rules).map { it.id },
        )
    }
}
