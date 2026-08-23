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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpStatusMappingTest {

    private lateinit var client: ApiClient

    @Before
    fun setup() {
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

    @Test
    fun `payment required maps to its own error instead of an unknown status`() {
        val err = client.map_http_status(402, """{"error":"card declined"}""")
        assertTrue(err is ApiError.PaymentRequired)
        assertEquals("card declined", (err as ApiError.PaymentRequired).detail)
    }

    @Test
    fun `payload too large without a storage code is an attachment problem`() {
        val err = client.map_http_status(413, """{"error":"file too big"}""")
        assertTrue(err is ApiError.AttachmentTooLarge)
        assertEquals("file too big", (err as ApiError.AttachmentTooLarge).detail)
    }

    @Test
    fun `payload too large with the storage code is a storage problem`() {
        val body = """{"code":"$STORAGE_QUOTA_CODE","error":"mailbox full"}"""
        val err = client.map_http_status(413, body)
        assertTrue(err is ApiError.StorageQuotaExceeded)
    }

    @Test
    fun `send quota refusals keep their own type at any status`() {
        val body = """{"code":"EXTERNAL_SEND_QUOTA_REACHED","error":"daily limit"}"""
        val err = client.map_http_status(429, body)
        assertTrue(err is ApiError.SendQuotaReached)
        assertEquals("daily limit", (err as ApiError.SendQuotaReached).detail)
    }
}
