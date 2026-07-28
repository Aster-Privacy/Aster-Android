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
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.astermail.android.mail.MailRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InlineImageDataUrisTest {

    @Before
    fun setup() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            "B64_" + (firstArg<ByteArray>().size)
        }
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

    @Test
    fun resolves_referenced_image_attachment() {
        val out = inline_image_data_uris(
            """<img src="cid:logo@aster">""",
            listOf(attachment("a", "<logo@aster>")),
        )
        assertEquals(mapOf("logo@aster" to "data:image/png;base64,B64_4"), out)
    }

    @Test
    fun skips_attachment_not_referenced_by_body() {
        val out = inline_image_data_uris(
            """<p>no images here</p>""",
            listOf(attachment("a", "logo")),
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun skips_non_image_content_types() {
        val out = inline_image_data_uris(
            """<img src="cid:doc">""",
            listOf(attachment("a", "doc", content_type = "application/pdf")),
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun skips_attachment_without_ciphertext() {
        val out = inline_image_data_uris(
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
        val out = inline_image_data_uris(
            """<img src="cid:logo">""",
            listOf(attachment("a", "logo")),
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun skips_images_over_the_per_image_cap() {
        every {
            MailRepository.decrypt_attachment_bytes(any(), any(), any())
        } returns ByteArray(5 * 1024 * 1024)
        val out = inline_image_data_uris(
            """<img src="cid:big">""",
            listOf(attachment("a", "big")),
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun stops_inlining_once_the_total_budget_is_spent() {
        every {
            MailRepository.decrypt_attachment_bytes(any(), any(), any())
        } returns ByteArray(4 * 1024 * 1024)
        val body = """<img src="cid:a"><img src="cid:b"><img src="cid:c"><img src="cid:d">"""
        val out = inline_image_data_uris(
            body,
            listOf(
                attachment("1", "a"),
                attachment("2", "b"),
                attachment("3", "c"),
                attachment("4", "d"),
            ),
        )
        assertEquals(3, out.size)
        assertFalse(out.containsKey("d"))
    }

    @Test
    fun keeps_the_first_attachment_when_content_ids_collide() {
        val out = inline_image_data_uris(
            """<img src="cid:dup">""",
            listOf(attachment("1", "dup"), attachment("2", "dup", data = "abcdefgh")),
        )
        assertEquals(mapOf("dup" to "data:image/png;base64,B64_4"), out)
    }
}
