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
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.astermail.android.network.connection_route
import org.astermail.android.ui.settings.detail.profile_picture_shown_view
import org.astermail.android.ui.settings.detail.profile_picture_slide_sign
import org.astermail.android.ui.settings.detail.profile_picture_view
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ProfilePictureGalleryTest {
    private lateinit var server: MockWebServer
    private var available = true

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        available = true
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    private fun gallery(): ProfilePictureGallery = ProfilePictureGallery(
        base_url = server.url("/").toString().trimEnd('/'),
        is_available = { available },
    )

    private fun manifest_response(body: String) =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun parses_valid_manifest() {
        val items = parse_gallery_manifest(
            """{"items":[{"slug":"andromeda-01","category":"space"},{"slug":"fjord_2","category":"water"}]}""",
        )
        assertEquals(
            listOf(GalleryItem("andromeda-01", "space"), GalleryItem("fjord_2", "water")),
            items,
        )
    }

    @Test
    fun invalid_or_unexpected_payloads_yield_empty() {
        assertTrue(parse_gallery_manifest("not json").isEmpty())
        assertTrue(parse_gallery_manifest("[]").isEmpty())
        assertTrue(parse_gallery_manifest("{}").isEmpty())
        assertTrue(parse_gallery_manifest("""{"items":{}}""").isEmpty())
        assertTrue(parse_gallery_manifest("""{"items":"x"}""").isEmpty())
    }

    @Test
    fun rejects_bad_slugs_unknown_categories_and_non_strings() {
        val items = parse_gallery_manifest(
            """
            {"items":[
              {"slug":"../etc/passwd","category":"space"},
              {"slug":"Upper","category":"space"},
              {"slug":"-lead","category":"space"},
              {"slug":"a/b","category":"space"},
              {"slug":"ok","category":"people"},
              {"slug":42,"category":"space"},
              {"slug":"num","category":7},
              {"category":"space"},
              "string",
              {"slug":"good","category":"desert"}
            ]}
            """.trimIndent(),
        )
        assertEquals(listOf(GalleryItem("good", "desert")), items)
    }

    @Test
    fun slug_length_limit() {
        assertTrue(is_gallery_slug("a" + "b".repeat(80)))
        assertFalse(is_gallery_slug("a" + "b".repeat(81)))
        assertFalse(is_gallery_slug(""))
    }

    @Test
    fun dedupes_by_slug_keeping_first() {
        val items = parse_gallery_manifest(
            """{"items":[{"slug":"x","category":"space"},{"slug":"x","category":"ocean"},{"slug":"y","category":"ocean"}]}""",
        )
        assertEquals(listOf(GalleryItem("x", "space"), GalleryItem("y", "ocean")), items)
    }

    @Test
    fun caps_at_two_thousand_items() {
        val entries = (0 until 2500).joinToString(",") { """{"slug":"s$it","category":"forest"}""" }
        val items = parse_gallery_manifest("""{"items":[$entries]}""")
        assertEquals(2000, items.size)
        assertEquals("s1999", items.last().slug)
    }

    @Test
    fun categories_present_in_canonical_order() {
        val items = listOf(
            GalleryItem("a", "desert"),
            GalleryItem("b", "space"),
            GalleryItem("c", "aurora"),
            GalleryItem("d", "space"),
        )
        assertEquals(listOf("space", "aurora", "desert"), gallery_categories_present(items))
    }

    @Test
    fun gallery_only_available_on_direct_connection_without_lockdown() {
        assertTrue(is_profile_picture_gallery_available(connection_route.direct, false))
        assertFalse(is_profile_picture_gallery_available(connection_route.direct, true))
        assertFalse(is_profile_picture_gallery_available(connection_route.routed, false))
        assertFalse(is_profile_picture_gallery_available(connection_route.routed, true))
    }

    @Test
    fun shown_view_falls_back_to_main_when_unavailable() {
        assertEquals(profile_picture_view.gallery, profile_picture_shown_view(profile_picture_view.gallery, true))
        assertEquals(profile_picture_view.main, profile_picture_shown_view(profile_picture_view.gallery, false))
        assertEquals(profile_picture_view.main, profile_picture_shown_view(profile_picture_view.main, true))
    }

    @Test
    fun slide_direction_is_mirrored_in_rtl() {
        assertEquals(1, profile_picture_slide_sign(profile_picture_view.gallery, rtl = false))
        assertEquals(-1, profile_picture_slide_sign(profile_picture_view.main, rtl = false))
        assertEquals(-1, profile_picture_slide_sign(profile_picture_view.gallery, rtl = true))
        assertEquals(1, profile_picture_slide_sign(profile_picture_view.main, rtl = true))
    }

    @Test
    fun routed_connection_makes_zero_requests() = runBlocking {
        available = false
        val gallery = gallery()
        try {
            gallery.load_manifest()
            fail("expected unavailable")
        } catch (_: GalleryUnavailableException) {
        }
        try {
            gallery.fetch_image("andromeda")
            fail("expected unavailable")
        } catch (_: GalleryUnavailableException) {
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun manifest_is_cached_after_success_and_sends_no_identifying_headers() = runBlocking {
        server.enqueue(manifest_response("""{"items":[{"slug":"a","category":"space"}]}"""))
        val gallery = gallery()
        val first = gallery.load_manifest()
        val second = gallery.load_manifest()
        assertEquals(first, second)
        assertEquals(1, server.requestCount)
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/manifest.json", request.path)
        assertNull(request.getHeader("Cookie"))
        assertNull(request.getHeader("Referer"))
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun failure_leaves_no_cache_so_retry_fetches_again() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(manifest_response("""{"items":[{"slug":"a","category":"space"}]}"""))
        val gallery = gallery()
        try {
            gallery.load_manifest()
            fail("expected failure")
        } catch (_: IOException) {
        }
        assertEquals(listOf(GalleryItem("a", "space")), gallery.load_manifest())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun empty_manifest_counts_as_failure() = runBlocking {
        server.enqueue(manifest_response("""{"items":[]}"""))
        server.enqueue(manifest_response("""{"items":[{"slug":"b","category":"ocean"}]}"""))
        val gallery = gallery()
        try {
            gallery.load_manifest()
            fail("expected failure")
        } catch (_: IOException) {
        }
        assertEquals(listOf(GalleryItem("b", "ocean")), gallery.load_manifest())
    }

    @Test
    fun clear_cache_forces_refetch() = runBlocking {
        server.enqueue(manifest_response("""{"items":[{"slug":"a","category":"space"}]}"""))
        server.enqueue(manifest_response("""{"items":[{"slug":"b","category":"space"}]}"""))
        val gallery = gallery()
        gallery.load_manifest()
        gallery.clear_cache()
        assertEquals(listOf(GalleryItem("b", "space")), gallery.load_manifest())
    }

    @Test
    fun fetch_image_reads_thumb_path() = runBlocking {
        val bytes = byteArrayOf(1, 2, 3, 4)
        server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(bytes)))
        val result = gallery().fetch_image("aurora-7")
        assertArrayEquals(bytes, result)
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/thumb/aurora-7.webp", request.path)
        assertNull(request.getHeader("Cookie"))
        assertNull(request.getHeader("Referer"))
    }

    @Test
    fun fetch_image_rejects_bad_slug_without_request() = runBlocking {
        try {
            gallery().fetch_image("../manifest")
            fail("expected rejection")
        } catch (_: IOException) {
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun redirects_are_not_followed() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://example.invalid/x"))
        try {
            gallery().load_manifest()
            fail("expected failure")
        } catch (_: IOException) {
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun thumb_url_shape() {
        assertEquals("https://aster-wallpapers.pages.dev/thumb/x.webp", gallery_thumb_url("x"))
    }
}
