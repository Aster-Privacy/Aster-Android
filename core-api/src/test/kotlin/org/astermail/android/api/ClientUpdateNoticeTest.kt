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

import io.ktor.client.plugins.auth.providers.BearerTokens
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClientUpdateNoticeTest {

    private lateinit var client: ApiClient

    @Before
    fun setup() {
        ClientUpdateNotice.dismiss()
        val token_provider = object : TokenProvider {
            override suspend fun load(): BearerTokens? = null
            override suspend fun refresh(): BearerTokens? = null
            override suspend fun clear() {}
        }
        client = ApiClient(
            base_url = "http://127.0.0.1:1",
            token_provider = token_provider,
            allow_cleartext_for_test = true,
        )
    }

    @After
    fun reset() {
        ClientUpdateNotice.dismiss()
    }

    @Test
    fun an_upgrade_reply_raises_the_update_prompt() {
        val err = client.map_http_status(426, """{"code":"CLIENT_UPGRADE_REQUIRED","error":"old client"}""")

        assertTrue(err is ApiError.UpgradeRequired)
        assertTrue(ClientUpdateNotice.required.value)
    }

    @Test
    fun dismissing_clears_the_prompt_until_the_next_reply() {
        client.map_http_status(426, """{"code":"CLIENT_UPGRADE_REQUIRED"}""")
        ClientUpdateNotice.dismiss()
        assertFalse(ClientUpdateNotice.required.value)

        client.map_http_status(426, "")
        assertTrue(ClientUpdateNotice.required.value)
    }

    @Test
    fun other_errors_never_raise_the_prompt() {
        client.map_http_status(403, """{"code":"CLIENT_UPGRADE_REQUIRED"}""")
        client.map_http_status(409, """{"code":"VAULT_LEVEL2_NOT_ADMITTED"}""")
        client.map_http_status(426, """{"code":"SOMETHING_ELSE"}""")

        assertFalse(ClientUpdateNotice.required.value)
    }

    @Test
    fun every_request_declares_vault_level_two() {
        assertEquals("2", VAULT_LEVEL)
    }
}
