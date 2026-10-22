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

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LongBodyScrollTest {

    @Test
    fun a_body_taller_than_its_view_overflows() {
        assertTrue(body_overflows_vertically(content_px = 5000, view_px = 2000, slack_px = 4))
        assertFalse(body_overflows_vertically(content_px = 2003, view_px = 2000, slack_px = 4))
        assertFalse(body_overflows_vertically(content_px = 5000, view_px = 0, slack_px = 4))
    }

    @Test
    fun the_body_keeps_drags_only_while_it_can_scroll_and_its_bottom_is_on_screen() {
        assertTrue(body_keeps_vertical_drag(true, -10f, can_scroll_down = true, can_scroll_up = false, bottom_visible = true))
        assertTrue(body_keeps_vertical_drag(true, 10f, can_scroll_down = false, can_scroll_up = true, bottom_visible = true))
        assertFalse(body_keeps_vertical_drag(true, -10f, can_scroll_down = false, can_scroll_up = true, bottom_visible = true))
        assertFalse(body_keeps_vertical_drag(true, -10f, can_scroll_down = true, can_scroll_up = false, bottom_visible = false))
        assertFalse(body_keeps_vertical_drag(false, -10f, can_scroll_down = true, can_scroll_up = true, bottom_visible = true))
    }

    @Test
    fun a_body_that_grows_after_load_is_measured_again() {
        assertTrue(body_needs_regrowth(content_dp = 4000f, shown_dp = 1200f, max_dp = 20000f, slack_dp = 8f))
        assertFalse(body_needs_regrowth(content_dp = 1205f, shown_dp = 1200f, max_dp = 20000f, slack_dp = 8f))
        assertFalse(body_needs_regrowth(content_dp = 30000f, shown_dp = 19995f, max_dp = 20000f, slack_dp = 8f))
        assertFalse(body_needs_regrowth(content_dp = 0f, shown_dp = 1200f, max_dp = 20000f, slack_dp = 8f))
    }

    @Test
    fun a_locked_growth_guard_unlocks_when_images_change_the_ratio() {
        val guard = body_growth_guard()
        assertEquals(208, guard.settle(179, 208))
        assertEquals(241, guard.settle(208, 241))
        assertEquals(179, guard.settle(241, 279))
        assertEquals(900, guard.settle(179, 900))
    }

    @Test
    fun a_body_capped_below_its_content_can_scroll_inside_the_page() {
        val html = build_email_html(
            body = "<div dir=\"ltr\"><p>Paragraph</p></div>",
            is_dark = false,
            fg_hex = "#111111",
            link_hex = "#3b82f6",
            forwarded_label = "Forwarded",
            image_failed_label = "Image could not be loaded",
            force_dark_emails = false,
            dyslexia_font = false,
            translate_mode = "off",
        )
        val css = Jsoup.parse(html).select("style").joinToString(" ") { it.data() }

        assertTrue(css.contains("overflow-y:auto"))
        assertFalse(css.contains("overflow-y:hidden"))
    }
}
