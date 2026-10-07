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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnsubscribeLinkVisibilityTest {

    private val one_click = detect_unsubscribe_info(
        list_unsubscribe = "<https://news.example/unsub?u=1>, <mailto:unsub@news.example>",
        list_unsubscribe_post = "List-Unsubscribe=One-Click",
        dkim_result = "pass",
    )

    private val mailto_only = detect_unsubscribe_info(
        list_unsubscribe = "<mailto:unsub@news.example?subject=unsubscribe>",
    )

    private val none = detect_unsubscribe_info(
        html_content = "<p>Hello, see you on Friday.</p>",
        text_content = "Hello, see you on Friday.",
    )

    @Test
    fun shows_when_the_sender_supports_unsubscribe() {
        assertTrue(unsubscribe_link_visible(show_unsub = true, info = one_click))
        assertTrue(unsubscribe_link_visible(show_unsub = true, info = mailto_only))
    }

    @Test
    fun hidden_when_the_message_has_no_unsubscribe_option() {
        assertFalse(none.has_unsubscribe)
        assertFalse(unsubscribe_link_visible(show_unsub = true, info = none))
    }

    @Test
    fun hidden_after_unsubscribing_or_dismissing() {
        assertFalse(unsubscribe_link_visible(show_unsub = false, info = one_click))
        assertFalse(unsubscribe_link_visible(show_unsub = false, info = mailto_only))
    }
}
