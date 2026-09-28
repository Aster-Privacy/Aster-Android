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

package org.astermail.android.mail

import org.astermail.android.api.send.ExternalAttachmentPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineImageCidsTest {

    private fun image(data: String, cid: String?) = ExternalAttachmentPayload(
        data = data,
        filename = "photo.png",
        content_type = "image/png",
        size_bytes = 3,
        content_id = cid,
    )

    @Test
    fun embedded_image_becomes_cid_reference() {
        val html = "<p>hi</p><img src=\"data:image/png;base64,QUJD\" alt=\"photo.png\" />"
        val result = with_cid_image_references(html, listOf(image("QUJD", "img_1@astermail.org")))
        assertEquals("<p>hi</p><img src=\"cid:img_1@astermail.org\" alt=\"photo.png\" />", result)
    }

    @Test
    fun regular_attachment_leaves_body_alone() {
        val html = "<img src=\"data:image/png;base64,QUJD\" />"
        assertEquals(html, with_cid_image_references(html, listOf(image("QUJD", null))))
    }

    @Test
    fun each_image_maps_to_its_own_cid() {
        val html = "<img src=\"data:image/png;base64,QUJD\" /><img src=\"data:image/png;base64,REVG\" />"
        val result = with_cid_image_references(
            html,
            listOf(image("QUJD", "img_a@astermail.org"), image("REVG", "img_b@astermail.org")),
        )
        assertEquals("<img src=\"cid:img_a@astermail.org\" /><img src=\"cid:img_b@astermail.org\" />", result)
    }

    @Test
    fun without_inline_images_keeps_only_regular_files() {
        val regular = image("QUJD", null)
        val inline = image("REVG", "img_b@astermail.org")
        assertEquals(listOf(regular), without_inline_images(listOf(regular, inline)))
        assertTrue(is_inline_image_payload(inline))
        assertFalse(is_inline_image_payload(regular))
    }

    @Test
    fun new_content_ids_are_unique_and_scoped() {
        val first = new_inline_image_content_id()
        val second = new_inline_image_content_id()
        assertTrue(first.startsWith("img_") && first.endsWith("@astermail.org"))
        assertFalse(first == second)
    }
}
