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

package org.astermail.android.notifications

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.astermail.android.security.SenderTrustContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSenderPictureTest {

    private val api_host = "api.astermail.org"
    private val settings = NotificationAvatarSettings(show_pictures = true, low_network = false)

    private fun budget() = NotificationAvatarBudget(total_ms = 400L, per_fetch_ms = 200L)

    private class PostRecorder {
        val posts = mutableListOf<String?>()
        fun post(icon: String?): Boolean {
            posts.add(icon)
            return true
        }
    }

    private suspend fun post_with_icon(recorder: PostRecorder, picture: suspend () -> String?) {
        val icon = sender_icon_or_initials(picture = picture, initials = { "initials" })
        recorder.post(icon)
    }

    @Test
    fun a_sender_with_a_profile_picture_gets_the_picture() = runBlocking {
        val loads = mutableListOf<Pair<String, Boolean>>()
        val recorder = PostRecorder()
        post_with_icon(recorder) {
            resolve_sender_picture(
                known_url = { null },
                lookup_url = { "https://api.astermail.org/avatars/abc.webp" },
                load = { url, allow_network ->
                    loads.add(url to allow_network)
                    "picture"
                },
                api_host = api_host,
                budget = budget(),
            )
        }
        assertEquals(listOf<String?>("picture"), recorder.posts)
        assertEquals(listOf("https://api.astermail.org/avatars/abc.webp" to true), loads)
    }

    @Test
    fun an_inline_picture_loads_without_the_network() = runBlocking {
        var network: Boolean? = null
        var looked_up = false
        val icon = resolve_sender_picture(
            known_url = { "data:image/png;base64,iVBORw0KGgo=" },
            lookup_url = {
                looked_up = true
                null
            },
            load = { _, allow_network ->
                network = allow_network
                "picture"
            },
            api_host = api_host,
            budget = budget(),
        )
        assertEquals("picture", icon)
        assertEquals(false, network)
        assertFalse(looked_up)
    }

    @Test
    fun a_picture_on_another_host_is_only_read_from_the_cache() = runBlocking {
        var network: Boolean? = null
        resolve_sender_picture(
            known_url = { "https://images.example.net/me.png" },
            lookup_url = { null },
            load = { _, allow_network ->
                network = allow_network
                null
            },
            api_host = api_host,
            budget = budget(),
        )
        assertEquals(false, network)
        assertEquals(SenderPictureSource.CachedOnly, sender_picture_source("https://images.example.net/me.png", api_host))
        assertEquals(SenderPictureSource.AsterHost, sender_picture_source("https://cdn.astermail.org/a.png", api_host))
        assertEquals(SenderPictureSource.CachedOnly, sender_picture_source("https://astermail.org.example.net/a.png", api_host))
        assertNull(sender_picture_source("http://api.astermail.org/a.png", api_host))
        assertNull(sender_picture_source("javascript:alert(1)", api_host))
    }

    @Test
    fun a_sender_without_a_picture_gets_initials_and_one_post() = runBlocking {
        var loaded = false
        val recorder = PostRecorder()
        post_with_icon(recorder) {
            resolve_sender_picture(
                known_url = { null },
                lookup_url = { null },
                load = { _, _ ->
                    loaded = true
                    "picture"
                },
                api_host = api_host,
                budget = budget(),
            )
        }
        assertEquals(listOf<String?>("initials"), recorder.posts)
        assertFalse(loaded)
    }

    @Test
    fun a_failed_picture_load_gets_initials_and_one_post() = runBlocking {
        val recorder = PostRecorder()
        post_with_icon(recorder) {
            resolve_sender_picture(
                known_url = { "https://api.astermail.org/avatars/abc.webp" },
                lookup_url = { null },
                load = { _, _ -> throw IllegalStateException("decode") },
                api_host = api_host,
                budget = budget(),
            )
        }
        assertEquals(listOf<String?>("initials"), recorder.posts)
    }

    @Test
    fun a_failed_profile_lookup_gets_initials_and_one_post() = runBlocking {
        val recorder = PostRecorder()
        post_with_icon(recorder) {
            resolve_sender_picture(
                known_url = { null },
                lookup_url = { throw java.io.IOException("offline") },
                load = { _, _ -> "picture" },
                api_host = api_host,
                budget = budget(),
            )
        }
        assertEquals(listOf<String?>("initials"), recorder.posts)
    }

    @Test
    fun a_slow_picture_load_gives_up_inside_the_budget_and_posts_once() = runBlocking {
        val recorder = PostRecorder()
        val started = System.currentTimeMillis()
        post_with_icon(recorder) {
            resolve_sender_picture<String>(
                known_url = { null },
                lookup_url = { awaitCancellation() },
                load = { _, _ -> awaitCancellation() },
                api_host = api_host,
                budget = budget(),
            )
        }
        val elapsed = System.currentTimeMillis() - started
        assertEquals(listOf<String?>("initials"), recorder.posts)
        assertTrue("took $elapsed ms", elapsed < 2_000L)
    }

    @Test
    fun a_spent_budget_skips_the_lookup() = runBlocking {
        var looked_up = false
        val spent = budget().apply { spend(10_000L) }
        val icon = resolve_sender_picture(
            known_url = { null },
            lookup_url = {
                looked_up = true
                "https://api.astermail.org/a.png"
            },
            load = { _, _ -> "picture" },
            api_host = api_host,
            budget = spent,
        )
        assertNull(icon)
        assertFalse(looked_up)
    }

    @Test
    fun trust_warming_runs_once_and_never_for_hidden_content() = runBlocking {
        var ready = false
        var warms = 0
        val warm: suspend () -> Boolean = {
            warms += 1
            ready = true
            true
        }
        assertFalse(warm_avatar_trust(settings, content_hidden = true, budget = budget(), ready = { ready }, warm = warm))
        assertEquals(0, warms)
        assertTrue(warm_avatar_trust(settings, content_hidden = false, budget = budget(), ready = { ready }, warm = warm))
        assertTrue(warm_avatar_trust(settings, content_hidden = false, budget = budget(), ready = { ready }, warm = warm))
        assertEquals(1, warms)
    }

    @Test
    fun a_slow_trust_warm_gives_up_inside_the_budget() = runBlocking {
        val started = System.currentTimeMillis()
        val ready = warm_avatar_trust(
            settings,
            content_hidden = false,
            budget = budget(),
            ready = { false },
            warm = { awaitCancellation() },
        )
        assertFalse(ready)
        assertTrue(System.currentTimeMillis() - started < 2_000L)
    }

    @Test
    fun push_trust_message_is_built_only_for_internal_received_mail() {
        fun message(item_type: String?, is_external: Boolean?) = push_trust_message(
            item_type = item_type,
            is_external = is_external,
            sender_name = "Robin",
            sender_email = "robin@astermail.org",
            display_sender_name = null,
            display_sender_email = null,
            sender_verification = null,
        )
        assertNull(message("received", null))
        assertNull(message("received", true))
        assertNull(message(null, false))
        assertNull(message("sent", false))
        val internal = message("received", false)
        assertEquals(false, internal?.is_external)
        assertEquals("robin@astermail.org", internal?.sender_email)
    }

    @Test
    fun an_internal_push_sender_plans_a_picture_once_the_trust_context_is_ready() {
        val trust_message = push_trust_message(
            item_type = "received",
            is_external = false,
            sender_name = "Robin",
            sender_email = "robin@astermail.org",
            display_sender_name = null,
            display_sender_email = null,
            sender_verification = null,
        )
        fun plan(ready: Boolean) = plan_notification_avatar(
            notification_avatar_facts(
                message = trust_message,
                shown_email = "robin@astermail.org",
                shown_name = "Robin",
                undecryptable = false,
                system_sender = false,
                settings = settings,
                content_hidden = false,
                trust_context = SenderTrustContext(own_addresses = listOf("me@astermail.org")),
                trust_context_ready = ready,
            ),
        )
        assertTrue(plan(ready = false) is NotificationAvatarPlan.Initials)
        val ready = plan(ready = true) as NotificationAvatarPlan.Picture
        assertEquals("robin@astermail.org", ready.email)
        assertTrue(ready.allow_profile_picture)
        assertTrue(ready.aster_domain)
    }
}
