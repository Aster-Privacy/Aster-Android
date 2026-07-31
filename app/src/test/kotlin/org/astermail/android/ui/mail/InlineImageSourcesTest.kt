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

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.astermail.android.mail.MailRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InlineImageSourcesTest {

    @Before
    fun setup() {
        mockkObject(MailRepository.Companion)
        every {
            MailRepository.decrypt_attachment_bytes(any(), any(), any())
        } answers { ByteArray(firstArg<String>().length) }
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun attachment(
        id: String,
        content_id: String?,
        content_type: String = "image/png",
        data: String? = "abcd",
        nonce: String? = "nonce",
    ) = MessageAttachment(
        id = id,
        filename = "$id.png",
        content_type = content_type,
        size_bytes = 4,
        encrypted_data = data,
        data_nonce = nonce,
        session_key = "key",
        content_id = content_id,
    )

    private fun expected_url(content_id: String, size: Int): String =
        InlineImageStore.url_for(InlineImageStore.content_key(content_id, ByteArray(size)))

    @Test
    fun resolves_referenced_image_attachment() {
        val out = inline_image_sources(
            """<img src="cid:logo@aster">""",
            listOf(attachment("a", "<logo@aster>")),
        )
        assertEquals(mapOf("logo@aster" to expected_url("logo@aster", 4)), out)
    }

    @Test
    fun serves_the_decrypted_bytes_from_the_store_instead_of_a_data_uri() {
        val out = inline_image_sources(
            """<img src="cid:logo@aster">""",
            listOf(attachment("a", "logo@aster")),
        )
        val src = out.getValue("logo@aster")
        assertFalse("inline images must not be embedded as base64", src.startsWith("data:"))
        val entry = InlineImageStore.entry_for_source(src)
        assertNotNull("the store must serve the inline image", entry)
        assertEquals("image/png", entry!!.content_type)
        assertEquals(4, entry.bytes.size)
    }

    @Test
    fun inlines_a_high_resolution_image_that_the_old_four_megabyte_cap_rejected() {
        every {
            MailRepository.decrypt_attachment_bytes(any(), any(), any())
        } returns ByteArray(9 * 1024 * 1024)
        val out = inline_image_sources(
            """<img src="cid:photo">""",
            listOf(attachment("a", "photo", content_type = "image/jpeg")),
        )
        val src = out.getValue("photo")
        assertEquals(9 * 1024 * 1024, InlineImageStore.entry_for_source(src)?.bytes?.size)
    }

    @Test
    fun skips_attachment_not_referenced_by_body() {
        val out = inline_image_sources(
            """<p>no images here</p>""",
            listOf(attachment("a", "logo")),
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun skips_non_image_content_types() {
        val out = inline_image_sources(
            """<img src="cid:doc">""",
            listOf(attachment("a", "doc", content_type = "application/pdf")),
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun skips_attachment_without_ciphertext() {
        val out = inline_image_sources(
            """<img src="cid:logo">""",
            listOf(attachment("a", "logo", data = null)),
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun skips_attachment_when_decrypt_fails() {
        every {
            MailRepository.decrypt_attachment_bytes(any(), any(), any())
        } throws IllegalStateException("bad key")
        val out = inline_image_sources(
            """<img src="cid:logo">""",
            listOf(attachment("a", "logo")),
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun skips_images_over_the_per_image_cap() {
        every {
            MailRepository.decrypt_attachment_bytes(any(), any(), any())
        } returns ByteArray(17 * 1024 * 1024)
        val out = inline_image_sources(
            """<img src="cid:big">""",
            listOf(attachment("a", "big")),
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun stops_inlining_once_the_total_budget_is_spent() {
        every {
            MailRepository.decrypt_attachment_bytes(any(), any(), any())
        } returns ByteArray(12 * 1024 * 1024)
        val body = """<img src="cid:a"><img src="cid:b"><img src="cid:c"><img src="cid:d">"""
        val out = inline_image_sources(
            body,
            listOf(
                attachment("1", "a"),
                attachment("2", "b"),
                attachment("3", "c"),
                attachment("4", "d"),
            ),
        )
        assertEquals(2, out.size)
        assertFalse(out.containsKey("c"))
        assertFalse(out.containsKey("d"))
    }

    @Test
    fun resolves_quoted_image_from_another_thread_message() {
        val own = listOf(attachment("1", "reply_logo"))
        val siblings = listOf(attachment("2", "<quoted_logo@aster>"))
        val out = inline_image_sources(
            """<img src="cid:quoted_logo@aster">""",
            own + siblings,
        )
        assertEquals(mapOf("quoted_logo@aster" to expected_url("quoted_logo@aster", 4)), out)
    }

    @Test
    fun own_attachment_wins_over_a_sibling_with_the_same_content_id() {
        val own = listOf(attachment("1", "shared", data = "abcd"))
        val siblings = listOf(attachment("2", "shared", data = "abcdefgh"))
        val out = inline_image_sources("""<img src="cid:shared">""", own + siblings)
        assertEquals(mapOf("shared" to expected_url("shared", 4)), out)
    }

    @Test
    fun keeps_the_first_attachment_when_content_ids_collide() {
        val out = inline_image_sources(
            """<img src="cid:dup">""",
            listOf(attachment("1", "dup"), attachment("2", "dup", data = "abcdefgh")),
        )
        assertEquals(mapOf("dup" to expected_url("dup", 4)), out)
    }

    @Test
    fun inline_sources_survive_the_remote_image_blocker_and_the_proxy_rewriter() {
        val src = InlineImageStore.url_for("deadbeef")
        val html = """<img src="$src"><img src="https://cdn.example/banner.png" width="600" height="200">"""
        val blocked = EmailHtmlSanitizer.replace_blocked_images(html, "blocked")
        assertTrue("inline images must never be blocked as remote", blocked.contains(src))
        assertTrue(blocked.contains("blocked-image"))
        val proxied = proxy_external_urls(html, "https://app.astermail.org/api/images/v1/proxy?url=")
        assertTrue("inline images must not be routed through the image proxy", proxied.contains(src))
    }

    @Test
    fun the_content_policy_allows_the_inline_image_host() {
        val document = build_email_html(
            body = """<img src="${InlineImageStore.url_for("deadbeef")}">""",
            is_dark = false,
            fg_hex = "#111827",
            link_hex = "#2563eb",
            forwarded_label = "Forwarded message",
            image_failed_label = "Image unavailable",
            force_dark_emails = false,
            dyslexia_font = false,
            translate_mode = "off",
        )
        val policy = document.substringAfter("Content-Security-Policy\" content=\"").substringBefore("\"")
        assertTrue("the inline image host must be allowed by img-src: $policy", policy.contains("img-src https://app.astermail.org https://mail-content.invalid"))
    }
}
