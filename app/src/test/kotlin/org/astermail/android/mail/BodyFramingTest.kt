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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyFramingTest {

    private val framings = listOf(
        "",
        "\u0000",
        "\u0000\u0000",
        " ",
        "\n\n",
        "\t",
        "\u00a0",
        "\ufeff",
        "\u200b",
        "\u200e",
        "\u2060",
        "\u3000",
        "\u00ad",
        "\u001f",
        "\ufeff \u0000\n",
    )

    @Test
    fun strips_every_framing_prefix() {
        for (framing in framings) {
            assertEquals("payload", strip_body_framing(framing + "payload"))
        }
    }

    @Test
    fun keeps_inner_content_intact() {
        assertEquals("a\u0000b", strip_body_framing("\ufeffa\u0000b"))
        assertEquals("hello world ", strip_body_framing("hello world "))
    }

    @Test
    fun detects_prefix_behind_framing() {
        for (framing in framings) {
            assertTrue(framing.length.toString(), body_starts_with(framing + "<html>", "<"))
            assertTrue(framing.length.toString(), body_starts_with(framing + "-----BEGIN PGP MESSAGE-----", "-----BEGIN PGP"))
            assertTrue(
                framing.length.toString(),
                body_starts_with(framing + "content-type: text/html", "Content-Type:", ignore_case = true),
            )
        }
        assertFalse(body_starts_with("x<html>", "<"))
    }

    @Test
    fun framing_only_check_matches_helper() {
        for (framing in framings) {
            assertTrue(is_body_framing_only(framing))
        }
        assertFalse(is_body_framing_only(" x "))
    }

    @Test
    fun mime_detection_survives_framing() {
        for (framing in framings) {
            assertTrue(MimeParser.looks_like_mime(framing + "MIME-Version: 1.0\r\nContent-Type: text/plain\r\n\r\nhi"))
        }
        assertFalse(MimeParser.looks_like_mime("hello"))
    }

    @Test
    fun subject_bundle_survives_framing() {
        for (framing in framings) {
            val bundle = extract_subject_bundle(framing + "ASTER_BUNDLE_V2{\"s\":\"hi\",\"b\":\"body\"}")
            assertEquals("hi", bundle.subject)
            assertEquals("body", bundle.body)
        }
    }

    @Test
    fun subject_bundle_ignores_marker_after_real_text() {
        val body = "hello ASTER_BUNDLE_V2{\"s\":\"hi\",\"b\":\"body\"}"
        val bundle = extract_subject_bundle(body)
        assertEquals(null, bundle.subject)
        assertEquals(body, bundle.body)
    }
}
