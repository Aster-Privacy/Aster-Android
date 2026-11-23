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

package org.astermail.android.translation

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationDetectionTextTest {
    private val reply = "Bonjour Anna, merci pour votre message. Nous vous enverrons une nouvelle facture demain."
    private val quoted = "Guten Tag, ich schreibe Ihnen wegen der Rechnung vom letzten Monat."

    @Test
    fun header_block_quote_is_left_out() {
        val text = "$reply\n\nFrom: someone@example.com\nDate: Nov 23, 2026, 4:42 PM\nSubject: Rechnung\n\n$quoted"
        assertEquals(reply, detection_text_of(text))
    }

    @Test
    fun attribution_quote_is_left_out() {
        val text = "$reply\n\nOn Mon, Nov 23, 2026 at 4:42 PM Anna <anna@example.com> wrote:\n> $quoted"
        assertEquals(reply, detection_text_of(text))
    }

    @Test
    fun trailing_quoted_lines_are_left_out() {
        val text = "$reply\n\n> $quoted\n> $quoted"
        assertEquals(reply, detection_text_of(text))
    }

    @Test
    fun short_reply_keeps_full_text() {
        val text = "Merci.\n\nFrom: someone@example.com\nDate: Nov 23, 2026, 4:42 PM\nSubject: Rechnung\n\n$quoted"
        assertEquals(text, detection_text_of(text))
    }

    @Test
    fun text_without_quote_is_unchanged() {
        assertEquals(reply, detection_text_of(reply))
    }
}
