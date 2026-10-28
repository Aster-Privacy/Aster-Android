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
    fun long_lived_token_refreshes_once_it_is_a_day_old() {
        val access = token(issued_s, issued_s + 23 * day_s)
        assertFalse(access_token_needs_refresh(access, issued_s))
        assertFalse(access_token_needs_refresh(access, issued_s + day_s - 1))
        assertTrue(access_token_needs_refresh(access, issued_s + day_s))
        assertTrue(access_token_needs_refresh(access, issued_s + 2 * day_s))
        assertTrue(access_token_needs_refresh(access, issued_s + 19 * day_s))
        assertTrue(access_token_needs_refresh(access, issued_s + 24 * day_s))
    }

    @Test
    fun short_lived_token_still_refreshes_in_its_last_third() {
        val hour_s = 3_600L
        val access = token(issued_s, issued_s + hour_s)
        assertFalse(access_token_needs_refresh(access, issued_s + 30 * 60))
        assertTrue(access_token_needs_refresh(access, issued_s + 41 * 60))
    }

    @Test
    fun any_visit_inside_thirty_days_keeps_the_session_alive() {
        val access_lifetime_s = 23 * day_s
        val refresh_lifetime_s = 30 * day_s
        listOf(2L, 15L, 16L, 29L).forEach { gap_days ->
            var access_issued_s = issued_s
            var refresh_expires_s = issued_s + refresh_lifetime_s
            var now_s = issued_s
            repeat(12) {
                now_s += gap_days * day_s
                assertTrue("signed out with a $gap_days day gap", now_s < refresh_expires_s)
                val access = token(access_issued_s, access_issued_s + access_lifetime_s)
                if (access_token_needs_refresh(access, now_s)) {
                    access_issued_s = now_s
                    refresh_expires_s = now_s + refresh_lifetime_s
                }
            }
        }
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

        assertNull(refresh_if_expiring(access, refresher, now_s = issued_s + 2 * 3_600L))
        assertEquals(0, calls.get())

        assertEquals(RefreshOutcome.Success, refresh_if_expiring(access, refresher, now_s = issued_s + 2 * day_s))
        assertEquals(1, calls.get())
    }
}
