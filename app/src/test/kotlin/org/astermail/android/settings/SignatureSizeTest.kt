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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SignatureSizeTest {

    private fun data_image(bytes: Int): String =
        "data:image/png;base64," + Base64.getEncoder().encodeToString(ByteArray(bytes) { (it % 251).toByte() })

    private val shrinking_encoder = SignatureImageEncoder { _, level ->
        SignatureImage("jpeg", ByteArray(level.max_dimension * 10))
    }

    private val failing_encoder = SignatureImageEncoder { _, _ -> null }

    @Test
    fun `the plaintext limit leaves room for the encryption tag`() {
        assertEquals(65_520, SIGNATURE_MAX_PLAINTEXT_BYTES)
        assertTrue(signature_fits("a".repeat(65_520)))
        assertTrue(!signature_fits("a".repeat(65_521)))
    }

    @Test
    fun `a small signature is left unchanged`() {
        val content = "<p>Best regards</p><img src=\"${data_image(1_000)}\">"
        assertEquals(content, fit_signature_content(content, shrinking_encoder))
    }

    @Test
    fun `a large image is recompressed until the signature fits the target`() {
        val content = "<p>Best regards</p><img src=\"${data_image(200_000)}\">"
        val fitted = fit_signature_content(content, shrinking_encoder)!!

        assertTrue(signature_byte_size(fitted) <= SIGNATURE_TARGET_BYTES)
        assertTrue(fitted.startsWith("<p>Best regards</p><img src=\"data:image/jpeg;base64,"))
        assertTrue(fitted.endsWith("\">"))
    }

    @Test
    fun `an image that cannot be decoded makes an oversized signature fail`() {
        val content = "<img src=\"${data_image(200_000)}\">"
        assertNull(fit_signature_content(content, failing_encoder))
    }

    @Test
    fun `oversized text without images fails`() {
        assertNull(fit_signature_content("a".repeat(70_000), shrinking_encoder))
    }

    @Test
    fun `text between the target and the limit is kept`() {
        val content = "a".repeat(60_000)
        assertEquals(content, fit_signature_content(content, failing_encoder))
    }

    @Test
    fun `several images are all recompressed`() {
        val content = "<img src=\"${data_image(40_000)}\"><img src=\"${data_image(40_000)}\">"
        val fitted = fit_signature_content(content, shrinking_encoder)!!

        assertTrue(signature_byte_size(fitted) <= SIGNATURE_TARGET_BYTES)
        assertEquals(2, Regex("data:image/jpeg").findAll(fitted).count())
    }
}
