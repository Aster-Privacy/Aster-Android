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

import org.astermail.android.R
import org.astermail.android.api.mail_rules.AddressOp
import org.astermail.android.api.mail_rules.Condition
import org.astermail.android.api.mail_rules.TextOp
import org.astermail.android.ui.settings.mail_rules.condition_regex_error
import org.astermail.android.ui.settings.mail_rules.is_condition_complete
import org.astermail.android.ui.settings.mail_rules.regex_error_res
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MailRulesRegexValidationTest {

    @Test
    fun `accepts patterns the backend regex crate supports`() {
        listOf(
            "^invoice",
            "(alpha|beta)+",
            "[a-z0-9_.-]+@example\\.com",
            "\\d{4}-\\d{2}-\\d{2}",
            "(?i)urgent",
            "(?:group)?",
            "a\\\\1b",
        ).forEach { assertNull(it, regex_error_res(it)) }
    }

    @Test
    fun `flags numeric and named backreferences`() {
        assertEquals(R.string.mail_rules_regex_backreference, regex_error_res("(\\w+)\\s\\1"))
        assertEquals(R.string.mail_rules_regex_backreference, regex_error_res("(a)(b)\\2"))
        assertEquals(R.string.mail_rules_regex_backreference, regex_error_res("(?P<w>\\w+) \\k<w>"))
    }

    @Test
    fun `flags lookahead and lookbehind`() {
        assertEquals(R.string.mail_rules_regex_lookaround, regex_error_res("(?=urgent)"))
        assertEquals(R.string.mail_rules_regex_lookaround, regex_error_res("foo(?!bar)"))
        assertEquals(R.string.mail_rules_regex_lookaround, regex_error_res("(?<=re: )ticket"))
        assertEquals(R.string.mail_rules_regex_lookaround, regex_error_res("(?<!un)happy"))
    }

    @Test
    fun `does not flag lookaround shaped text inside a character class`() {
        assertNull(regex_error_res("[(?=]"))
    }

    @Test
    fun `rejects empty and overlong patterns`() {
        assertEquals(R.string.mail_rules_regex_empty, regex_error_res(""))
        assertEquals(R.string.mail_rules_regex_too_long, regex_error_res("a".repeat(513)))
        assertNull(regex_error_res("a".repeat(512)))
    }

    @Test
    fun `rejects malformed patterns`() {
        assertEquals(R.string.mail_rules_regex_invalid, regex_error_res("(unclosed"))
        assertEquals(R.string.mail_rules_regex_invalid, regex_error_res("[\\1]"))
    }

    @Test
    fun `only inspects conditions using a regex operator`() {
        assertNull(condition_regex_error(Condition.Subject(op = TextOp.CONTAINS, value = "(?=x)")))
        assertEquals(
            R.string.mail_rules_regex_lookaround,
            condition_regex_error(Condition.Subject(op = TextOp.MATCHES_REGEX, value = "(?=x)")),
        )
        assertEquals(
            R.string.mail_rules_regex_backreference,
            condition_regex_error(Condition.From(op = AddressOp.MATCHES_REGEX, value = "(a)\\1")),
        )
    }

    @Test
    fun `blocks saving a condition whose regex the backend would reject`() {
        assertFalse(is_condition_complete(Condition.Body(op = TextOp.MATCHES_REGEX, value = "(?=x)")))
        assertTrue(is_condition_complete(Condition.Body(op = TextOp.MATCHES_REGEX, value = "^ok$")))
        assertFalse(is_condition_complete(Condition.Body(op = TextOp.MATCHES_REGEX, value = "")))
    }
}
