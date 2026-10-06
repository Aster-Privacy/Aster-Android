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
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.Ban
import compose.icons.tablericons.Inbox
import compose.icons.tablericons.Mail
import compose.icons.tablericons.Shield
import compose.icons.tablericons.ShieldCheck
import compose.icons.tablericons.ShieldLock
import compose.icons.tablericons.ShieldOff
import compose.icons.tablericons.ShieldX
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.VectorPath
import org.astermail.android.ui.icons.mail_check_icon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SpamActionIconTest {

    @Test
    fun not_spam_is_a_mail_check_and_report_spam_is_a_warning_triangle() {
        assertEquals(mail_check_icon, spam_action_icon(is_spam = true))
        assertEquals(TablerIcons.AlertTriangle, spam_action_icon(is_spam = false))
    }

    @Test
    fun not_spam_is_no_longer_the_inbox_tray_or_a_plain_envelope() {
        assertNotEquals(TablerIcons.Inbox, spam_action_icon(is_spam = true))
        assertNotEquals(TablerIcons.Mail, spam_action_icon(is_spam = true))
    }

    @Test
    fun mail_check_matches_the_tabler_outline_style() {
        assertEquals(24f, mail_check_icon.viewportWidth)
        assertEquals(24f, mail_check_icon.viewportHeight)
        assertEquals(24f, mail_check_icon.defaultWidth.value)
        assertEquals(24f, mail_check_icon.defaultHeight.value)
        val paths = (0 until mail_check_icon.root.size).map { mail_check_icon.root[it] as VectorPath }
        assertEquals(3, paths.size)
        paths.forEach { path ->
            assertEquals(2f, path.strokeLineWidth)
            assertEquals(StrokeCap.Round, path.strokeLineCap)
            assertEquals(StrokeJoin.Round, path.strokeLineJoin)
            assertEquals(null, path.fill)
        }
    }

    @Test
    fun not_spam_never_borrows_the_tracking_protection_shield() {
        assertNotEquals(TablerIcons.ShieldCheck, spam_action_icon(is_spam = true))
        listOf(
            TablerIcons.Shield,
            TablerIcons.ShieldCheck,
            TablerIcons.ShieldLock,
            TablerIcons.ShieldOff,
            TablerIcons.ShieldX,
        ).forEach { shield ->
            assertNotEquals(shield, spam_action_icon(is_spam = true))
            assertNotEquals(shield, spam_action_icon(is_spam = false))
        }
    }

    @Test
    fun not_spam_and_report_spam_stay_distinct() {
        assertNotEquals(spam_action_icon(is_spam = false), spam_action_icon(is_spam = true))
    }

    @Test
    fun spam_actions_never_borrow_the_block_sender_glyph() {
        assertNotEquals(TablerIcons.Ban, spam_action_icon(is_spam = true))
        assertNotEquals(TablerIcons.Ban, spam_action_icon(is_spam = false))
    }

    @Test
    fun toolbar_pickers_show_the_report_spam_icon() {
        assertEquals(spam_action_icon(is_spam = false), toolbar_action_catalog.single { it.id == "spam" }.icon)
        assertEquals(spam_action_icon(is_spam = false), selection_toolbar_action_catalog.single { it.id == "spam" }.icon)
    }

    @Test
    fun swipes_use_the_same_icons_as_the_spam_actions() {
        assertEquals(spam_action_icon(is_spam = false), swipe_action_icon("spam"))
        assertEquals(spam_action_icon(is_spam = true), swipe_action_icon("unmark_spam"))
    }
}
