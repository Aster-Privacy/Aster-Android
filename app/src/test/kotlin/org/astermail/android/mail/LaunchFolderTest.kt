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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchFolderTest {

    private val main = "android.intent.action.MAIN"
    private val target_extras = setOf("open_email_id", "open_sessions")

    private fun resolve(
        stored: String?,
        plain_launch: Boolean = true,
        has_pending_target: Boolean = false,
        categories_enabled: Boolean = true,
        active_tabs: List<String> = CATEGORY_TABS,
    ) = resolve_launch_destination(stored, plain_launch, has_pending_target, categories_enabled, active_tabs)

    @Test
    fun nothing_stored_opens_the_primary_inbox() {
        assertEquals(launch_destination("inbox", "primary"), resolve(null))
        assertEquals(launch_destination("inbox", "primary"), resolve("inbox"))
    }

    @Test
    fun each_system_folder_opens_that_folder() {
        for (folder in listOf("all", "starred", "sent", "drafts", "archive")) {
            assertEquals(launch_destination(folder, "primary"), resolve(folder))
        }
    }

    @Test
    fun the_system_folders_match_the_drawer_ids() {
        assertEquals(listOf("inbox", "all", "starred", "sent", "drafts", "archive"), launch_folder_system_ids)
    }

    @Test
    fun a_category_opens_the_inbox_on_that_tab() {
        assertEquals(launch_destination("inbox", "social"), resolve("category:social"))
        assertEquals(launch_destination("inbox", "promotions"), resolve(launch_folder_for_category("promotions")))
    }

    @Test
    fun primary_is_stored_as_the_inbox() {
        assertEquals("inbox", launch_folder_for_category("primary"))
    }

    @Test
    fun a_disabled_category_falls_back_to_primary() {
        assertEquals(
            launch_destination.inbox,
            resolve("category:social", active_tabs = listOf("primary", "updates")),
        )
    }

    @Test
    fun a_category_falls_back_to_primary_when_tabs_are_off() {
        assertEquals(launch_destination.inbox, resolve("category:social", categories_enabled = false))
    }

    @Test
    fun a_folder_that_no_longer_exists_falls_back_to_the_inbox() {
        assertEquals(launch_destination.inbox, resolve("f_9b1c2d3e"))
        assertEquals(launch_destination.inbox, resolve("category:custom:7f2a"))
        assertEquals(launch_destination.inbox, resolve("category:primary"))
        assertEquals(launch_destination.inbox, resolve(""))
    }

    @Test
    fun spam_and_trash_are_not_offered() {
        assertEquals(launch_destination.inbox, resolve("spam"))
        assertEquals(launch_destination.inbox, resolve("trash"))
    }

    @Test
    fun a_notification_or_link_launch_keeps_the_inbox() {
        assertEquals(launch_destination.inbox, resolve("all", plain_launch = false))
        assertEquals(launch_destination.inbox, resolve("category:social", plain_launch = false))
    }

    @Test
    fun a_pending_message_or_share_keeps_the_inbox() {
        assertEquals(launch_destination.inbox, resolve("all", has_pending_target = true))
    }

    @Test
    fun only_a_bare_main_intent_counts_as_a_plain_launch() {
        assertTrue(is_plain_launch(main, has_data = false, extra_keys = emptySet(), target_extra_keys = target_extras))
        assertTrue(is_plain_launch(main, has_data = false, extra_keys = setOf("profile"), target_extra_keys = target_extras))
        assertFalse(is_plain_launch(null, has_data = false, extra_keys = setOf("open_email_id"), target_extra_keys = target_extras))
        assertFalse(is_plain_launch(null, has_data = false, extra_keys = emptySet(), target_extra_keys = target_extras))
        assertFalse(is_plain_launch(main, has_data = false, extra_keys = setOf("open_sessions"), target_extra_keys = target_extras))
        assertFalse(is_plain_launch("android.intent.action.VIEW", has_data = true, extra_keys = emptySet(), target_extra_keys = target_extras))
        assertFalse(is_plain_launch("android.intent.action.SENDTO", has_data = true, extra_keys = emptySet(), target_extra_keys = target_extras))
        assertFalse(is_plain_launch("android.intent.action.SEND", has_data = false, extra_keys = setOf("android.intent.extra.TEXT"), target_extra_keys = target_extras))
        assertFalse(is_plain_launch(main, has_data = true, extra_keys = emptySet(), target_extra_keys = target_extras))
    }

    @Test
    fun sanitize_keeps_only_known_values() {
        assertEquals("all", sanitize_launch_folder("all"))
        assertEquals("category:updates", sanitize_launch_folder("category:updates"))
        assertEquals("inbox", sanitize_launch_folder("category:nope"))
        assertEquals("inbox", sanitize_launch_folder(null))
    }
}
