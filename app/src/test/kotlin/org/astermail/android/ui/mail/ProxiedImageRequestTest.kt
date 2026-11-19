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

package org.astermail.android.ui.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProxiedImageRequestTest {

    private val url = "https://app.astermail.org/api/images/v1/proxy?url=https%3A%2F%2Fexample.com%2Fa.png"

    @Test
    fun sends_the_access_token_as_a_bearer_header_without_cookies() {
        val request = proxied_image_request(url, "token-123")!!

        assertEquals("Bearer token-123", request.header("Authorization"))
        assertNull(request.header("Cookie"))
        assertEquals(url, request.url.toString())
    }

    @Test
    fun the_token_is_sent_only_to_the_image_proxy() {
        val others = listOf(
            "https://example.com/a.png",
            "https://example.com/api/images/v1/proxy?url=https%3A%2F%2Fexample.com%2Fa.png",
            "http://app.astermail.org/api/images/v1/proxy?url=https%3A%2F%2Fexample.com%2Fa.png",
            "https://app.astermail.org:8443/api/images/v1/proxy?url=https%3A%2F%2Fexample.com%2Fa.png",
            "https://user:pass@app.astermail.org/api/images/v1/proxy?url=https%3A%2F%2Fexample.com%2Fa.png",
            "https://app.astermail.org/api/mail/v1/messages?url=https%3A%2F%2Fexample.com%2Fa.png",
            "https://app.astermail.org/api/images/v1/proxy",
            "https://app.astermail.org.example.com/api/images/v1/proxy?url=x",
            "not a url",
        )
        for (other in others) {
            assertNull(other, proxied_image_request(other, "token-123"))
        }
    }

    @Test
    fun an_expired_token_is_refreshed_once() {
        val sent = mutableListOf<String?>()
        val closed = mutableListOf<Int>()
        val result = fetch_with_one_token_refresh(
            token = "old",
            fetch = { bearer -> sent += bearer; if (bearer == "old") 401 else 200 },
            status_of = { it },
            close = { closed += it },
            refresh = { "new" },
        )
        assertEquals(200, result)
        assertEquals(listOf<String?>("old", "new"), sent)
        assertEquals(listOf(401), closed)
    }

    @Test
    fun a_second_expiry_is_not_refreshed_again() {
        var refreshes = 0
        val sent = mutableListOf<String?>()
        val result = fetch_with_one_token_refresh(
            token = "old",
            fetch = { bearer -> sent += bearer; 401 },
            status_of = { it },
            close = {},
            refresh = { refreshes++; "new" },
        )
        assertEquals(401, result)
        assertEquals(1, refreshes)
        assertEquals(listOf<String?>("old", "new"), sent)
    }

    @Test
    fun a_failed_refresh_keeps_the_first_answer() {
        val sent = mutableListOf<String?>()
        val result = fetch_with_one_token_refresh(
            token = "old",
            fetch = { bearer -> sent += bearer; 401 },
            status_of = { it },
            close = {},
            refresh = { null },
        )
        assertEquals(401, result)
        assertEquals(listOf<String?>("old"), sent)
        assertEquals(200, fetch_with_one_token_refresh("old", { 200 }, { it }, {}, { error("unused") }))
    }

    @Test
    fun the_token_is_attached_only_to_the_image_proxy() {
        val refused = listOf(
            "https://example.com/api/images/v1/proxy?url=https%3A%2F%2Fexample.com%2Fa.png",
            "http://app.astermail.org/api/images/v1/proxy?url=https%3A%2F%2Fexample.com%2Fa.png",
            "https://app.astermail.org:8443/api/images/v1/proxy?url=https%3A%2F%2Fexample.com%2Fa.png",
            "https://user:pass@app.astermail.org/api/images/v1/proxy?url=https%3A%2F%2Fexample.com%2Fa.png",
            "https://app.astermail.org@example.com/api/images/v1/proxy?url=https%3A%2F%2Fexample.com%2Fa.png",
            "https://app.astermail.org.example.com/api/images/v1/proxy?url=https%3A%2F%2Fexample.com%2Fa.png",
            "https://app.astermail.org/api/images/v1/proxy/../../auth/me?url=x",
            "https://app.astermail.org/api/auth/me?url=https%3A%2F%2Fexample.com%2Fa.png",
            "https://app.astermail.org/api/images/v1/proxy",
            "https://app.astermail.org/api/images/v1/proxy?url=",
            "not a url",
        )
        for (candidate in refused) {
            assertNull(candidate, proxied_image_request(candidate, "tok"))
        }
        assertEquals("Bearer tok", proxied_image_request(url, "tok")?.header("Authorization"))
        assertEquals(
            "Bearer tok",
            proxied_image_request("https://app.astermail.org:443/api/images/v1/proxy?url=x", "tok")?.header("Authorization"),
        )
    }

    @Test
    fun a_redirect_away_from_the_image_proxy_is_not_followed() {
        val refused = listOf(
            "https://example.com/a.png",
            "//example.com/api/images/v1/proxy?url=x",
            "http://app.astermail.org/api/images/v1/proxy?url=x",
            "https://app.astermail.org:8443/api/images/v1/proxy?url=x",
            "https://a@app.astermail.org/api/images/v1/proxy?url=x",
            "/api/auth/me",
            "/api/images/v1/proxy",
            "",
            null,
        )
        for (location in refused) {
            assertNull(location, proxy_redirect_target(url, location))
        }
        assertEquals(
            "https://app.astermail.org/api/images/v1/proxy?url=y",
            proxy_redirect_target(url, "/api/images/v1/proxy?url=y"),
        )
    }

    @Test
    fun skips_the_fetch_without_a_token() {
        assertNull(proxied_image_request(url, null))
        assertNull(proxied_image_request(url, "  "))
    }

    @Test
    fun only_the_image_proxy_is_fetched() {
        assertEquals(
            MailBodyRequestAction.PROXY,
            mail_body_request_action("https", REMOTE_IMAGE_PROXY_HOST, REMOTE_IMAGE_PROXY_PATH, "https://example.com/a.png", true),
        )
        assertEquals(
            MailBodyRequestAction.BLOCK,
            mail_body_request_action("https", "example.com", "/a.png", null, true),
        )
        assertEquals(
            MailBodyRequestAction.BLOCK,
            mail_body_request_action("https", REMOTE_IMAGE_PROXY_HOST, REMOTE_IMAGE_PROXY_PATH, "https://example.com/a.png", false),
        )
    }

    @Test
    fun the_image_proxy_is_fetched_only_on_its_own_origin() {
        val target = "https://example.com/a.png"
        assertEquals(
            MailBodyRequestAction.PROXY,
            mail_body_request_action("https", REMOTE_IMAGE_PROXY_HOST, REMOTE_IMAGE_PROXY_PATH, target, true, port = 443),
        )
        assertEquals(
            MailBodyRequestAction.BLOCK,
            mail_body_request_action("https", REMOTE_IMAGE_PROXY_HOST, REMOTE_IMAGE_PROXY_PATH, target, true, port = 8443),
        )
        assertEquals(
            MailBodyRequestAction.BLOCK,
            mail_body_request_action("https", REMOTE_IMAGE_PROXY_HOST, REMOTE_IMAGE_PROXY_PATH, target, true, user_info = "a"),
        )
    }
}
