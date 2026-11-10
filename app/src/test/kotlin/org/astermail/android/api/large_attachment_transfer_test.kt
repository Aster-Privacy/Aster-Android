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
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.astermail.android.api.mail.CreateAttachmentRequestBody
import org.astermail.android.api.mail.MailApiImpl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class large_attachment_transfer_test {
    private lateinit var server: MockWebServer

    private val image_bytes = 18 * 1024 * 1024
    private val encrypted_chars = (image_bytes + 2) / 3 * 4
    private val server_pause_seconds = 20L

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    private fun api(): MailApiImpl {
        val token_provider = object : TokenProvider {
            override suspend fun load(): BearerTokens? = BearerTokens("access", "refresh")
            override suspend fun refresh(): BearerTokens? = BearerTokens("access", "refresh")
            override suspend fun clear() {}
        }
        return MailApiImpl(
            ApiClient(
                base_url = server.url("/").toString().trimEnd('/'),
                token_provider = token_provider,
                initial_csrf = "csrf",
                allow_cleartext_for_test = true,
            ),
        )
    }

    @Test
    fun an_18_mb_image_downloads_when_the_server_pauses_mid_transfer() = runBlocking {
        val data = "A".repeat(encrypted_chars)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"id":"att-1","mail_item_id":"mail-1","encrypted_data":"$data",""" +
                        """"data_nonce":"n","encrypted_meta":"m","size_bytes":$image_bytes}""",
                )
                .setBodyDelay(server_pause_seconds, TimeUnit.SECONDS),
        )

        val attachment = api().get_attachment("att-1")

        assertEquals("att-1", attachment.id)
        assertEquals(encrypted_chars, attachment.encrypted_data.length)
    }

    @Test
    fun a_message_carrying_an_18_mb_image_opens_when_the_server_pauses() = runBlocking {
        val envelope = "A".repeat(encrypted_chars)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"mail-1","encrypted_envelope":"$envelope"}""")
                .setBodyDelay(server_pause_seconds, TimeUnit.SECONDS),
        )

        val item = api().get_message("mail-1")

        assertEquals("mail-1", item.id)
        assertEquals(encrypted_chars, item.encrypted_envelope?.length)
    }

    @Test
    fun an_18_mb_image_uploads_when_the_server_is_slow_to_answer() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"att-2","success":true}""")
                .setHeadersDelay(server_pause_seconds, TimeUnit.SECONDS),
        )

        val created = api().create_attachment(
            "mail-1",
            CreateAttachmentRequestBody(
                encrypted_data = "A".repeat(encrypted_chars),
                data_nonce = "n",
                encrypted_meta = "m",
                meta_nonce = "mn",
            ),
        )

        assertEquals("att-2", created.id)
        assertTrue(server.takeRequest().bodySize > encrypted_chars)
    }
}
