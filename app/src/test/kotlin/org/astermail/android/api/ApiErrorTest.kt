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

package org.astermail.android.api

import org.astermail.android.api.autoforward.CreateForwardingRuleRequest
import org.astermail.android.api.autoforward.ForwardingRule
import org.astermail.android.api.autoforward.ForwardingRulesResponse
import org.astermail.android.api.autoforward.ToggleForwardingRuleRequest
import org.astermail.android.api.autoforward.UpdateForwardingRuleRequest
import org.astermail.android.api.developer.ApiKeyInfo
import org.astermail.android.api.developer.ApiKeyListResponse
import org.astermail.android.api.developer.CreateApiKeyRequest
import org.astermail.android.api.developer.CreateApiKeyResponse
import org.astermail.android.api.developer.WebhookInfo
import org.astermail.android.api.developer.WebhookListResponse
import org.astermail.android.api.ghost.CreateGhostAliasRequest
import org.astermail.android.api.ghost.CreateGhostAliasResponse
import org.astermail.android.api.ghost.GhostAlias
import org.astermail.android.api.ghost.GhostAliasListResponse
import org.astermail.android.api.keys.DiscoverKeyRequest
import org.astermail.android.api.keys.ExternalKeyInfo
import org.astermail.android.api.keys.PublicKeyResponse
import org.astermail.android.api.preferences.UserPreferences
import org.astermail.android.api.subscriptions.ProxyUnsubscribeRequest
import org.astermail.android.api.subscriptions.ProxyUnsubscribeResponse
import org.astermail.android.api.subscriptions.MailingListSubscription
import org.astermail.android.api.subscriptions.MailingListsResponse
import org.astermail.android.api.subscriptions.MailingListStats
import org.astermail.android.api.subscriptions.TrackSubscriptionRequest
import org.astermail.android.api.subscriptions.TrackSubscriptionResponse
import org.astermail.android.api.subscriptions.UnsubscribeRequest
import org.astermail.android.api.subscriptions.UnsubscribeResult
import org.astermail.android.api.user.Badge
import org.astermail.android.api.user.UpdateDisplayNameRequest
import org.astermail.android.api.user.UpdateProfilePictureRequest
import org.astermail.android.api.user.UpdateProfilePictureResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorTest {

    @Test
    fun `ApiError NetworkError message`() {
        val error = ApiError.NetworkError
        assertEquals("network error", error.message)
    }

    @Test
    fun `ApiError UnauthorizedError message`() {
        val error = ApiError.UnauthorizedError
        assertEquals("unauthorized", error.message)
    }

    @Test
    fun `ApiError ForbiddenError default message`() {
        val error = ApiError.ForbiddenError()
        assertEquals("forbidden", error.message)
        assertEquals("forbidden", error.detail)
    }

    @Test
    fun `ApiError ForbiddenError custom message`() {
        val error = ApiError.ForbiddenError("access denied to resource")
        assertEquals("access denied to resource", error.message)
        assertEquals("access denied to resource", error.detail)
    }

    @Test
    fun `ApiError NotFoundError message`() {
        val error = ApiError.NotFoundError
        assertEquals("not found", error.message)
    }

    @Test
    fun `ApiError ServerError stores code`() {
        val error = ApiError.ServerError(500)
        assertEquals(500, error.code)
        assertEquals("server error 500", error.message)

        val error_502 = ApiError.ServerError(502)
        assertEquals(502, error_502.code)
        assertEquals("server error 502", error_502.message)
    }

    @Test
    fun `ApiError ValidationError single message`() {
        val error = ApiError.ValidationError(listOf("field is required"))
        assertEquals(listOf("field is required"), error.messages)
        assertEquals("field is required", error.message)
    }

    @Test
    fun `ApiError ValidationError multiple messages`() {
        val error = ApiError.ValidationError(listOf("field1 required", "field2 too long"))
        assertEquals(2, error.messages.size)
        assertEquals("field1 required; field2 too long", error.message)
    }

    @Test
    fun `ApiError ValidationError empty messages`() {
        val error = ApiError.ValidationError(emptyList())
        assertTrue(error.messages.isEmpty())
        assertEquals("", error.message)
    }

    @Test
    fun `ApiError UnknownError stores detail`() {
        val error = ApiError.UnknownError("something went wrong")
        assertEquals("something went wrong", error.detail)
        assertEquals("something went wrong", error.message)
    }

    @Test
    fun `ApiError is Exception`() {
        val error: Exception = ApiError.NetworkError
        assertTrue(error is ApiError)
    }

    @Test
    fun `ApiError ForbiddenError equality`() {
        val a = ApiError.ForbiddenError("reason1")
        val b = ApiError.ForbiddenError("reason1")
        assertEquals(a, b)
    }

    @Test
    fun `ApiError ServerError equality`() {
        val a = ApiError.ServerError(503)
        val b = ApiError.ServerError(503)
        assertEquals(a, b)
    }

    @Test
    fun `GhostAlias defaults`() {
        val alias = GhostAlias()
        assertEquals("", alias.id)
        assertTrue(alias.enabled)
        assertNull(alias.expires_at)
        assertNull(alias.created_at)
    }

    @Test
    fun `GhostAlias with encrypted fields`() {
        val alias = GhostAlias(
            id = "ga1",
            encrypted_local_part = "enc",
            local_part_nonce = "nonce",
            domain = "astermail.org",
            is_enabled = false,
            expires_at = "2026-05-01T00:00:00Z",
            created_at = "2026-04-01T00:00:00Z",
            decrypted_address = "random123@astermail.org",
        )
        assertEquals("ga1", alias.id)
        assertEquals("random123@astermail.org", alias.address)
        assertFalse(alias.enabled)
        assertEquals("2026-05-01T00:00:00Z", alias.expires_at)
    }

    @Test
    fun `GhostAlias address fallback`() {
        val alias = GhostAlias(id = "ga1", domain = "astermail.org")
        assertEquals("ghost@astermail.org", alias.address)
    }

    @Test
    fun `GhostAlias copy`() {
        val original = GhostAlias(id = "ga1", decrypted_address = "a@b.c")
        val copied = original.copy(is_enabled = false)
        assertFalse(copied.enabled)
        assertEquals("ga1", copied.id)
    }

    @Test
    fun `GhostAliasListResponse defaults`() {
        val response = GhostAliasListResponse()
        assertTrue(response.aliases.isEmpty())
    }

    @Test
    fun `GhostAliasListResponse with aliases`() {
        val aliases = listOf(GhostAlias(id = "ga1"), GhostAlias(id = "ga2"))
        val response = GhostAliasListResponse(aliases = aliases)
        assertEquals(2, response.aliases.size)
    }

    @Test
    fun `CreateGhostAliasRequest fields`() {
        val request = CreateGhostAliasRequest(
            encrypted_local_part = "enc",
            local_part_nonce = "nonce",
            alias_address_hash = "hash",
            routing_address_hash = "rhash",
            domain = "astermail.org",
        )
        assertEquals("enc", request.encrypted_local_part)
        assertEquals("nonce", request.local_part_nonce)
        assertEquals("hash", request.alias_address_hash)
        assertEquals("rhash", request.routing_address_hash)
        assertEquals("astermail.org", request.domain)
        assertEquals(30, request.expires_in_days)
        assertNull(request.thread_token_hash)
    }

    @Test
    fun `CreateGhostAliasResponse defaults`() {
        val response = CreateGhostAliasResponse()
        assertEquals("", response.id)
        assertFalse(response.success)
        assertEquals("", response.expires_at)
        assertEquals("", response.grace_expires_at)
    }

    @Test
    fun `CreateGhostAliasResponse with values`() {
        val response = CreateGhostAliasResponse(
            id = "ga_new",
            success = true,
            expires_at = "2026-07-01T00:00:00Z",
            grace_expires_at = "2026-07-08T00:00:00Z",
        )
        assertEquals("ga_new", response.id)
        assertTrue(response.success)
        assertEquals("2026-07-01T00:00:00Z", response.expires_at)
    }

    @Test
    fun `ForwardingRule defaults`() {
        val rule = ForwardingRule()
        assertEquals("", rule.id)
        assertEquals("", rule.target_address)
        assertTrue(rule.enabled)
        assertTrue(rule.keep_copy)
        assertNull(rule.created_at)
    }

    @Test
    fun `ForwardingRule with all fields`() {
        val rule = ForwardingRule(
            id = "fr1",
            forward_to = listOf("backup@example.com"),
            is_enabled = false,
            keep_copy = false,
            created_at = "2026-04-01T00:00:00Z",
        )
        assertEquals("fr1", rule.id)
        assertEquals("backup@example.com", rule.target_address)
        assertFalse(rule.enabled)
        assertFalse(rule.keep_copy)
    }

    @Test
    fun `ForwardingRule copy`() {
        val original = ForwardingRule(id = "fr1", forward_to = listOf("a@b.c"))
        val copied = original.copy(is_enabled = false)
        assertFalse(copied.enabled)
        assertEquals("fr1", copied.id)
    }

    @Test
    fun `ForwardingRulesResponse defaults`() {
        val response = ForwardingRulesResponse()
        assertTrue(response.rules.isEmpty())
    }

    @Test
    fun `ForwardingRulesResponse with rules`() {
        val rules = listOf(ForwardingRule(id = "fr1"), ForwardingRule(id = "fr2"))
        val response = ForwardingRulesResponse(rules = rules)
        assertEquals(2, response.rules.size)
    }

    @Test
    fun `CreateForwardingRuleRequest defaults`() {
        val request = CreateForwardingRuleRequest(name = "fwd@example.com", forward_to = listOf("fwd@example.com"))
        assertEquals(listOf("fwd@example.com"), request.forward_to)
        assertTrue(request.keep_copy)
    }

    @Test
    fun `CreateForwardingRuleRequest no keep_copy`() {
        val request = CreateForwardingRuleRequest(name = "x@y.z", forward_to = listOf("x@y.z"), keep_copy = false)
        assertFalse(request.keep_copy)
    }

    @Test
    fun `UpdateForwardingRuleRequest defaults all null`() {
        val request = UpdateForwardingRuleRequest(id = "r1")
        assertNull(request.forward_to)
        assertNull(request.keep_copy)
    }

    @Test
    fun `UpdateForwardingRuleRequest with values`() {
        val request = UpdateForwardingRuleRequest(id = "r1", forward_to = listOf("new@addr.com"), keep_copy = false)
        assertEquals(listOf("new@addr.com"), request.forward_to)
        assertFalse(request.keep_copy!!)
    }

    @Test
    fun `ToggleForwardingRuleRequest stores fields`() {
        val request = ToggleForwardingRuleRequest(id = "fr1", is_enabled = false)
        assertEquals("fr1", request.id)
        assertFalse(request.is_enabled)
    }

    @Test
    fun `ApiKeyInfo defaults`() {
        val info = ApiKeyInfo()
        assertEquals("", info.id)
        assertEquals("", info.name_encrypted)
        assertEquals("", info.name_nonce)
        assertEquals("", info.prefix)
        assertEquals("", info.decrypted_name)
        assertNull(info.created_at)
        assertNull(info.last_used_at)
    }

    @Test
    fun `ApiKeyInfo with all fields`() {
        val info = ApiKeyInfo(
            id = "ak1",
            name_encrypted = "ZW5j",
            name_nonce = "bm9uY2U",
            prefix = "ast_",
            created_at = "2026-04-01T00:00:00Z",
            last_used_at = "2026-04-26T10:00:00Z",
        )
        assertEquals("ak1", info.id)
        assertEquals("ZW5j", info.name_encrypted)
        assertEquals("ast_", info.prefix)
    }

    @Test
    fun `ApiKeyListResponse defaults`() {
        val response = ApiKeyListResponse()
        assertTrue(response.api_keys.isEmpty())
    }

    @Test
    fun `ApiKeyListResponse with keys`() {
        val keys = listOf(ApiKeyInfo(id = "ak1"), ApiKeyInfo(id = "ak2"))
        val response = ApiKeyListResponse(api_keys = keys)
        assertEquals(2, response.api_keys.size)
    }

    @Test
    fun `CreateApiKeyRequest stores name`() {
        val request = CreateApiKeyRequest(name = "Production Key")
        assertEquals("Production Key", request.name)
    }

    @Test
    fun `CreateApiKeyResponse defaults`() {
        val response = CreateApiKeyResponse()
        assertEquals("", response.id)
        assertEquals("", response.key)
        assertEquals("", response.prefix)
        assertFalse(response.success)
    }

    @Test
    fun `CreateApiKeyResponse with values`() {
        val response = CreateApiKeyResponse(
            id = "ak_new",
            key = "ast_secretkey123",
            prefix = "ast_",
            success = true,
        )
        assertEquals("ak_new", response.id)
        assertEquals("ast_secretkey123", response.key)
        assertTrue(response.success)
    }

    @Test
    fun `WebhookInfo defaults`() {
        val info = WebhookInfo()
        assertEquals("", info.id)
        assertEquals("", info.url_encrypted)
        assertEquals("", info.url_nonce)
        assertEquals("", info.decrypted_url)
        assertTrue(info.events.isEmpty())
        assertTrue(info.enabled)
        assertNull(info.created_at)
    }

    @Test
    fun `WebhookInfo with all fields`() {
        val info = WebhookInfo(
            id = "wh1",
            url_encrypted = "ZW5j",
            url_nonce = "bm9uY2U",
            events = listOf("mail.received", "mail.sent"),
            enabled = false,
            created_at = "2026-04-01T00:00:00Z",
        )
        assertEquals("wh1", info.id)
        assertEquals("ZW5j", info.url_encrypted)
        assertEquals(2, info.events.size)
        assertFalse(info.enabled)
    }

    @Test
    fun `WebhookInfo with empty events`() {
        val info = WebhookInfo(id = "wh1", url_encrypted = "ZW5j", events = emptyList())
        assertTrue(info.events.isEmpty())
    }

    @Test
    fun `WebhookListResponse defaults`() {
        val response = WebhookListResponse()
        assertTrue(response.webhooks.isEmpty())
    }

    @Test
    fun `WebhookListResponse with webhooks`() {
        val webhooks = listOf(WebhookInfo(id = "wh1"), WebhookInfo(id = "wh2"))
        val response = WebhookListResponse(webhooks = webhooks)
        assertEquals(2, response.webhooks.size)
    }

    @Test
    fun `PublicKeyResponse stores fields`() {
        val response = PublicKeyResponse(
            username = "alice",
            public_key = "pk_abc123",
        )
        assertEquals("alice", response.username)
        assertEquals("pk_abc123", response.public_key)
    }

    @Test
    fun `ExternalKeyInfo defaults`() {
        val info = ExternalKeyInfo(email = "ext@example.com", found = false)
        assertEquals("ext@example.com", info.email)
        assertFalse(info.found)
        assertNull(info.public_key)
        assertNull(info.fingerprint)
        assertNull(info.source)
        assertNull(info.expires_at)
    }

    @Test
    fun `ExternalKeyInfo found with all fields`() {
        val info = ExternalKeyInfo(
            email = "ext@example.com",
            found = true,
            public_key = "pk_ext",
            fingerprint = "fp_abc123",
            source = "wkd",
            expires_at = "2027-01-01T00:00:00Z",
        )
        assertTrue(info.found)
        assertEquals("pk_ext", info.public_key)
        assertEquals("fp_abc123", info.fingerprint)
        assertEquals("wkd", info.source)
    }

    @Test
    fun `DiscoverKeyRequest stores email`() {
        val request = DiscoverKeyRequest(email = "test@example.com")
        assertEquals("test@example.com", request.email)
    }

    @Test
    fun `UserPreferences defaults`() {
        val prefs = UserPreferences()
        assertEquals("en", prefs.language)
        assertEquals("system", prefs.theme)
        assertEquals("auto", prefs.time_zone)
        assertEquals("MM/DD/YYYY", prefs.date_format)
        assertEquals("auto", prefs.time_format)
        assertTrue(prefs.push_notifications)
        assertTrue(prefs.sound)
        assertTrue(prefs.vibrate)
        assertTrue(prefs.notify_new_email)
        assertTrue(prefs.notify_replies)
        assertTrue(prefs.notify_mentions)
        assertFalse(prefs.quiet_hours_enabled)
        assertEquals("22:00", prefs.quiet_hours_start)
        assertEquals("07:00", prefs.quiet_hours_end)
        assertEquals("default", prefs.font_size_scale)
        assertFalse(prefs.high_contrast)
        assertFalse(prefs.reduce_transparency)
        assertFalse(prefs.underline_links)
        assertEquals("none", prefs.color_vision)
        assertFalse(prefs.dyslexia_font)
        assertFalse(prefs.text_spacing)
        assertFalse(prefs.reduce_motion)
        assertFalse(prefs.compact_mode)
        assertEquals("1_second", prefs.mark_as_read)
        assertEquals("reply", prefs.default_reply_behavior)
        assertTrue(prefs.block_external_images)
        assertTrue(prefs.block_tracking_pixels)
        assertTrue(prefs.block_tracking_links)
        assertTrue(prefs.auto_save_recent_recipients)
        assertTrue(prefs.undo_send_enabled)
        assertEquals(10, prefs.undo_send_seconds)
        assertFalse(prefs.confirm_delete)
        assertFalse(prefs.confirm_archive)
        assertFalse(prefs.confirm_spam)
        assertTrue(prefs.haptic_enabled)
        assertTrue(prefs.block_trackers)
        assertEquals("never", prefs.load_remote_images)
        assertFalse(prefs.send_read_receipts)
        assertTrue(prefs.warn_suspicious_links)
        assertTrue(prefs.strip_exif)
        assertFalse(prefs.ghost_mode)
        assertFalse(prefs.dev_mode)
        assertFalse(prefs.show_raw_headers)
        assertFalse(prefs.allow_insecure)
        assertFalse(prefs.verbose_logs)
        assertTrue(prefs.show_profile_pictures)
        assertTrue(prefs.show_email_preview)
        assertTrue(prefs.keyboard_shortcuts_enabled)
    }

    @Test
    fun `UserPreferences copy`() {
        val original = UserPreferences()
        val copied = original.copy(
            language = "de",
            theme = "dark",
            compact_mode = true,
            dev_mode = true,
            undo_send_seconds = 30,
        )
        assertEquals("de", copied.language)
        assertEquals("dark", copied.theme)
        assertTrue(copied.compact_mode)
        assertTrue(copied.dev_mode)
        assertEquals(30, copied.undo_send_seconds)
        assertTrue(copied.push_notifications)
    }

    @Test
    fun `UserPreferences equality`() {
        val a = UserPreferences()
        val b = UserPreferences()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `MailingListSubscription defaults`() {
        val sub = MailingListSubscription(id = "sub1", sender_email = "news@example.com")
        assertEquals("sub1", sub.id)
        assertEquals("news@example.com", sub.sender_email)
        assertEquals("", sub.sender_name)
        assertEquals("", sub.domain)
        assertEquals(0, sub.email_count)
        assertEquals("", sub.last_received)
        assertNull(sub.unsubscribe_link)
        assertFalse(sub.has_list_unsubscribe)
        assertEquals("unknown", sub.category)
        assertEquals("safe", sub.risk_level)
        assertEquals("active", sub.status)
    }

    @Test
    fun `MailingListSubscription with all fields`() {
        val sub = MailingListSubscription(
            id = "sub1",
            sender_email = "news@example.com",
            sender_name = "Example Newsletter",
            domain = "example.com",
            email_count = 42,
            last_received = "2026-04-25T00:00:00Z",
            unsubscribe_link = "https://example.com/unsub",
            has_list_unsubscribe = true,
            category = "newsletter",
            risk_level = "safe",
            status = "active",
        )
        assertEquals("Example Newsletter", sub.sender_name)
        assertEquals(42, sub.email_count)
        assertTrue(sub.has_list_unsubscribe)
        assertEquals("newsletter", sub.category)
    }

    @Test
    fun `MailingListSubscription copy`() {
        val original = MailingListSubscription(id = "s1", sender_email = "a@b.c")
        val copied = original.copy(status = "unsubscribed", email_count = 10)
        assertEquals("unsubscribed", copied.status)
        assertEquals(10, copied.email_count)
    }

    @Test
    fun `MailingListsResponse defaults`() {
        val response = MailingListsResponse()
        assertTrue(response.subscriptions.isEmpty())
        assertEquals(0, response.total)
        assertFalse(response.has_more)
    }

    @Test
    fun `MailingListsResponse with data`() {
        val subs = listOf(
            MailingListSubscription(id = "s1", sender_email = "a@b.c"),
            MailingListSubscription(id = "s2", sender_email = "d@e.f"),
        )
        val response = MailingListsResponse(subscriptions = subs, total = 50, has_more = true)
        assertEquals(2, response.subscriptions.size)
        assertEquals(50, response.total)
        assertTrue(response.has_more)
    }

    @Test
    fun `MailingListStats defaults`() {
        val stats = MailingListStats()
        assertEquals(0, stats.total_subscriptions)
        assertEquals(0, stats.active)
        assertEquals(0, stats.unsubscribed)
        assertEquals(0, stats.newsletters)
        assertEquals(0, stats.marketing)
        assertEquals(0, stats.social)
        assertEquals(0, stats.total_emails_from_subscriptions)
    }

    @Test
    fun `MailingListStats with values`() {
        val stats = MailingListStats(
            total_subscriptions = 100,
            active = 80,
            unsubscribed = 20,
            newsletters = 30,
            marketing = 40,
            social = 10,
            total_emails_from_subscriptions = 5000,
        )
        assertEquals(100, stats.total_subscriptions)
        assertEquals(80, stats.active)
        assertEquals(5000, stats.total_emails_from_subscriptions)
    }

    @Test
    fun `ProxyUnsubscribeRequest stores fields`() {
        val request = ProxyUnsubscribeRequest(method = "http_get")
        assertEquals("http_get", request.method)
        assertNull(request.url)
        assertNull(request.mailto_address)
        assertNull(request.list_unsubscribe_post)
    }

    @Test
    fun `ProxyUnsubscribeRequest with all fields`() {
        val request = ProxyUnsubscribeRequest(
            method = "http_post",
            url = "https://example.com/unsub",
            mailto_address = "unsub@example.com",
            list_unsubscribe_post = "List-Unsubscribe=One-Click",
        )
        assertEquals("http_post", request.method)
        assertEquals("https://example.com/unsub", request.url)
        assertEquals("unsub@example.com", request.mailto_address)
    }

    @Test
    fun `ProxyUnsubscribeResponse defaults`() {
        val response = ProxyUnsubscribeResponse()
        assertFalse(response.success)
        assertEquals("", response.method)
        assertNull(response.message)
    }

    @Test
    fun `ProxyUnsubscribeResponse with values`() {
        val response = ProxyUnsubscribeResponse(
            success = true,
            method = "http_post",
            message = "Unsubscribed successfully",
        )
        assertTrue(response.success)
        assertEquals("http_post", response.method)
    }

    @Test
    fun `UnsubscribeRequest defaults`() {
        val request = UnsubscribeRequest(subscription_id = "sub1")
        assertEquals("sub1", request.subscription_id)
        assertEquals("auto", request.method)
    }

    @Test
    fun `UnsubscribeRequest with method`() {
        val request = UnsubscribeRequest(subscription_id = "sub1", method = "mailto")
        assertEquals("mailto", request.method)
    }

    @Test
    fun `UnsubscribeResult defaults`() {
        val response = UnsubscribeResult()
        assertFalse(response.success)
        assertEquals("", response.subscription_id)
        assertNull(response.message)
    }

    @Test
    fun `UnsubscribeResult with values`() {
        val response = UnsubscribeResult(
            success = true,
            subscription_id = "sub1",
            message = "done",
        )
        assertTrue(response.success)
        assertEquals("sub1", response.subscription_id)
    }

    @Test
    fun `TrackSubscriptionRequest required fields`() {
        val request = TrackSubscriptionRequest(sender_email = "news@example.com")
        assertEquals("news@example.com", request.sender_email)
        assertNull(request.sender_name)
        assertNull(request.unsubscribe_link)
        assertNull(request.list_unsubscribe_header)
        assertNull(request.category)
    }

    @Test
    fun `TrackSubscriptionRequest with all fields`() {
        val request = TrackSubscriptionRequest(
            sender_email = "news@example.com",
            sender_name = "Newsletter",
            unsubscribe_link = "https://example.com/unsub",
            list_unsubscribe_header = "<mailto:unsub@example.com>",
            category = "newsletter",
        )
        assertEquals("Newsletter", request.sender_name)
        assertEquals("newsletter", request.category)
    }

    @Test
    fun `TrackSubscriptionResponse defaults`() {
        val response = TrackSubscriptionResponse()
        assertFalse(response.success)
        assertEquals("", response.subscription_id)
        assertFalse(response.is_new)
    }

    @Test
    fun `TrackSubscriptionResponse with values`() {
        val response = TrackSubscriptionResponse(
            success = true,
            subscription_id = "sub_new",
            is_new = true,
        )
        assertTrue(response.success)
        assertTrue(response.is_new)
    }

    @Test
    fun `UpdateDisplayNameRequest stores field`() {
        val request = UpdateDisplayNameRequest(display_name = "Alice Smith")
        assertEquals("Alice Smith", request.display_name)
    }

    @Test
    fun `UpdateProfilePictureRequest defaults`() {
        val request = UpdateProfilePictureRequest()
        assertNull(request.profile_picture)
    }

    @Test
    fun `UpdateProfilePictureRequest with value`() {
        val request = UpdateProfilePictureRequest(profile_picture = "data:image/png;base64,abc")
        assertEquals("data:image/png;base64,abc", request.profile_picture)
    }

    @Test
    fun `UpdateProfilePictureResponse stores fields`() {
        val response = UpdateProfilePictureResponse(success = true, profile_picture = "url")
        assertTrue(response.success)
        assertEquals("url", response.profile_picture)
    }

    @Test
    fun `Badge stores all fields`() {
        val badge = Badge(
            slug = "early_adopter",
            display_name = "Early Adopter",
            description = "Joined during beta",
            icon = "star",
            color = "#FFD700",
            granted_at = "2026-01-01T00:00:00Z",
        )
        assertEquals("early_adopter", badge.slug)
        assertEquals("Early Adopter", badge.display_name)
        assertEquals("Joined during beta", badge.description)
        assertEquals("star", badge.icon)
        assertEquals("#FFD700", badge.color)
        assertEquals("2026-01-01T00:00:00Z", badge.granted_at)
    }

    @Test
    fun `Badge with null description`() {
        val badge = Badge(
            slug = "s", display_name = "d",
            icon = "i", color = "c", granted_at = "g",
        )
        assertNull(badge.description)
    }

    @Test
    fun `Badge copy`() {
        val original = Badge(
            slug = "s", display_name = "d",
            icon = "i", color = "c", granted_at = "g",
        )
        val copied = original.copy(description = "new desc")
        assertEquals("new desc", copied.description)
        assertEquals("s", copied.slug)
    }

    @Test
    fun `Badge equality`() {
        val a = Badge(slug = "s", display_name = "d", icon = "i", color = "c", granted_at = "g")
        val b = Badge(slug = "s", display_name = "d", icon = "i", color = "c", granted_at = "g")
        assertEquals(a, b)
    }
}
