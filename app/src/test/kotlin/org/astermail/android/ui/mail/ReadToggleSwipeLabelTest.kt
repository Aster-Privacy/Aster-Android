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

import compose.icons.TablerIcons
import compose.icons.tablericons.Archive
import compose.icons.tablericons.Mail
import compose.icons.tablericons.MailOpened
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadToggleSwipeLabelTest {
    @Test
    fun unread_row_offers_read() {
        assertEquals("Read", read_toggle_swipe_label("toggle_read", "Read", has_unread = true, unread_label = "Unread"))
        assertEquals(TablerIcons.MailOpened, read_toggle_swipe_icon("toggle_read", TablerIcons.MailOpened, has_unread = true))
    }

    @Test
    fun read_row_offers_unread() {
        assertEquals("Unread", read_toggle_swipe_label("toggle_read", "Read", has_unread = false, unread_label = "Unread"))
        assertEquals(TablerIcons.Mail, read_toggle_swipe_icon("toggle_read", TablerIcons.MailOpened, has_unread = false))
    }

    @Test
    fun other_actions_keep_their_label() {
        assertEquals("Archive", read_toggle_swipe_label("archive", "Archive", has_unread = false, unread_label = "Unread"))
        assertEquals(TablerIcons.Archive, read_toggle_swipe_icon("archive", TablerIcons.Archive, has_unread = false))
    }
}
