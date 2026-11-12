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
}
