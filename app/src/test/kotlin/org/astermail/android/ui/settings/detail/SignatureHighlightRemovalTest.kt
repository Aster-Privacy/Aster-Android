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
package org.astermail.android.ui.settings.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureHighlightRemovalTest {
    @Test
    fun remove_command_calls_the_editor_unhighlight_function() {
        assertEquals("window.aster_unhighlight()", remove_highlight_script)
    }

    @Test
    fun editor_script_defines_the_unhighlight_function() {
        assertTrue(signature_editor_script.contains("window.aster_unhighlight=function()"))
    }

    @Test
    fun unhighlight_strips_both_background_properties_and_mark_tags() {
        assertTrue(signature_editor_script.contains("removeProperty('background-color')"))
        assertTrue(signature_editor_script.contains("removeProperty('background')"))
        assertTrue(signature_editor_script.contains("el.tagName==='MARK'"))
    }

    @Test
    fun unhighlight_does_not_paint_a_transparent_highlight() {
        val body = signature_editor_script.substringAfter("window.aster_unhighlight=function()")

        assertFalse(body.contains("hiliteColor"))
        assertFalse(body.contains("transparent"))
    }

    @Test
    fun unhighlight_sends_the_cleaned_html_back() {
        val body = signature_editor_script.substringAfter("window.aster_unhighlight=function()")

        assertTrue(body.contains("push();"))
    }
}
