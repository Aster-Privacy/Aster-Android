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

import org.astermail.android.ui.mail.subject_prefix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class subject_limit_test {
    @Test
    fun keeps_a_short_subject() {
        assertEquals("Re: hi", clamp_subject("Re: hi"))
    }

    @Test
    fun cuts_a_long_subject_to_the_limit() {
        val clamped = clamp_subject("word ".repeat(400))
        assertTrue(clamped.codePointCount(0, clamped.length) <= MAX_SUBJECT_LENGTH)
    }

    @Test
    fun never_splits_an_emoji() {
        val emoji = String(Character.toChars(0x1F600))
        val clamped = clamp_subject(emoji.repeat(MAX_SUBJECT_LENGTH + 5))
        assertEquals(MAX_SUBJECT_LENGTH, clamped.codePointCount(0, clamped.length))
        assertTrue(clamped.endsWith(emoji))
    }

    @Test
    fun reply_to_a_maximum_length_subject_stays_within_the_limit() {
        val reply = subject_prefix("a".repeat(MAX_SUBJECT_LENGTH), "reply")
        assertTrue(reply.startsWith("Re: aaa"))
        assertEquals(MAX_SUBJECT_LENGTH, reply.length)
    }

    @Test
    fun forward_of_a_maximum_length_subject_stays_within_the_limit() {
        val forward = subject_prefix("a".repeat(MAX_SUBJECT_LENGTH), "forward")
        assertTrue(forward.startsWith("Fwd: aaa"))
        assertEquals(MAX_SUBJECT_LENGTH, forward.length)
    }
}
