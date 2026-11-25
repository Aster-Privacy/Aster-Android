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

package org.astermail.android.mail.extraction

import org.junit.Assert.assertEquals
import org.junit.Test

class own_content_scan_time_test {

    @Test(timeout = 2_000)
    fun long_dash_runs_in_a_preview_finish_quickly() {
        val dashes = "-".repeat(100_000)
        assertEquals("Hi $dashes", own_preview_text("Hello", "Hi $dashes"))
        own_preview_text("Hello", "Hi " + "-- ".repeat(30_000))
    }

    @Test(timeout = 2_000)
    fun long_spacing_after_attribution_words_finishes_quickly() {
        val spaces = " ".repeat(100_000)
        own_preview_text("Hello", "Hi\n" + " On abcd wrote$spaces".repeat(3))
        own_preview_text("Hello", "Hi\n" + " From: a b Sent$spaces".repeat(3))
    }

    @Test
    fun inline_quotes_are_still_cut_from_previews() {
        assertEquals("Sounds good.", own_preview_text("Re: Lunch", "Sounds good. On Tue, Oct 6 Ada wrote: see you"))
        assertEquals(
            "Thanks",
            own_preview_text("Re: Plan", "Thanks ----- Original Message ----- From: Ada"),
        )
        assertEquals(
            "Ok",
            own_preview_text("Re: Plan", "Ok From: Ada Park Sent: Tuesday"),
        )
        assertEquals("Ja", own_preview_text("AW: Plan", "Ja Am 6. Okt. 2026 schrieb Ada Park:"))
        assertEquals("Plain update", own_preview_text("Weekly", "Plain update"))
    }
}
