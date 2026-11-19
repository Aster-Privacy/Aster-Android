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

package org.astermail.android.settings

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class GalleryThumbClientTest {

    private lateinit var gallery_server: MockWebServer
    private lateinit var other_server: MockWebServer

    private val signed_in_client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder().header("Authorization", "Bearer session-token").build(),
            )
        }
        .build()

    @Before
    fun setup() {
        gallery_server = MockWebServer()
        other_server = MockWebServer()
        gallery_server.start()
        other_server.start()
    }

    @After
    fun teardown() {
        gallery_server.shutdown()
        other_server.shutdown()
    }

    private fun base_url(): String = gallery_server.url("/gallery").toString()

    private fun fetch(client: OkHttpClient, url: String): Int =
        client.newCall(Request.Builder().url(url).build()).execute().use { it.code }

    @Test
    fun gallery_thumbnail_request_carries_no_authorization() {
        gallery_server.enqueue(MockResponse().setResponseCode(200).setBody("img"))
        val client = gallery_thumb_client(signed_in_client, base_url())

        fetch(client, gallery_thumb_url("aurora-1", base_url()))

        assertNull(gallery_server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun gallery_thumbnail_redirect_off_host_is_not_followed() {
        gallery_server.enqueue(
            MockResponse().setResponseCode(302).setHeader("Location", other_server.url("/x.webp").toString()),
        )
        other_server.enqueue(MockResponse().setResponseCode(200).setBody("img"))
        val client = gallery_thumb_client(signed_in_client, base_url())

        val code = fetch(client, gallery_thumb_url("aurora-1", base_url()))

        assertEquals(302, code)
        assertEquals(0, other_server.requestCount)
    }

    @Test
    fun gallery_client_refuses_another_host() {
        other_server.enqueue(MockResponse().setResponseCode(200).setBody("img"))
        val client = gallery_thumb_client(signed_in_client, base_url())

        try {
            fetch(client, other_server.url("/gallery/thumb/aurora-1.webp").toString())
            fail("request to another host was sent")
        } catch (_: IOException) {
        }
        assertEquals(0, other_server.requestCount)
    }
}
