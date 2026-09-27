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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.astermail.android.api.auth.RefreshOutcome
import org.astermail.android.api.auth.SessionRefresher
import org.astermail.android.api.auth.access_token_needs_refresh
import org.astermail.android.api.auth.read_token_lifetime
import org.astermail.android.api.auth.refresh_if_expiring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class token_expiry_test {

    private val day_s = 86_400L
    private val issued_s = 1_780_000_000L

    private fun jwt(payload: String): String {
        val encoder = java.util.Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val body = encoder.encodeToString(payload.toByteArray())
        return "$header.$body.signature"
    }

    private fun token(iat: Long, exp: Long): String =
        jwt("""{"sub":"user","iat":$iat,"exp":$exp,"token_type":"access"}""")

    @Test
    fun reads_issued_and_expiry_claims() {
        val lifetime = read_token_lifetime(token(issued_s, issued_s + 30 * day_s))
        assertEquals(issued_s, lifetime?.issued_at_s)
        assertEquals(issued_s + 30 * day_s, lifetime?.expires_at_s)
    }

    @Test
    fun malformed_tokens_have_no_lifetime() {
        assertNull(read_token_lifetime(null))
        assertNull(read_token_lifetime(""))
        assertNull(read_token_lifetime("not-a-jwt"))
        assertNull(read_token_lifetime("a.%%%.c"))
        assertNull(read_token_lifetime(jwt("""{"sub":"user"}""")))
        assertNull(read_token_lifetime(token(issued_s, issued_s)))
    }

    @Test
    fun long_lived_token_refreshes_only_in_its_last_third() {
        val access = token(issued_s, issued_s + 30 * day_s)
        assertFalse(access_token_needs_refresh(access, issued_s + day_s))
        assertFalse(access_token_needs_refresh(access, issued_s + 19 * day_s))
        assertTrue(access_token_needs_refresh(access, issued_s + 21 * day_s))
        assertTrue(access_token_needs_refresh(access, issued_s + 31 * day_s))
    }

    @Test
    fun unreadable_token_never_triggers_a_refresh() {
        assertFalse(access_token_needs_refresh(null, issued_s))
        assertFalse(access_token_needs_refresh("opaque", issued_s))
    }

    @Test
    fun refresh_if_expiring_rotates_before_both_tokens_lapse() = runBlocking {
        val calls = AtomicInteger(0)
        val refresher = SessionRefresher(
            read_refresh_token = { "refresh_0" },
            perform_refresh = {
                calls.incrementAndGet()
                RefreshOutcome.Success
            },
            notify_scope = CoroutineScope(Dispatchers.Unconfined),
        )
        val access = token(issued_s, issued_s + 30 * day_s)

        assertNull(refresh_if_expiring(access, refresher, now_s = issued_s + 2 * day_s))
        assertEquals(0, calls.get())

        assertEquals(RefreshOutcome.Success, refresh_if_expiring(access, refresher, now_s = issued_s + 25 * day_s))
        assertEquals(1, calls.get())
    }
}
