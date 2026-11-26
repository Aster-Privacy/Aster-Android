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

package org.astermail.android.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class trailing_breaks_test {
    @Test
    fun removes_breaks_after_last_line() {
        assertEquals(
            "Best regards,<br>Aster Support",
            trim_trailing_breaks("Best regards,<br>Aster Support<br><br>"),
        )
    }

    @Test
    fun removes_mixed_breaks_and_spaces() {
        assertEquals(
            "<b>Aster Support</b>",
            trim_trailing_breaks("<b>Aster Support</b><br> <br>\n"),
        )
    }

    @Test
    fun rich_body_without_signature_has_no_trailing_breaks() {
        val html = assemble_body_with_signature(
            body = trim_trailing_breaks("Best regards,<br>Aster Support<br><br>"),
            is_rich = true,
            plain_signature = "",
            html_signature = "",
            signature_id = null,
            quote_html = "",
            place_below = false,
        )
        assertEquals(false, html.removeSuffix("</div>").endsWith("<br>"))
    }

    @Test
    fun keeps_breaks_inside_the_body() {
        val body = "Hi,<br><br>Thanks.<br>Aster Support"
        assertEquals(body, trim_trailing_breaks(body))
    }

    @Test
    fun keeps_plain_text_without_trailing_breaks() {
        assertEquals("Aster Support", trim_trailing_breaks("Aster Support"))
    }
}
