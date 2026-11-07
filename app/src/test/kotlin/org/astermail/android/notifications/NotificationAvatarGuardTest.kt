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

package org.astermail.android.notifications

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.astermail.android.api.mail.MailItem
import org.astermail.android.mail.InboxItem
import org.astermail.android.security.SenderTrustContext
import org.astermail.android.ui.mail.inbox_logo_root_domain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAvatarGuardTest {

    private val on = NotificationAvatarSettings(show_pictures = true, low_network = false)
    private val trust_context = SenderTrustContext(own_addresses = listOf("me@astermail.org"))

    private fun authenticated_raw(): MailItem = mockk(relaxed = true) {
        every { item_type } returns "received"
        every { metadata } returns null
        every { spf_result } returns "pass"
        every { dkim_result } returns "pass"
        every { dmarc_result } returns "pass"
        every { sender_aligned } returns true
        every { sender_verified } returns null
        every { sender_verified_domain } returns null
        every { is_external } returns true
        every { system_origin } returns false
    }

    private fun item(
        sender_email: String,
        display_sender_email: String? = null,
        sender_name: String = "Bank",
    ) = InboxItem(
        id = "item-1",
        thread_token = null,
        thread_message_count = 1,
        sender_name = sender_name,
        sender_email = sender_email,
        subject = "Subject",
        preview = "Preview",
        timestamp = "2026-11-06T12:00:00Z",
        is_read = false,
        is_starred = false,
        is_encrypted = true,
        has_attachments = false,
        is_trashed = false,
        is_archived = false,
        is_spam = false,
        labels = emptyList(),
        display_sender_email = display_sender_email,
        raw_item = authenticated_raw(),
    )

    private fun inbox_plan(item: InboxItem, ready: Boolean = true) = inbox_item_avatar_plan(
        item,
        item.sender_name,
        on,
        content_hidden = false,
        trust_context = trust_context,
        trust_context_ready = ready,
    )

    private fun http_error(code: Int) = coil.network.HttpException(
        Response.Builder()
            .request(Request.Builder().url("https://example.invalid/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("status")
            .build(),
    )

    @Test
    fun authenticated_inbox_item_gets_its_logo() {
        val plan = inbox_plan(item("news@mail.github.com")) as NotificationAvatarPlan.Picture
        assertEquals("github.com", plan.root_domain)
    }

    @Test
    fun forwarded_or_rewritten_display_address_gets_initials() {
        val rewritten = item(sender_email = "bounce@relay.example", display_sender_email = "security@bank.example")
        assertTrue(inbox_plan(rewritten) is NotificationAvatarPlan.Initials)
    }

    @Test
    fun tenant_inbox_items_get_initials_in_notifications() {
        assertTrue(inbox_plan(item("orders@acme.myshopify.com")) is NotificationAvatarPlan.Initials)
        assertTrue(inbox_plan(item("it@contoso.onmicrosoft.com")) is NotificationAvatarPlan.Initials)
    }

    @Test
    fun inbox_item_waits_for_the_trust_context() {
        assertTrue(inbox_plan(item("news@mail.github.com"), ready = false) is NotificationAvatarPlan.Initials)
    }

    @Test
    fun inbox_avatar_drops_the_logo_for_tenant_domains_only() {
        assertEquals("", inbox_logo_root_domain("acme.myshopify.com"))
        assertEquals("", inbox_logo_root_domain("help.acme.zendesk.com"))
        assertEquals("", inbox_logo_root_domain("contoso.sharepoint.com"))
        assertEquals("", inbox_logo_root_domain("demo.web.app"))
        assertEquals("", inbox_logo_root_domain("diary.blogspot.com"))
        assertEquals("", inbox_logo_root_domain(""))
        assertEquals("github.com", inbox_logo_root_domain("mail.github.com"))
        assertEquals("zendesk.com", inbox_logo_root_domain("zendesk.com"))
        assertEquals("shop.co.uk", inbox_logo_root_domain("mail.shop.co.uk"))
    }

    @Test
    fun hidden_content_never_runs_avatar_work() = runBlocking {
        var planned = false
        var rendered = false
        var released = false
        val icon = guarded_avatar_icon<String>(
            content_hidden = true,
            plan = {
                planned = true
                NotificationAvatarPlan.AppIcon
            },
            render = {
                rendered = true
                "icon"
            },
            release_claim = { released = true },
        )
        assertNull(icon)
        assertFalse(planned)
        assertFalse(rendered)
        assertFalse(released)
    }

    @Test
    fun a_cancelled_avatar_fetch_releases_the_notification_claim() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var released = false
        val job = launch(Dispatchers.Default) {
            guarded_avatar_icon<String>(
                content_hidden = false,
                plan = { NotificationAvatarPlan.AppIcon },
                render = {
                    started.complete(Unit)
                    awaitCancellation()
                },
                release_claim = { released = true },
            )
        }
        started.await()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(released)
    }

    @Test
    fun a_failed_render_keeps_the_claim() = runBlocking {
        var released = false
        val icon = guarded_avatar_icon<String>(
            content_hidden = false,
            plan = { NotificationAvatarPlan.AppIcon },
            render = { throw IllegalStateException("decode") },
            release_claim = { released = true },
        )
        assertNull(icon)
        assertFalse(released)
    }

    @Test
    fun only_definitive_http_misses_reach_the_shared_inbox_cache() {
        val shared = mutableListOf<String>()
        val misses = NotificationLogoMisses()
        record_notification_logo_miss(misses, "timeout.example", null) { shared.add(it) }
        record_notification_logo_miss(misses, "rejected.example", NotificationLogoRejected()) { shared.add(it) }
        record_notification_logo_miss(misses, "busy.example", http_error(503)) { shared.add(it) }
        assertTrue(shared.isEmpty())
        for (code in listOf(400, 404, 410)) {
            record_notification_logo_miss(misses, "gone$code.example", http_error(code)) { shared.add(it) }
        }
        assertEquals(listOf("gone400.example", "gone404.example", "gone410.example"), shared)
        for (domain in listOf("timeout.example", "rejected.example", "busy.example", "gone404.example")) {
            assertTrue(domain, misses.is_blocked(domain))
        }
    }

    @Test
    fun notification_misses_expire_and_stay_bounded() {
        var now = 1_000L
        val misses = NotificationLogoMisses(max_entries = 2, clock = { now })
        misses.note("a.example", NOTIFICATION_LOGO_RETRY_TTL_MS)
        assertTrue(misses.is_blocked("a.example"))
        now += NOTIFICATION_LOGO_RETRY_TTL_MS
        assertFalse(misses.is_blocked("a.example"))
        misses.note("b.example", 10_000L)
        misses.note("c.example", 10_000L)
        misses.note("d.example", 10_000L)
        assertTrue(misses.is_blocked("d.example"))
        misses.forget("d.example")
        assertFalse(misses.is_blocked("d.example"))
        misses.note("e.example", 10_000L)
        misses.clear()
        assertFalse(misses.is_blocked("e.example"))
    }
}
