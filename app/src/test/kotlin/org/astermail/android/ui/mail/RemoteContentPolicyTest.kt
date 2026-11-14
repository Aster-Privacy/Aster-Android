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

import org.astermail.android.api.preferences.UserPreferences
import org.astermail.android.ui.compose.build_quoted_preview_body
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteContentPolicyTest {

    private val remote_image = "https://cdn.example.com/hero.png"

    private val mail_html = """<p>Hello</p><img src="$remote_image" width="600" height="400">"""

    private val pixel_html = """<p>Hello</p><img src="https://cdn.example.com/a.gif" width="1" height="1">"""

    private fun preferences(
        load_remote_images: String,
        block_external_images: Boolean = true,
        block_external_content: Boolean = true,
        block_tracking_pixels: Boolean = true,
        block_tracking_links: Boolean = true,
    ) = UserPreferences(
        load_remote_images = load_remote_images,
        block_external_images = block_external_images,
        block_external_content = block_external_content,
        block_tracking_pixels = block_tracking_pixels,
        block_tracking_links = block_tracking_links,
    )

    private fun preview_body(preferences: UserPreferences?): String = build_quoted_preview_body(
        raw_html = mail_html,
        allow_external = !setting_blocks_remote_images(preferences),
        blocked_image_labels = BlockedImageLabels.ENGLISH,
        sanitize_options = remote_content_sanitize_options(preferences),
    )

    private fun loadable_sources(preferences: UserPreferences?): List<String> =
        Jsoup.parseBodyFragment(preview_body(preferences)).select("[src], [srcset], [background]")
            .flatMap { listOf(it.attr("src"), it.attr("srcset"), it.attr("background")) }
            .filter { it.isNotBlank() && !it.startsWith("data:") }

    @Test
    fun never_blocks_images_when_the_content_toggle_is_off() {
        val chosen = preferences("never", block_external_content = false)
        assertEquals(REMOTE_IMAGES_NEVER, effective_remote_image_mode(chosen))
        assertTrue(setting_blocks_remote_images(chosen))
    }

    @Test
    fun ask_blocks_images_when_the_content_toggle_is_off() {
        val chosen = preferences("ask", block_external_content = false)
        assertEquals(REMOTE_IMAGES_ASK, effective_remote_image_mode(chosen))
        assertTrue(setting_blocks_remote_images(chosen))
    }

    @Test
    fun never_and_ask_block_images_when_the_content_toggle_is_on() {
        assertTrue(setting_blocks_remote_images(preferences("never")))
        assertTrue(setting_blocks_remote_images(preferences("ask")))
    }

    @Test
    fun always_loads_images_whatever_the_content_toggle_says() {
        assertFalse(setting_blocks_remote_images(preferences("always")))
        assertFalse(setting_blocks_remote_images(preferences("always", block_external_content = false)))
    }

    @Test
    fun turning_image_blocking_off_loads_images() {
        val chosen = preferences("never", block_external_images = false)
        assertEquals(REMOTE_IMAGES_ALWAYS, effective_remote_image_mode(chosen))
        assertFalse(setting_blocks_remote_images(chosen))
    }

    @Test
    fun an_unknown_mode_and_missing_preferences_block_images() {
        assertTrue(setting_blocks_remote_images(preferences("sometimes", block_external_content = false)))
        assertTrue(setting_blocks_remote_images(null))
        assertTrue(setting_blocks_remote_images(UserPreferences()))
    }

    @Test
    fun tracking_pixels_are_removed_while_images_are_blocked() {
        val chosen = preferences("never", block_external_content = false, block_tracking_pixels = false)
        assertTrue(setting_removes_tracking_pixels(chosen))
        assertTrue(remote_content_sanitize_options(chosen).remove_tracking_pixels)
        assertFalse(EmailHtmlSanitizer.sanitize(pixel_html, remote_content_sanitize_options(chosen)).contains("a.gif"))
    }

    @Test
    fun tracking_pixels_follow_their_own_switch_when_images_load() {
        assertTrue(setting_removes_tracking_pixels(preferences("always")))
        assertFalse(setting_removes_tracking_pixels(preferences("always", block_tracking_pixels = false)))
        assertFalse(setting_removes_tracking_pixels(preferences("always", block_external_content = false)))
    }

    @Test
    fun link_cleaning_and_remote_styles_keep_their_rules() {
        assertTrue(remote_content_sanitize_options(preferences("never")).clean_tracking_links)
        assertFalse(
            remote_content_sanitize_options(preferences("never", block_external_content = false)).clean_tracking_links,
        )
        assertFalse(
            remote_content_sanitize_options(preferences("never", block_tracking_links = false)).clean_tracking_links,
        )
        assertTrue(remote_content_sanitize_options(null).block_remote_fonts)
        assertTrue(remote_content_sanitize_options(null).block_remote_css)
    }

    @Test
    fun quoted_preview_keeps_remote_images_out_for_never_with_the_content_toggle_off() {
        assertEquals(emptyList<String>(), loadable_sources(preferences("never", block_external_content = false)))
    }

    @Test
    fun quoted_preview_keeps_remote_images_out_for_ask_with_the_content_toggle_off() {
        assertEquals(emptyList<String>(), loadable_sources(preferences("ask", block_external_content = false)))
    }

    @Test
    fun quoted_preview_routes_images_through_the_proxy_for_always() {
        val body = preview_body(preferences("always"))
        assertTrue(body.contains(REMOTE_IMAGE_PROXY_BASE))
    }

    @Test
    fun never_mode_refuses_a_one_time_image_grant() {
        assertFalse(allows_remote_image_grant(preferences("never")))
        assertFalse(allows_remote_image_grant(null))
        assertTrue(allows_remote_image_grant(preferences("ask")))
        assertTrue(allows_remote_image_grant(preferences("always")))

        assertFalse(message_loads_remote_content(blocked = true, granted = true, grant_allowed = false, is_system_sender = false))
        assertTrue(message_loads_remote_content(blocked = true, granted = true, grant_allowed = true, is_system_sender = false))
        assertFalse(message_loads_remote_content(blocked = true, granted = false, grant_allowed = true, is_system_sender = false))
        assertTrue(message_loads_remote_content(blocked = false, granted = false, grant_allowed = false, is_system_sender = false))
        assertTrue(message_loads_remote_content(blocked = true, granted = false, grant_allowed = false, is_system_sender = true))
    }
}
