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

import org.astermail.android.api.auth.Argon2Params
import org.astermail.android.api.auth.GetSaltRequest
import org.astermail.android.api.auth.LoginRequest
import org.astermail.android.api.auth.LoginResponse
import org.astermail.android.api.auth.RefreshResponse
import org.astermail.android.api.auth.RegisterRequest
import org.astermail.android.api.auth.RegisterResponse
import org.astermail.android.api.auth.SaltResponse
import org.astermail.android.api.auth.UserInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthModelsTest {

    @Test
    fun `LoginRequest defaults`() {
        val request = LoginRequest(
            user_hash = "hash123",
            password_hash = "pass456",
        )
        assertEquals("hash123", request.user_hash)
        assertEquals("pass456", request.password_hash)
        assertNull(request.remember_me)
        assertNull(request.captcha_token)
        assertEquals("android", request.client_platform)
    }

    @Test
    fun `LoginRequest with all fields`() {
        val request = LoginRequest(
            user_hash = "hash123",
            password_hash = "pass456",
            remember_me = true,
            captcha_token = "cap_tok",
            client_platform = "ios",
        )
        assertEquals(true, request.remember_me)
        assertEquals("cap_tok", request.captcha_token)
        assertEquals("ios", request.client_platform)
    }

    @Test
    fun `LoginRequest copy modifies fields`() {
        val original = LoginRequest(user_hash = "h1", password_hash = "p1")
        val copied = original.copy(remember_me = false, captcha_token = "new_token")
        assertFalse(copied.remember_me!!)
        assertEquals("new_token", copied.captcha_token)
        assertEquals("h1", copied.user_hash)
    }

    @Test
    fun `LoginResponse defaults`() {
        val response = LoginResponse(
            user_id = "u1",
            username = "alice",
            email = "alice@astermail.org",
            csrf_token = "csrf_abc",
            encrypted_vault = "vault_enc",
            vault_nonce = "nonce_abc",
        )
        assertEquals("u1", response.user_id)
        assertEquals("alice", response.username)
        assertEquals("alice@astermail.org", response.email)
        assertEquals("csrf_abc", response.csrf_token)
        assertEquals("vault_enc", response.encrypted_vault)
        assertEquals("nonce_abc", response.vault_nonce)
        assertNull(response.access_token)
        assertFalse(response.needs_prekey_replenishment)
        assertNull(response.switch_token)
        assertNull(response.switch_token_expires_at)
        assertNull(response.is_suspended)
    }

    @Test
    fun `LoginResponse with all optional fields`() {
        val response = LoginResponse(
            user_id = "u1",
            username = "alice",
            email = "alice@astermail.org",
            csrf_token = "csrf_abc",
            encrypted_vault = "vault_enc",
            vault_nonce = "nonce_abc",
            access_token = "at_123",
            needs_prekey_replenishment = true,
            switch_token = "sw_tok",
            switch_token_expires_at = "2026-05-01T00:00:00Z",
            is_suspended = false,
        )
        assertEquals("at_123", response.access_token)
        assertTrue(response.needs_prekey_replenishment)
        assertEquals("sw_tok", response.switch_token)
        assertEquals("2026-05-01T00:00:00Z", response.switch_token_expires_at)
        assertFalse(response.is_suspended!!)
    }

    @Test
    fun `LoginResponse copy`() {
        val original = LoginResponse(
            user_id = "u1", username = "a", email = "a@b.c",
            csrf_token = "c", encrypted_vault = "v", vault_nonce = "n",
        )
        val updated = original.copy(access_token = "new_token", needs_prekey_replenishment = true)
        assertEquals("new_token", updated.access_token)
        assertTrue(updated.needs_prekey_replenishment)
        assertEquals("u1", updated.user_id)
    }

    @Test
    fun `GetSaltRequest stores user_hash`() {
        val request = GetSaltRequest(user_hash = "abc_hash")
        assertEquals("abc_hash", request.user_hash)
    }

    @Test
    fun `SaltResponse stores salt`() {
        val response = SaltResponse(salt = "random_salt_value")
        assertEquals("random_salt_value", response.salt)
    }

    @Test
    fun `SaltResponse copy`() {
        val original = SaltResponse(salt = "salt1")
        val copied = original.copy(salt = "salt2")
        assertEquals("salt2", copied.salt)
    }

    @Test
    fun `Argon2Params defaults`() {
        val params = Argon2Params()
        assertEquals(65536, params.memory)
        assertEquals(3, params.iterations)
        assertEquals(4, params.parallelism)
    }

    @Test
    fun `Argon2Params custom values`() {
        val params = Argon2Params(memory = 131072, iterations = 6, parallelism = 8)
        assertEquals(131072, params.memory)
        assertEquals(6, params.iterations)
        assertEquals(8, params.parallelism)
    }

    @Test
    fun `Argon2Params copy`() {
        val original = Argon2Params()
        val modified = original.copy(memory = 32768)
        assertEquals(32768, modified.memory)
        assertEquals(3, modified.iterations)
    }

    @Test
    fun `RegisterRequest required fields only`() {
        val params = Argon2Params()
        val request = RegisterRequest(
            username = "bob",
            user_hash = "uhash",
            password_hash = "phash",
            password_salt = "psalt",
            argon2_params = params,
            identity_key = "ik",
            signed_prekey = "spk",
            signed_prekey_signature = "spks",
            encrypted_vault = "ev",
            vault_nonce = "vn",
        )
        assertEquals("bob", request.username)
        assertEquals("uhash", request.user_hash)
        assertEquals("phash", request.password_hash)
        assertEquals("psalt", request.password_salt)
        assertEquals(params, request.argon2_params)
        assertEquals("ik", request.identity_key)
        assertEquals("spk", request.signed_prekey)
        assertEquals("spks", request.signed_prekey_signature)
        assertEquals("ev", request.encrypted_vault)
        assertEquals("vn", request.vault_nonce)
        assertNull(request.display_name)
        assertNull(request.profile_color)
        assertNull(request.email_domain)
        assertNull(request.remember_me)
        assertNull(request.captcha_token)
        assertNull(request.referral_code)
        assertEquals("android", request.client_platform)
    }

    @Test
    fun `RegisterRequest with all optional fields`() {
        val request = RegisterRequest(
            username = "bob",
            user_hash = "uhash",
            password_hash = "phash",
            password_salt = "psalt",
            argon2_params = Argon2Params(),
            identity_key = "ik",
            signed_prekey = "spk",
            signed_prekey_signature = "spks",
            encrypted_vault = "ev",
            vault_nonce = "vn",
            display_name = "Bob Smith",
            profile_color = "#FF0000",
            email_domain = "aster.cx",
            remember_me = true,
            captcha_token = "cap123",
            referral_code = "REF456",
            client_platform = "web",
        )
        assertEquals("Bob Smith", request.display_name)
        assertEquals("#FF0000", request.profile_color)
        assertEquals("aster.cx", request.email_domain)
        assertTrue(request.remember_me!!)
        assertEquals("cap123", request.captcha_token)
        assertEquals("REF456", request.referral_code)
        assertEquals("web", request.client_platform)
    }

    @Test
    fun `RegisterRequest copy`() {
        val original = RegisterRequest(
            username = "bob", user_hash = "u", password_hash = "p",
            password_salt = "s", argon2_params = Argon2Params(),
            identity_key = "ik", signed_prekey = "spk",
            signed_prekey_signature = "spks", encrypted_vault = "ev",
            vault_nonce = "vn",
        )
        val copied = original.copy(display_name = "Bobby", email_domain = "astermail.org")
        assertEquals("Bobby", copied.display_name)
        assertEquals("astermail.org", copied.email_domain)
        assertEquals("bob", copied.username)
    }

    @Test
    fun `RegisterResponse defaults`() {
        val response = RegisterResponse(
            user_id = "u1",
            username = "bob",
            email = "bob@astermail.org",
            csrf_token = "csrf_tok",
        )
        assertEquals("u1", response.user_id)
        assertEquals("bob", response.username)
        assertEquals("bob@astermail.org", response.email)
        assertEquals("csrf_tok", response.csrf_token)
        assertNull(response.access_token)
        assertFalse(response.recovery_email_required)
    }

    @Test
    fun `RegisterResponse with optional fields`() {
        val response = RegisterResponse(
            user_id = "u1",
            username = "bob",
            email = "bob@astermail.org",
            csrf_token = "csrf_tok",
            access_token = "at_789",
            recovery_email_required = true,
        )
        assertEquals("at_789", response.access_token)
        assertTrue(response.recovery_email_required)
    }

    @Test
    fun `RefreshResponse stores fields`() {
        val response = RefreshResponse(csrf_token = "new_csrf", access_token = "new_at")
        assertEquals("new_csrf", response.csrf_token)
        assertEquals("new_at", response.access_token)
    }

    @Test
    fun `RefreshResponse copy`() {
        val original = RefreshResponse(csrf_token = "c1", access_token = "a1")
        val copied = original.copy(csrf_token = "c2")
        assertEquals("c2", copied.csrf_token)
        assertEquals("a1", copied.access_token)
    }

    @Test
    fun `UserInfo defaults all nullable`() {
        val info = UserInfo(user_id = "u1")
        assertEquals("u1", info.user_id)
        assertNull(info.username)
        assertNull(info.email)
        assertNull(info.display_name)
        assertNull(info.profile_color)
        assertNull(info.profile_picture)
        assertNull(info.created_at)
        assertNull(info.identity_key)
    }

    @Test
    fun `UserInfo with all fields populated`() {
        val info = UserInfo(
            user_id = "u1",
            username = "alice",
            email = "alice@astermail.org",
            display_name = "Alice Wonderland",
            profile_color = "#00FF00",
            profile_picture = "https://cdn.astermail.org/pic.png",
            created_at = "2026-01-01T00:00:00Z",
            identity_key = "ik_alice",
        )
        assertEquals("alice", info.username)
        assertEquals("alice@astermail.org", info.email)
        assertEquals("Alice Wonderland", info.display_name)
        assertEquals("#00FF00", info.profile_color)
        assertEquals("https://cdn.astermail.org/pic.png", info.profile_picture)
        assertEquals("2026-01-01T00:00:00Z", info.created_at)
        assertEquals("ik_alice", info.identity_key)
    }

    @Test
    fun `UserInfo copy`() {
        val original = UserInfo(user_id = "u1", username = "alice")
        val copied = original.copy(display_name = "Alice", profile_color = "#FF0000")
        assertEquals("Alice", copied.display_name)
        assertEquals("#FF0000", copied.profile_color)
        assertEquals("alice", copied.username)
        assertEquals("u1", copied.user_id)
    }

    @Test
    fun `LoginRequest equality`() {
        val a = LoginRequest(user_hash = "h", password_hash = "p")
        val b = LoginRequest(user_hash = "h", password_hash = "p")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `Argon2Params equality`() {
        val a = Argon2Params()
        val b = Argon2Params(memory = 65536, iterations = 3, parallelism = 4)
        assertEquals(a, b)
    }
}
