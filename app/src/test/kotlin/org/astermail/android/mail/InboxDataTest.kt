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

import io.mockk.mockk
import org.astermail.android.api.mail.MailItemMetadata
import org.astermail.android.api.settings.AliasInfo
import org.astermail.android.api.settings.BlockedSenderInfo
import org.astermail.android.api.settings.StorageOverview
import org.astermail.android.api.settings.SubscriptionInfo
import org.astermail.android.settings.SaveStatus
import org.astermail.android.settings.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxDataTest {

    @Test
    fun `InboxUiState defaults are correct`() {
        val state = InboxUiState()
        assertTrue(state.items.isEmpty())
        assertFalse(state.is_loading)
        assertTrue(state.initial)
        assertFalse(state.is_loading_more)
        assertNull(state.error)
        assertFalse(state.has_more)
        assertNull(state.next_cursor)
        assertEquals(0, state.total)
        assertEquals("inbox", state.current_folder)
        assertNull(state.stats)
    }

    @Test
    fun `ThreadUiState defaults are correct`() {
        val state = ThreadUiState()
        assertTrue(state.messages.isEmpty())
        assertFalse(state.is_loading)
        assertNull(state.error)
        assertNull(state.item)
    }

    @Test
    fun `SearchUiState defaults are correct`() {
        val state = SearchUiState()
        assertTrue(state.all_items.isEmpty())
        assertFalse(state.is_indexing)
        assertFalse(state.is_indexed)
        assertNull(state.error)
    }

    @Test
    fun `InboxItem copy modifies correct fields`() {
        val item = InboxItem(
            id = "1", thread_token = "t1", thread_message_count = 1,
            sender_name = "Alice", sender_email = "alice@x.com",
            subject = "Hello", preview = "Hi there",
            timestamp = "2026-04-26T10:00:00Z",
            is_read = false, is_starred = false, is_encrypted = true,
            has_attachments = false, is_trashed = false, is_archived = false,
            is_spam = false, labels = emptyList(), raw_item = mockk(relaxed = true),
        )

        val starred = item.copy(is_starred = true)
        assertTrue(starred.is_starred)
        assertFalse(starred.is_read)
        assertEquals("1", starred.id)

        val read = item.copy(is_read = true)
        assertTrue(read.is_read)
        assertFalse(read.is_starred)
    }

    @Test
    fun `InboxPage with pagination data`() {
        val page = InboxPage(
            items = emptyList(),
            has_more = true,
            next_cursor = "cursor_abc",
            total = 150,
        )
        assertTrue(page.has_more)
        assertEquals("cursor_abc", page.next_cursor)
        assertEquals(150, page.total)
    }

    @Test
    fun `MailItemMetadata stores all flags`() {
        val meta = MailItemMetadata(
            is_read = true,
            is_starred = true,
            is_trashed = false,
            is_archived = true,
            is_spam = false,
            has_attachments = true,
        )
        assertTrue(meta.is_read)
        assertTrue(meta.is_starred)
        assertFalse(meta.is_trashed)
        assertTrue(meta.is_archived)
        assertFalse(meta.is_spam)
        assertTrue(meta.has_attachments)
    }

    @Test
    fun `AliasInfo address computed property`() {
        val alias = AliasInfo(
            id = "a1",
            encrypted_local_part = "myalias",
            domain = "astermail.org",
        )
        assertEquals("myalias@astermail.org", alias.address)
    }

    @Test
    fun `AliasInfo address falls back to id when domain empty`() {
        val alias = AliasInfo(id = "a1", encrypted_local_part = "", domain = "")
        assertEquals("a1", alias.address)
    }

    @Test
    fun `BlockedSenderInfo defaults`() {
        val blocked = BlockedSenderInfo(address = "spam@evil.com")
        assertEquals("spam@evil.com", blocked.address)
        assertEquals(0, blocked.blocked_count)
        assertNull(blocked.created_at)
    }

    @Test
    fun `StorageOverview percentage calculation`() {
        val storage = StorageOverview(
            used_bytes = 536_870_912,
            total_bytes = 1_073_741_824,
            percentage_used = 50.0,
            is_over_limit = false,
            addon_bytes = 0,
        )
        assertEquals(50.0, storage.percentage_used, 0.01)
        assertFalse(storage.is_over_limit)
    }

    @Test
    fun `StorageOverview over limit flag`() {
        val storage = StorageOverview(
            used_bytes = 1_200_000_000,
            total_bytes = 1_073_741_824,
            percentage_used = 111.7,
            is_over_limit = true,
        )
        assertTrue(storage.is_over_limit)
    }

    @Test
    fun `SubscriptionInfo with free plan`() {
        val sub = SubscriptionInfo()
        assertNull(sub.plan_name)
        assertEquals(0, sub.amount)
        assertEquals("usd", sub.currency)
    }

    @Test
    fun `SubscriptionInfo with paid plan`() {
        val sub = SubscriptionInfo(
            plan_name = "Supernova",
            status = "active",
            amount = 1999,
            currency = "usd",
            interval = "month",
            current_period_end = "2026-05-26T00:00:00Z",
        )
        assertEquals("Supernova", sub.plan_name)
        assertEquals(1999, sub.amount)
        assertEquals("month", sub.interval)
    }

    @Test
    fun `SettingsUiState defaults are correct`() {
        val state = SettingsUiState()
        assertNull(state.user)
        assertTrue(state.sessions.isEmpty())
        assertTrue(state.blocked_senders.isEmpty())
        assertTrue(state.aliases.isEmpty())
        assertNull(state.storage)
        assertNull(state.subscription)
        assertNull(state.security_status)
        assertTrue(state.labels.isEmpty())
        assertNull(state.referral)
        assertNull(state.preferences)
        assertTrue(state.ghost_aliases.isEmpty())
        assertTrue(state.forwarding_rules.isEmpty())
        assertTrue(state.api_keys.isEmpty())
        assertTrue(state.webhooks.isEmpty())
        assertFalse(state.is_loading)
        assertNull(state.error)
        assertEquals(SaveStatus.IDLE, state.save_status)
    }

    @Test
    fun `SaveStatus enum values`() {
        assertEquals(4, SaveStatus.entries.size)
        assertEquals(SaveStatus.IDLE, SaveStatus.valueOf("IDLE"))
        assertEquals(SaveStatus.SAVING, SaveStatus.valueOf("SAVING"))
        assertEquals(SaveStatus.SAVED, SaveStatus.valueOf("SAVED"))
        assertEquals(SaveStatus.ERROR, SaveStatus.valueOf("ERROR"))
    }

    @Test
    fun `DecryptedEnvelope stores all fields`() {
        val envelope = DecryptedEnvelope(
            subject = "Test",
            body_text = "Hello world",
            body_html = "<p>Hello world</p>",
            from_name = "Alice",
            from_email = "alice@astermail.org",
            to = listOf("Bob" to "bob@astermail.org"),
            cc = listOf("Charlie" to "charlie@astermail.org"),
            sent_at = "2026-04-26T10:00:00Z",
        )
        assertEquals("Test", envelope.subject)
        assertEquals("alice@astermail.org", envelope.from_email)
        assertEquals(1, envelope.to.size)
        assertEquals(1, envelope.cc.size)
    }

    @Test
    fun `ThreadMessageDecrypted stores fields`() {
        val msg = ThreadMessageDecrypted(
            id = "m1",
            sender_name = "Alice",
            sender_email = "alice@x.com",
            to_label = "me",
            timestamp = "2026-04-26T10:00:00Z",
            body_text = "Hello",
            body_html = null,
            is_encrypted = true,
            is_read = false,
            raw_item = mockk(relaxed = true),
        )
        assertEquals("m1", msg.id)
        assertTrue(msg.is_encrypted)
        assertFalse(msg.is_read)
        assertNull(msg.body_html)
    }

    @Test
    fun `InboxItem with labels`() {
        val item = InboxItem(
            id = "1", thread_token = "t1", thread_message_count = 1,
            sender_name = "S", sender_email = "s@x.com",
            subject = "Sub", preview = "P",
            timestamp = "2026-04-26",
            is_read = true, is_starred = false, is_encrypted = true,
            has_attachments = false, is_trashed = false, is_archived = false,
            is_spam = false, labels = listOf("Work", "Urgent"),
            raw_item = mockk(relaxed = true),
        )
        assertEquals(2, item.labels.size)
        assertEquals("Work", item.labels[0])
        assertEquals("Urgent", item.labels[1])
    }
}
