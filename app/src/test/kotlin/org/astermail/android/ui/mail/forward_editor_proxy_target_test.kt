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

import org.astermail.android.ui.compose.forward_editor_image_sources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class forward_editor_proxy_target_test {

    @Test
    fun remote_images_go_through_the_image_proxy() {
        val target = forward_editor_proxy_target("https://example.com/a.png?x=1&y=2", allow_external = true)

        assertEquals(REMOTE_IMAGE_PROXY_BASE + "https%3A%2F%2Fexample.com%2Fa.png%3Fx%3D1%26y%3D2", target)
        assertTrue(is_image_proxy_url(target!!))
    }

    @Test
    fun plain_http_images_are_upgraded_before_proxying() {
        val target = forward_editor_proxy_target("http://example.com/a.png", allow_external = true)

        assertEquals(REMOTE_IMAGE_PROXY_BASE + "https%3A%2F%2Fexample.com%2Fa.png", target)
    }

    @Test
    fun an_existing_proxy_url_is_not_wrapped_twice() {
        val proxied = REMOTE_IMAGE_PROXY_BASE + "https%3A%2F%2Fexample.com%2Fa.png"

        assertEquals(proxied, forward_editor_proxy_target(proxied, allow_external = true))
    }

    @Test
    fun nothing_is_fetched_when_remote_images_are_blocked() {
        assertNull(forward_editor_proxy_target("https://example.com/a.png", allow_external = false))
    }

    @Test
    fun only_web_urls_are_proxied() {
        listOf(
            "file:///data/data/org.astermail.android/a.png",
            "content://media/a.png",
            "javascript:alert(1)",
            "cid:image001@01D0",
            "ftp://example.com/a.png",
            "",
            null,
        ).forEach { assertNull(it, forward_editor_proxy_target(it, allow_external = true)) }
    }

    @Test
    fun the_editor_policy_allows_web_images_only_when_remote_images_are_allowed() {
        assertEquals("data:", forward_editor_image_sources(allow_external = false))
        assertEquals("data: https: http:", forward_editor_image_sources(allow_external = true))
    }
}
