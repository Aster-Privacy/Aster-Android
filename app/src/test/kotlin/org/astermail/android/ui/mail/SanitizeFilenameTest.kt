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

package org.astermail.android.ui.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SanitizeFilenameTest {

    @Test
    fun strips_right_to_left_override() {
        assertEquals("invoicekpa.gnp", sanitize_filename("invoice\u202Ekpa.gnp"))
    }

    @Test
    fun strips_every_directional_control_character() {
        val raw = "\u202Aa\u202Bb\u202Cc\u202Dd\u202Ee\u2066f\u2067g\u2068h\u2069i\u200Ej\u200Fk.pdf"
        assertEquals("abcdefghijk.pdf", sanitize_filename(raw))
    }

    @Test
    fun keeps_arabic_letters() {
        assertEquals("فاتورة.pdf", sanitize_filename("فاتورة.pdf"))
    }

    @Test
    fun keeps_hebrew_letters() {
        assertEquals("חשבונית.pdf", sanitize_filename("חשבונית.pdf"))
    }

    @Test
    fun keeps_cjk_letters() {
        assertEquals("請求書_明細.pdf", sanitize_filename("請求書_明細.pdf"))
    }

    @Test
    fun still_strips_path_separators_and_control_bytes() {
        assertEquals("evil.pdf", sanitize_filename("../../etc/evil.pdf"))
        assertEquals("a_b.txt", sanitize_filename("a\u0001b.txt"))
    }

    @Test
    fun display_filename_removes_overrides_but_keeps_letters() {
        val shown = display_filename("invoice\u202Ekpa.gnp\u202C")
        assertEquals("invoicekpa.gnp", shown)
        assertFalse(shown.any { it == '\u202E' })
        assertEquals("فاتورة.pdf", display_filename("فاتورة.pdf"))
    }
}
