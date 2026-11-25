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

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.astermail.android.api.preferences.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteImagePrefetchTest {
    private val always = UserPreferences(block_external_images = true, load_remote_images = "always")
    private val ask = UserPreferences(block_external_images = true, load_remote_images = "ask")
    private val on = remote_image_cache_settings(keep_images = true, prefetch_on_arrival = true)

    private fun body(vararg sources: String): String =
        "<html><body>" + sources.joinToString("") { "<img src=\"$it\" width=\"200\" height=\"100\">" } + "</body></html>"

    @Test
    fun prefetch_needs_every_condition() {
        assertTrue(arrival_prefetch_allowed(on, always, unmetered = true))
        assertFalse(arrival_prefetch_allowed(on, always, unmetered = false))
        assertFalse(arrival_prefetch_allowed(on, ask, unmetered = true))
        assertFalse(arrival_prefetch_allowed(on, null, unmetered = true))
        assertFalse(arrival_prefetch_allowed(on.copy(prefetch_on_arrival = false), always, unmetered = true))
        assertFalse(arrival_prefetch_allowed(on.copy(keep_images = false), always, unmetered = true))
        assertFalse(arrival_prefetch_allowed(on, always, unmetered = true, low_network = true))
        assertFalse(arrival_prefetch_allowed(on, always.copy(html_rendering_mode = "plain_text"), unmetered = true))
    }

    @Test
    fun prefetch_skips_trackers_and_pixels() {
        val html = "<html><body>" +
            "<img src=\"https://cdn.example.com/hero.jpg\" width=\"600\" height=\"300\">" +
            "<img src=\"https://news.example.com/open/abc.gif\" width=\"600\" height=\"300\">" +
            "<img src=\"https://mail.example.com/track/xyz.png\">" +
            "<img src=\"https://cdn.example.com/dot.gif\" width=\"1\" height=\"1\">" +
            "<img src=\"cid:inline-part\">" +
            "<img src=\"data:image/png;base64,iVBORw0KGgo=\">" +
            "</body></html>"
        val urls = prefetch_image_urls(html)
        assertEquals(1, urls.size)
        assertTrue(is_image_proxy_url(urls.single()))
        assertEquals("https://cdn.example.com/hero.jpg", remote_image_cache_key(urls.single()))
    }

    @Test
    fun setting_off_means_zero_fetches() = runBlocking {
        val fetches = AtomicInteger()
        val stored = prefetch_remote_images(
            bodies = listOf(body("https://cdn.example.com/a.png")),
            allowed = { arrival_prefetch_allowed(on.copy(prefetch_on_arrival = false), always, unmetered = true) },
            is_cached = { false },
            fetch = { fetches.incrementAndGet(); prefetch_outcome.stored },
            pause = {},
        )
        assertEquals(0, stored)
        assertEquals(0, fetches.get())
    }

    @Test
    fun metered_network_means_zero_fetches() = runBlocking {
        val fetches = AtomicInteger()
        prefetch_remote_images(
            bodies = listOf(body("https://cdn.example.com/a.png")),
            allowed = { arrival_prefetch_allowed(on, always, unmetered = false) },
            is_cached = { false },
            fetch = { fetches.incrementAndGet(); prefetch_outcome.stored },
            pause = {},
        )
        assertEquals(0, fetches.get())
    }

    @Test
    fun cached_images_are_not_fetched_again() = runBlocking {
        val fetched = mutableListOf<String>()
        prefetch_remote_images(
            bodies = listOf(body("https://cdn.example.com/a.png", "https://cdn.example.com/b.png")),
            allowed = { true },
            is_cached = { remote_image_cache_key(it) == "https://cdn.example.com/a.png" },
            fetch = { url -> synchronized(fetched) { fetched.add(remote_image_cache_key(url)!!) }; prefetch_outcome.stored },
            pause = {},
        )
        assertEquals(listOf("https://cdn.example.com/b.png"), fetched)
    }

    @Test
    fun caps_messages_and_images_and_paces_requests() = runBlocking {
        val bodies = (0 until 25).map { m -> body(*Array(40) { i -> "https://cdn.example.com/m$m/i$i.png" }) }
        val fetched = AtomicInteger()
        val pauses = mutableListOf<Long>()
        prefetch_remote_images(
            bodies = bodies,
            allowed = { true },
            is_cached = { false },
            fetch = { fetched.incrementAndGet(); prefetch_outcome.stored },
            pause = { pauses.add(it) },
        )
        val total = PREFETCH_MAX_MESSAGES * PREFETCH_MAX_IMAGES_PER_MESSAGE
        assertEquals(total, fetched.get())
        assertEquals(total - 1, pauses.size)
        assertTrue(pauses.all { it == PREFETCH_INTERVAL_MS })
        assertTrue(60_000L / PREFETCH_INTERVAL_MS < 100)
    }

    @Test
    fun three_failures_in_a_row_stop_the_batch() = runBlocking {
        val fetched = AtomicInteger()
        prefetch_remote_images(
            bodies = listOf(body(*Array(10) { "https://cdn.example.com/f$it.png" })),
            allowed = { true },
            is_cached = { false },
            fetch = { fetched.incrementAndGet(); prefetch_outcome.failed },
            pause = {},
        )
        assertEquals(PREFETCH_MAX_CONSECUTIVE_FAILURES, fetched.get())
    }

    @Test
    fun a_success_resets_the_failure_count() = runBlocking {
        val outcomes = ArrayDeque(
            listOf(
                prefetch_outcome.failed, prefetch_outcome.failed, prefetch_outcome.stored,
                prefetch_outcome.failed, prefetch_outcome.failed, prefetch_outcome.failed,
            ),
        )
        val fetched = AtomicInteger()
        val stored = prefetch_remote_images(
            bodies = listOf(body(*Array(10) { "https://cdn.example.com/r$it.png" })),
            allowed = { true },
            is_cached = { false },
            fetch = { fetched.incrementAndGet(); outcomes.removeFirstOrNull() ?: prefetch_outcome.stored },
            pause = {},
        )
        assertEquals(6, fetched.get())
        assertEquals(1, stored)
    }

    @Test
    fun turning_the_setting_off_mid_batch_stops_further_fetches() = runBlocking {
        var enabled = true
        val fetched = AtomicInteger()
        prefetch_remote_images(
            bodies = listOf(body(*Array(10) { "https://cdn.example.com/s$it.png" })),
            allowed = { enabled },
            is_cached = { false },
            fetch = {
                if (fetched.incrementAndGet() == 2) enabled = false
                prefetch_outcome.stored
            },
            pause = {},
        )
        assertEquals(2, fetched.get())
    }

    @Test
    fun duplicate_images_are_fetched_once() = runBlocking {
        val fetched = AtomicInteger()
        prefetch_remote_images(
            bodies = listOf(body("https://cdn.example.com/a.png"), body("https://cdn.example.com/a.png#x")),
            allowed = { true },
            is_cached = { false },
            fetch = { fetched.incrementAndGet(); prefetch_outcome.stored },
            pause = {},
        )
        assertEquals(1, fetched.get())
    }
}
