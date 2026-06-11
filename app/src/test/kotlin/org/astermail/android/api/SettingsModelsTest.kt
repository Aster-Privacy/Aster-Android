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

import org.astermail.android.api.settings.AliasInfo
import org.astermail.android.api.settings.AliasListResponse
import org.astermail.android.api.settings.BlockedSenderInfo
import org.astermail.android.api.settings.BlockedSendersResponse
import org.astermail.android.api.settings.ChangePasswordRequest
import org.astermail.android.api.settings.ChangePasswordResponse
import org.astermail.android.api.settings.FeedbackRequest
import org.astermail.android.api.settings.RecoveryKeyResponse
import org.astermail.android.api.settings.SecurityStatusResponse
import org.astermail.android.api.settings.SessionInfo
import org.astermail.android.api.settings.SessionListResponse
import org.astermail.android.api.settings.StorageOverview
import org.astermail.android.api.settings.SubscriptionInfo
import org.astermail.android.api.settings.TotpStatusResponse
import org.astermail.android.api.settings.UpdateProfileColorRequest
import org.astermail.android.api.settings.UpdateProfileColorResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsModelsTest {

    @Test
    fun `SessionInfo defaults`() {
        val session = SessionInfo(id = "s1")
        assertEquals("s1", session.id)
        assertNull(session.ip_address)
        assertNull(session.user_agent)
        assertNull(session.created_at)
        assertNull(session.last_active_at)
        assertFalse(session.is_current)
    }

    @Test
    fun `SessionInfo with all fields`() {
        val session = SessionInfo(
            id = "s1",
            ip_address = "192.168.1.1",
            user_agent = "Mozilla/5.0",
            created_at = "2026-04-01T00:00:00Z",
            last_active_at = "2026-04-26T10:00:00Z",
            is_current = true,
        )
        assertEquals("192.168.1.1", session.ip_address)
        assertEquals("Mozilla/5.0", session.user_agent)
        assertEquals("2026-04-01T00:00:00Z", session.created_at)
        assertEquals("2026-04-26T10:00:00Z", session.last_active_at)
        assertTrue(session.is_current)
    }

    @Test
    fun `SessionInfo copy`() {
        val original = SessionInfo(id = "s1", is_current = false)
        val copied = original.copy(is_current = true, ip_address = "10.0.0.1")
        assertTrue(copied.is_current)
        assertEquals("10.0.0.1", copied.ip_address)
        assertEquals("s1", copied.id)
    }

    @Test
    fun `SessionListResponse defaults`() {
        val response = SessionListResponse()
        assertTrue(response.sessions.isEmpty())
    }

    @Test
    fun `SessionListResponse with sessions`() {
        val sessions = listOf(
            SessionInfo(id = "s1", is_current = true),
            SessionInfo(id = "s2"),
        )
        val response = SessionListResponse(sessions = sessions)
        assertEquals(2, response.sessions.size)
        assertTrue(response.sessions[0].is_current)
    }

    @Test
    fun `ChangePasswordRequest stores fields`() {
        val request = ChangePasswordRequest(
            current_password_hash = "old_hash",
            new_password_hash = "new_hash",
            new_password_salt = "new_salt",
            new_encrypted_vault = "new_vault",
            new_vault_nonce = "new_nonce",
        )
        assertEquals("old_hash", request.current_password_hash)
        assertEquals("new_hash", request.new_password_hash)
        assertEquals("new_salt", request.new_password_salt)
        assertEquals("new_vault", request.new_encrypted_vault)
        assertEquals("new_nonce", request.new_vault_nonce)
    }

    @Test
    fun `ChangePasswordRequest copy`() {
        val original = ChangePasswordRequest(
            current_password_hash = "o", new_password_hash = "n",
            new_password_salt = "s", new_encrypted_vault = "v",
            new_vault_nonce = "vn",
        )
        val copied = original.copy(new_password_hash = "updated")
        assertEquals("updated", copied.new_password_hash)
        assertEquals("o", copied.current_password_hash)
    }

    @Test
    fun `ChangePasswordResponse defaults`() {
        val response = ChangePasswordResponse()
        assertTrue(response.success)
        assertNull(response.csrf_token)
    }

    @Test
    fun `ChangePasswordResponse with csrf`() {
        val response = ChangePasswordResponse(success = true, csrf_token = "new_csrf")
        assertTrue(response.success)
        assertEquals("new_csrf", response.csrf_token)
    }

    @Test
    fun `UpdateProfileColorRequest stores color`() {
        val request = UpdateProfileColorRequest(profile_color = "#FF5733")
        assertEquals("#FF5733", request.profile_color)
    }

    @Test
    fun `UpdateProfileColorResponse defaults`() {
        val response = UpdateProfileColorResponse()
        assertTrue(response.success)
        assertNull(response.profile_color)
    }

    @Test
    fun `UpdateProfileColorResponse with color`() {
        val response = UpdateProfileColorResponse(success = true, profile_color = "#00FF00")
        assertEquals("#00FF00", response.profile_color)
    }

    @Test
    fun `SecurityStatusResponse defaults`() {
        val status = SecurityStatusResponse()
        assertFalse(status.totp_enabled)
        assertFalse(status.recovery_email_set)
        assertFalse(status.recovery_email_verified)
        assertNull(status.password_last_changed)
        assertEquals(0, status.hardware_keys_count)
    }

    @Test
    fun `SecurityStatusResponse with all fields`() {
        val status = SecurityStatusResponse(
            totp_enabled = true,
            recovery_email_set = true,
            recovery_email_verified = true,
            password_last_changed = "2026-04-01T00:00:00Z",
            hardware_keys_count = 2,
        )
        assertTrue(status.totp_enabled)
        assertTrue(status.recovery_email_set)
        assertTrue(status.recovery_email_verified)
        assertEquals("2026-04-01T00:00:00Z", status.password_last_changed)
        assertEquals(2, status.hardware_keys_count)
    }

    @Test
    fun `SecurityStatusResponse copy`() {
        val original = SecurityStatusResponse()
        val copied = original.copy(totp_enabled = true, hardware_keys_count = 1)
        assertTrue(copied.totp_enabled)
        assertEquals(1, copied.hardware_keys_count)
        assertFalse(copied.recovery_email_set)
    }

    @Test
    fun `TotpStatusResponse defaults`() {
        val status = TotpStatusResponse()
        assertFalse(status.enabled)
        assertEquals(0, status.backup_codes_remaining)
    }

    @Test
    fun `TotpStatusResponse with values`() {
        val status = TotpStatusResponse(enabled = true, backup_codes_remaining = 8)
        assertTrue(status.enabled)
        assertEquals(8, status.backup_codes_remaining)
    }

    @Test
    fun `BlockedSenderInfo defaults`() {
        val info = BlockedSenderInfo(address = "spam@evil.com")
        assertEquals("spam@evil.com", info.address)
        assertEquals(0, info.blocked_count)
        assertNull(info.created_at)
    }

    @Test
    fun `BlockedSenderInfo with all fields`() {
        val info = BlockedSenderInfo(
            address = "spam@evil.com",
            blocked_count = 15,
            created_at = "2026-03-01T00:00:00Z",
        )
        assertEquals(15, info.blocked_count)
        assertEquals("2026-03-01T00:00:00Z", info.created_at)
    }

    @Test
    fun `BlockedSendersResponse defaults`() {
        val response = BlockedSendersResponse()
        assertTrue(response.blocked_senders.isEmpty())
    }

    @Test
    fun `BlockedSendersResponse with items`() {
        val senders = listOf(
            BlockedSenderInfo(address = "a@b.c"),
            BlockedSenderInfo(address = "d@e.f", blocked_count = 5),
        )
        val response = BlockedSendersResponse(blocked_senders = senders)
        assertEquals(2, response.blocked_senders.size)
        assertEquals(5, response.blocked_senders[1].blocked_count)
    }

    @Test
    fun `AliasInfo defaults`() {
        val alias = AliasInfo(id = "a1")
        assertEquals("a1", alias.id)
        assertEquals("", alias.encrypted_local_part)
        assertEquals("", alias.local_part_nonce)
        assertNull(alias.encrypted_display_name)
        assertNull(alias.display_name_nonce)
        assertEquals("", alias.alias_address_hash)
        assertEquals("", alias.domain)
        assertTrue(alias.is_enabled)
        assertFalse(alias.is_random)
        assertNull(alias.profile_picture)
        assertEquals("", alias.created_at)
        assertEquals("", alias.updated_at)
    }

    @Test
    fun `AliasInfo with all fields`() {
        val alias = AliasInfo(
            id = "a1",
            encrypted_local_part = "alice",
            local_part_nonce = "lpn",
            encrypted_display_name = "Alice Smith",
            display_name_nonce = "dnn",
            alias_address_hash = "aah_123",
            domain = "astermail.org",
            is_enabled = true,
            is_random = true,
            profile_picture = "https://cdn.astermail.org/pic.png",
            created_at = "2026-01-01T00:00:00Z",
            updated_at = "2026-04-26T00:00:00Z",
        )
        assertEquals("alice", alias.encrypted_local_part)
        assertEquals("Alice Smith", alias.encrypted_display_name)
        assertTrue(alias.is_random)
        assertEquals("https://cdn.astermail.org/pic.png", alias.profile_picture)
    }

    @Test
    fun `AliasInfo address computed property with domain and local_part`() {
        val alias = AliasInfo(
            id = "a1",
            encrypted_local_part = "myalias",
            domain = "astermail.org",
        )
        assertEquals("myalias@astermail.org", alias.address)
    }

    @Test
    fun `AliasInfo address falls back to id when domain blank`() {
        val alias = AliasInfo(id = "a1", encrypted_local_part = "myalias", domain = "")
        assertEquals("a1", alias.address)
    }

    @Test
    fun `AliasInfo address falls back to id when local_part blank`() {
        val alias = AliasInfo(id = "a1", encrypted_local_part = "", domain = "astermail.org")
        assertEquals("a1", alias.address)
    }

    @Test
    fun `AliasInfo address falls back to id when both blank`() {
        val alias = AliasInfo(id = "a1", encrypted_local_part = "", domain = "")
        assertEquals("a1", alias.address)
    }

    @Test
    fun `AliasInfo address with whitespace domain`() {
        val alias = AliasInfo(id = "a1", encrypted_local_part = "user", domain = "   ")
        assertEquals("a1", alias.address)
    }

    @Test
    fun `AliasInfo copy`() {
        val original = AliasInfo(id = "a1", domain = "astermail.org", encrypted_local_part = "user")
        val copied = original.copy(is_enabled = false, is_random = true)
        assertFalse(copied.is_enabled)
        assertTrue(copied.is_random)
        assertEquals("user@astermail.org", copied.address)
    }

    @Test
    fun `AliasListResponse defaults`() {
        val response = AliasListResponse()
        assertTrue(response.aliases.isEmpty())
        assertEquals(0L, response.total)
        assertFalse(response.has_more)
        assertEquals(0, response.max_aliases)
    }

    @Test
    fun `AliasListResponse with data`() {
        val aliases = listOf(
            AliasInfo(id = "a1", encrypted_local_part = "user1", domain = "astermail.org"),
            AliasInfo(id = "a2", encrypted_local_part = "user2", domain = "aster.cx"),
        )
        val response = AliasListResponse(
            aliases = aliases,
            total = 10,
            has_more = true,
            max_aliases = 50,
        )
        assertEquals(2, response.aliases.size)
        assertEquals(10L, response.total)
        assertTrue(response.has_more)
        assertEquals(50, response.max_aliases)
    }

    @Test
    fun `StorageOverview defaults`() {
        val storage = StorageOverview()
        assertEquals(0L, storage.used_bytes)
        assertEquals(0L, storage.total_bytes)
        assertEquals(0.0, storage.percentage_used, 0.001)
        assertFalse(storage.is_over_limit)
        assertEquals(0L, storage.addon_bytes)
    }

    @Test
    fun `StorageOverview with values`() {
        val storage = StorageOverview(
            used_bytes = 536_870_912L,
            total_bytes = 1_073_741_824L,
            percentage_used = 50.0,
            is_over_limit = false,
            addon_bytes = 1_073_741_824L,
        )
        assertEquals(536_870_912L, storage.used_bytes)
        assertEquals(1_073_741_824L, storage.total_bytes)
        assertEquals(50.0, storage.percentage_used, 0.001)
        assertFalse(storage.is_over_limit)
        assertEquals(1_073_741_824L, storage.addon_bytes)
    }

    @Test
    fun `StorageOverview over limit`() {
        val storage = StorageOverview(
            used_bytes = 2_000_000_000L,
            total_bytes = 1_073_741_824L,
            percentage_used = 186.26,
            is_over_limit = true,
        )
        assertTrue(storage.is_over_limit)
        assertTrue(storage.percentage_used > 100.0)
    }

    @Test
    fun `StorageOverview copy`() {
        val original = StorageOverview(used_bytes = 100L, total_bytes = 1000L)
        val copied = original.copy(used_bytes = 500L, percentage_used = 50.0)
        assertEquals(500L, copied.used_bytes)
        assertEquals(50.0, copied.percentage_used, 0.001)
        assertEquals(1000L, copied.total_bytes)
    }

    @Test
    fun `SubscriptionInfo defaults`() {
        val sub = SubscriptionInfo()
        assertNull(sub.plan_name)
        assertNull(sub.status)
        assertEquals(0, sub.amount)
        assertEquals("usd", sub.currency)
        assertNull(sub.interval)
        assertNull(sub.current_period_end)
    }

    @Test
    fun `SubscriptionInfo with paid plan`() {
        val sub = SubscriptionInfo(
            plan_name = "Supernova",
            status = "active",
            amount = 1999,
            currency = "eur",
            interval = "month",
            current_period_end = "2026-05-26T00:00:00Z",
        )
        assertEquals("Supernova", sub.plan_name)
        assertEquals("active", sub.status)
        assertEquals(1999, sub.amount)
        assertEquals("eur", sub.currency)
        assertEquals("month", sub.interval)
        assertEquals("2026-05-26T00:00:00Z", sub.current_period_end)
    }

    @Test
    fun `SubscriptionInfo copy`() {
        val original = SubscriptionInfo(plan_name = "Nebula", amount = 499)
        val copied = original.copy(status = "canceled")
        assertEquals("Nebula", copied.plan_name)
        assertEquals("canceled", copied.status)
        assertEquals(499, copied.amount)
    }

    @Test
    fun `FeedbackRequest defaults`() {
        val request = FeedbackRequest(category = "bug", message = "Something broke")
        assertEquals("bug", request.category)
        assertEquals("Something broke", request.message)
        assertEquals("android", request.platform)
    }

    @Test
    fun `FeedbackRequest with custom platform`() {
        val request = FeedbackRequest(category = "feature", message = "Please add X", platform = "ios")
        assertEquals("ios", request.platform)
    }

    @Test
    fun `FeedbackRequest copy`() {
        val original = FeedbackRequest(category = "bug", message = "msg")
        val copied = original.copy(category = "feature")
        assertEquals("feature", copied.category)
        assertEquals("msg", copied.message)
    }

    @Test
    fun `RecoveryKeyResponse defaults`() {
        val response = RecoveryKeyResponse()
        assertNull(response.recovery_key)
    }

    @Test
    fun `RecoveryKeyResponse with key`() {
        val response = RecoveryKeyResponse(recovery_key = "rk_abc_123_def")
        assertEquals("rk_abc_123_def", response.recovery_key)
    }

    @Test
    fun `SessionInfo equality`() {
        val a = SessionInfo(id = "s1", is_current = true)
        val b = SessionInfo(id = "s1", is_current = true)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `AliasInfo equality`() {
        val a = AliasInfo(id = "a1", domain = "astermail.org", encrypted_local_part = "user")
        val b = AliasInfo(id = "a1", domain = "astermail.org", encrypted_local_part = "user")
        assertEquals(a, b)
    }
}
