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

package org.astermail.android.ui.icons

import compose.icons.TablerIcons
import compose.icons.tablericons.Mail
import compose.icons.tablericons.MailOpened
import org.astermail.android.ui.mail.selection_read_toolbar_action
import org.astermail.android.ui.mail.selection_toolbar_action_catalog
import org.astermail.android.ui.mail.swipe_action_icon
import org.astermail.android.ui.mail.toolbar_action_catalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReadStateIconTest {
    @Test
    fun toggle_shows_the_result_of_the_action() {
        assertEquals(mail_unread_icon, read_toggle_icon(is_read = true))
        assertEquals(TablerIcons.MailOpened, read_toggle_icon(is_read = false))
    }

    @Test
    fun unread_glyph_differs_from_plain_and_not_spam_envelopes() {
        assertNotEquals(TablerIcons.Mail, mail_unread_icon)
        assertNotEquals(mail_check_icon, mail_unread_icon)
    }

    @Test
    fun catalogs_use_the_shared_read_icons() {
        assertEquals(mark_read_icon, toolbar_action_catalog.single { it.id == "read" }.icon)
        assertEquals(mark_read_icon, selection_toolbar_action_catalog.single { it.id == "read" }.icon)
        assertEquals(mail_unread_icon, selection_toolbar_action_catalog.single { it.id == "unread" }.icon)
        assertEquals(mail_unread_icon, selection_read_toolbar_action(selection_all_read = true).icon)
        assertEquals(mark_read_icon, selection_read_toolbar_action(selection_all_read = false).icon)
        assertEquals(mark_read_icon, swipe_action_icon("toggle_read"))
    }
}
