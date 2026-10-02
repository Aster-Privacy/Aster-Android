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

package org.astermail.android.mail_rules

import org.astermail.android.api.labels.LabelItem
import org.astermail.android.api.mail_rules.Action
import org.astermail.android.api.mail_rules.AddressOp
import org.astermail.android.api.mail_rules.Condition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleFoldersTest {

    private fun label(token: String, folder_type: String, is_system: Boolean = true) =
        LabelItem(id = token, label_token = token, folder_type = folder_type, is_system = is_system)

    private val labels = listOf(
        label("t_trash", "trash"),
        label("t_sent", "sent"),
        label("t_spam", "spam"),
        label("t_drafts", "drafts"),
        label("t_archive", "archive"),
        label("t_inbox", "inbox"),
        label("t_custom", "folder", is_system = false),
    )

    @Test
    fun `system folder types map to rule targets`() {
        assertEquals(RuleSystemFolder.INBOX, rule_system_folder_type("inbox"))
        assertEquals(RuleSystemFolder.INBOX, rule_system_folder_type("default_open"))
        assertEquals(RuleSystemFolder.ARCHIVE, rule_system_folder_type("archive"))
        assertEquals(RuleSystemFolder.SPAM, rule_system_folder_type("spam"))
        assertEquals(RuleSystemFolder.TRASH, rule_system_folder_type("trash"))
        assertNull(rule_system_folder_type("sent"))
        assertNull(rule_system_folder_type("drafts"))
        assertNull(rule_system_folder_type("folder"))
        assertNull(rule_system_folder_type("custom"))
        assertNull(rule_system_folder_type(null))
    }

    @Test
    fun `system folders are ordered and exclude sent and drafts`() {
        val result = rule_system_folders(labels)
        assertEquals(listOf("t_inbox", "t_archive", "t_spam", "t_trash"), result.map { it.folder.label_token })
        assertEquals(
            listOf(RuleSystemFolder.INBOX, RuleSystemFolder.ARCHIVE, RuleSystemFolder.SPAM, RuleSystemFolder.TRASH),
            result.map { it.system_type },
        )
    }

    @Test
    fun `legacy default_open folder stands in for a missing inbox`() {
        val result = rule_system_folders(listOf(label("t_open", "default_open"), label("t_spam", "spam")))
        assertEquals(listOf("t_open", "t_spam"), result.map { it.folder.label_token })
        assertEquals(RuleSystemFolder.INBOX, result.first().system_type)
    }

    @Test
    fun `inbox is preferred over default_open`() {
        val result = rule_system_folders(listOf(label("t_open", "default_open"), label("t_inbox", "inbox")))
        assertEquals(listOf("t_inbox"), result.map { it.folder.label_token })
    }

    @Test
    fun `blank tokens and missing folders are skipped`() {
        assertTrue(rule_system_folders(emptyList()).isEmpty())
        assertTrue(rule_system_folders(listOf(label("", "inbox"))).isEmpty())
        assertTrue(rule_system_folders(listOf(label("t_custom", "folder", is_system = false))).isEmpty())
    }

    @Test
    fun `folder type lookup by token`() {
        assertEquals("archive", rule_folder_type_of(labels, "t_archive"))
        assertNull(rule_folder_type_of(labels, "unknown"))
        assertNull(rule_folder_type_of(labels, null))
    }

    @Test
    fun `rule target matches alias delivery`() {
        assertTrue(rule_target_matches_alias_delivery("t_inbox", "inbox", null, false))
        assertFalse(rule_target_matches_alias_delivery("t_inbox", "inbox", null, true))
        assertTrue(rule_target_matches_alias_delivery("t_archive", "archive", null, true))
        assertFalse(rule_target_matches_alias_delivery("t_archive", "archive", null, false))
        assertFalse(rule_target_matches_alias_delivery("t_spam", "spam", null, false))
        assertTrue(rule_target_matches_alias_delivery("t_custom", "folder", "t_custom", false))
        assertFalse(rule_target_matches_alias_delivery("t_inbox", "inbox", "t_custom", false))
    }

    private val conditions = listOf(Condition.To(op = AddressOp.IS, value = "shop@astermail.org"))

    @Test
    fun `archive rule does not conflict with an archive alias`() {
        val conflict = rule_alias_delivery_conflict(
            conditions = conditions,
            actions = listOf(Action.MoveTo("t_archive")),
            alias_delivery = mapOf("shop@astermail.org" to AliasDeliverySetting(null, null, never_inbox = true)),
            folder_type_of = { rule_folder_type_of(labels, it) },
        )
        assertNull(conflict)
    }

    @Test
    fun `inbox rule conflicts with an archive alias`() {
        val conflict = rule_alias_delivery_conflict(
            conditions = conditions,
            actions = listOf(Action.MoveTo("t_inbox")),
            alias_delivery = mapOf("shop@astermail.org" to AliasDeliverySetting(null, null, never_inbox = true)),
            folder_type_of = { rule_folder_type_of(labels, it) },
        )
        assertNotNull(conflict)
        assertEquals("t_inbox", conflict?.rule_folder_token)
    }

    @Test
    fun `archive rule conflicts with a custom folder alias`() {
        val conflict = rule_alias_delivery_conflict(
            conditions = conditions,
            actions = listOf(Action.MoveTo("t_archive")),
            alias_delivery = mapOf("shop@astermail.org" to AliasDeliverySetting("t_custom", null, never_inbox = false)),
            folder_type_of = { rule_folder_type_of(labels, it) },
        )
        assertNotNull(conflict)
    }

    @Test
    fun `system rule targets never conflict with a default inbox alias`() {
        listOf("t_inbox", "t_archive", "t_spam", "t_trash").forEach { token ->
            val conflict = rule_alias_delivery_conflict(
                conditions = conditions,
                actions = listOf(Action.MoveTo(token)),
                alias_delivery = mapOf("shop@astermail.org" to AliasDeliverySetting(null, null, never_inbox = false)),
                folder_type_of = { rule_folder_type_of(labels, it) },
            )
            assertNull(conflict)
        }
    }
}
