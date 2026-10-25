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


package org.astermail.android.auth

import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.astermail.android.api.ApiError
import org.astermail.android.api.auth.RefreshOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTokenOwnerTest {

    private fun jwt(sub: String, nonce: String = "n"): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("{\"alg\":\"none\"}".toByteArray())
        val body = encoder.encodeToString("{\"sub\":\"$sub\",\"jti\":\"$nonce\"}".toByteArray())
        return "$header.$body.sig"
    }

    private data class SavedForAccount(
        val account_id: String,
        val expected_refresh: String?,
        val access: String,
        val refresh: String,
        val csrf: String?,
    )

    private class Harness(var access: String?, var refresh: String?, var csrf: String? = "csrf_a") {
        var calls = 0
        var saved_current: Pair<String, String>? = null
        val saved_for_account = mutableListOf<SavedForAccount>()
        var restored_csrf: String? = null
        var on_call: suspend Harness.(String?) -> RotatedTokens = { error("unset") }

        fun build(): AccountBoundRefresh = AccountBoundRefresh(
            gate = Mutex(),
            read_access_token = { access },
            read_refresh_token = { refresh },
            resolve_owner = { it },
            call_refresh = { presented ->
                calls += 1
                on_call(presented)
            },
            read_csrf = { csrf },
            restore_csrf = {
                restored_csrf = it
                csrf = it
            },
            save_current = { a, r ->
                saved_current = a to r
                access = a
                refresh = r
            },
            save_for_account = { id, expected, a, r, c -> saved_for_account += SavedForAccount(id, expected, a, r, c) },
        )
    }

    @Test
    fun jwt_subject_reads_sub_claim() {
        assertEquals("User-A", jwt_subject(jwt("User-A")))
        assertNull(jwt_subject(null))
        assertNull(jwt_subject("opaque"))
        assertNull(jwt_subject("a.!!!.c"))
    }

    @Test
    fun session_token_owner_prefers_refresh_and_lowercases() {
        assertEquals("user-a", session_token_owner(jwt("User-A"), jwt("user-b")))
        assertEquals("user-b", session_token_owner("opaque", jwt("User-B")))
        assertNull(session_token_owner(null, null))
    }

    @Test
    fun same_account_ignores_case_and_rejects_null() {
        assertTrue(same_account("ABC", "abc"))
        assertFalse(same_account("abc", null))
        assertFalse(same_account(null, null))
        assertFalse(same_account("abc", "abd"))
    }

    @Test
    fun stale_presented_token_skips_the_call() = runBlocking {
        val harness = Harness(access = jwt("a"), refresh = jwt("a", "new"))
        val outcome = harness.build().refresh(jwt("a", "old"))
        assertEquals(RefreshOutcome.Success, outcome)
        assertEquals(0, harness.calls)
    }

    @Test
    fun current_account_refresh_saves_current_and_snapshot() = runBlocking {
        val presented = jwt("a", "r1")
        val harness = Harness(access = jwt("a", "x1"), refresh = presented)
        val rotated_access = jwt("a", "x2")
        val rotated_refresh = jwt("a", "r2")
        harness.on_call = {
            csrf = "csrf_rotated"
            RotatedTokens(rotated_access, rotated_refresh)
        }
        val outcome = harness.build().refresh(presented)
        assertEquals(RefreshOutcome.Success, outcome)
        assertEquals(rotated_access to rotated_refresh, harness.saved_current)
        assertEquals(
            listOf(SavedForAccount("a", null, rotated_access, rotated_refresh, "csrf_rotated")),
            harness.saved_for_account,
        )
        assertNull(harness.restored_csrf)
    }

    @Test
    fun switched_account_refresh_writes_only_the_owner_snapshot() = runBlocking {
        val presented = jwt("a", "r1")
        val harness = Harness(access = jwt("a", "x1"), refresh = presented, csrf = "csrf_a")
        val account_b_refresh = jwt("b", "r1")
        val rotated_access = jwt("a", "x2")
        val rotated_refresh = jwt("a", "r2")
        harness.on_call = {
            refresh = account_b_refresh
            access = jwt("b", "x1")
            csrf = "csrf_from_a_refresh"
            RotatedTokens(rotated_access, rotated_refresh)
        }
        harness.csrf = "csrf_a"
        val outcome = harness.build().refresh(presented)
        assertEquals(RefreshOutcome.Success, outcome)
        assertNull(harness.saved_current)
        assertEquals(account_b_refresh, harness.refresh)
        assertEquals("csrf_a", harness.restored_csrf)
        assertEquals(
            listOf(SavedForAccount("a", presented, rotated_access, rotated_refresh, "csrf_from_a_refresh")),
            harness.saved_for_account,
        )
    }

    @Test
    fun response_for_another_account_is_discarded() = runBlocking {
        val presented = jwt("a", "r1")
        val harness = Harness(access = jwt("a", "x1"), refresh = presented)
        harness.on_call = {
            csrf = "csrf_b"
            RotatedTokens(jwt("b", "x2"), jwt("b", "r2"))
        }
        val outcome = harness.build().refresh(presented)
        assertEquals(RefreshOutcome.Transient, outcome)
        assertNull(harness.saved_current)
        assertTrue(harness.saved_for_account.isEmpty())
        assertEquals("csrf_a", harness.csrf)
    }

    @Test
    fun unauthorized_maps_to_auth_failed() = runBlocking {
        val presented = jwt("a", "r1")
        val harness = Harness(access = jwt("a", "x1"), refresh = presented)
        harness.on_call = { throw ApiError.UnauthorizedError }
        assertEquals(RefreshOutcome.AuthFailed, harness.build().refresh(presented))
        assertNull(harness.saved_current)
    }

    @Test
    fun network_failure_maps_to_transient() = runBlocking {
        val presented = jwt("a", "r1")
        val harness = Harness(access = jwt("a", "x1"), refresh = presented)
        harness.on_call = { throw ApiError.NetworkError }
        assertEquals(RefreshOutcome.Transient, harness.build().refresh(presented))
        assertTrue(harness.saved_for_account.isEmpty())
    }
}
