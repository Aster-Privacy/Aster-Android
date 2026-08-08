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
import org.junit.Test

class BodyPreviewInvisiblesTest {

    @Test
    fun `strips object replacement characters left by inline images`() {
        assertEquals(
            "Your Apple Account",
            strip_body_html("￼￼ Your Apple Account"),
        )
    }

    @Test
    fun `strips interlinear annotation markers`() {
        assertEquals("abcd", strip_body_html("a￹b￺c￻d"))
    }

    @Test
    fun `strips bidi isolate markers`() {
        assertEquals("hello world", strip_body_html("⁦hello⁩ ⁧world⁨"))
    }

    @Test
    fun `preview of an apple style body starts with real text`() {
        val preview = clean_body_preview("￼￼\nYour Apple Account was used to sign in.", null)

        assertEquals("Your Apple Account was used to sign in.", preview)
    }

    @Test
    fun `does not split an astral character at the preview cap`() {
        val body = "a".repeat(PREVIEW_MAX_LENGTH - 1) + "😀tail"
        val preview = clean_body_preview(body, null)

        assertEquals("a".repeat(PREVIEW_MAX_LENGTH - 1), preview)
    }

    @Test
    fun `keeps an astral character that fits inside the cap`() {
        assertEquals("hi 😀", safe_display_text("hi 😀", 5))
    }

    @Test
    fun `take_whole_chars leaves short text untouched`() {
        assertEquals("short", take_whole_chars("short", PREVIEW_MAX_LENGTH))
    }
}
