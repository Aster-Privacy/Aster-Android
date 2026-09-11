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

import kotlinx.coroutines.runBlocking
import org.astermail.android.api.auth.RefreshOutcome
import org.astermail.android.api.auth.SessionTokenProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class session_token_provider_test {

    private var access: String? = "access_0"
    private var refresh: String? = "refresh_0"
    private var refresh_calls = 0

    private fun provider(outcome: RefreshOutcome, rotate: Boolean = false) = SessionTokenProvider(
        read_access_token = { access },
        read_refresh_token = { refresh },
        refresh_session = {
            refresh_calls += 1
            if (rotate) {
                access = "access_1"
                refresh = "refresh_1"
            }
            outcome
        },
    )

    @Test
    fun a_token_rotated_by_another_request_is_reused_without_refreshing() = runBlocking {
        access = "access_1"
        val tokens = provider(RefreshOutcome.Success).refresh("access_0")
        assertEquals("access_1", tokens?.accessToken)
        assertEquals(0, refresh_calls)
    }

    @Test
    fun a_dead_session_returns_no_tokens() = runBlocking {
        assertNull(provider(RefreshOutcome.AuthFailed).refresh("access_0"))
        assertEquals(1, refresh_calls)
    }

    @Test
    fun a_transient_failure_does_not_retry_with_the_rejected_token() = runBlocking {
        assertNull(provider(RefreshOutcome.Transient).refresh("access_0"))
        assertEquals("access_0", access)
    }

    @Test
    fun a_successful_refresh_returns_the_rotated_tokens() = runBlocking {
        val tokens = provider(RefreshOutcome.Success, rotate = true).refresh("access_0")
        assertEquals("access_1", tokens?.accessToken)
        assertEquals("refresh_1", tokens?.refreshToken)
        assertEquals(1, refresh_calls)
    }
}
