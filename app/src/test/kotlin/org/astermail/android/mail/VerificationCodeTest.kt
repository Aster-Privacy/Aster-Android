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
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationCodeTest {

    @Test
    fun corpus_has_sixty_cases_on_each_side() {
        assertTrue(VERIFICATION_CODE_CASES.count { it.expected != null } >= 60)
        assertTrue(VERIFICATION_CODE_CASES.count { it.expected == null } >= 60)
    }

    @Test
    fun corpus_matches_expected_codes() {
        val failures = VERIFICATION_CODE_CASES.mapNotNull { case ->
            val found = detect_verification_code(case.subject, case.body, case.sender)
            if (found == case.expected) null else "expected ${case.expected} got $found for ${case.subject} / ${case.body.take(120)}"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun ignores_missing_sources() {
        assertNull(detect_verification_code(null, null))
        assertNull(detect_verification_code("", ""))
        assertEquals("918273", detect_verification_code(null, "Your code is 918273"))
    }

    @Test
    fun reply_needs_an_automated_sender_address() {
        assertEquals(
            "481920",
            detect_verification_code(
                "Re: Sign-in request",
                "Use 481920 to finish signing in",
                "Example Security <no-reply@example.com>",
            ),
        )
        assertNull(
            detect_verification_code(
                "Re: Sign-in request",
                "Use 481920 to finish signing in",
                "Sam <sam@example.com>",
            ),
        )
    }
}
