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

import android.util.Base64
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.astermail.android.api.send.ExternalAttachmentPayload
import org.astermail.android.crypto.PgpEncryptor
import org.astermail.android.crypto.PgpKeyGenerator
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DraftAttachmentsTest {

    @Before
    fun setup() {
        mockkStatic(Base64::class)
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
    }

    @After
    fun teardown() {
        unmockkStatic(Base64::class)
    }

    private fun payload(name: String, bytes: ByteArray, type: String = "application/pdf") =
        ExternalAttachmentPayload(
            data = java.util.Base64.getEncoder().encodeToString(bytes),
            filename = name,
            content_type = type,
            size_bytes = bytes.size.toLong(),
        )

    @Test
    fun `draft attachments round trip through the envelope json`() {
        val bytes = ByteArray(2048) { (it % 199).toByte() }
        val original = listOf(payload("report.pdf", bytes), payload("photo.jpg", byteArrayOf(1, 2, 3), "image/jpeg"))
        val envelope = org.json.JSONObject().apply {
            put("subject", "Hi")
            put(DRAFT_ATTACHMENTS_KEY, draft_attachments_json(original))
        }.toString()

        val restored = parse_draft_attachments(envelope)

        assertEquals(2, restored.size)
        assertEquals("report.pdf", restored[0].filename)
        assertEquals("application/pdf", restored[0].content_type)
        assertEquals(2048L, restored[0].size_bytes)
        assertArrayEquals(bytes, java.util.Base64.getDecoder().decode(restored[0].data))
        assertEquals("photo.jpg", restored[1].filename)
        assertEquals("image/jpeg", restored[1].content_type)
    }

    @Test
    fun `draft attachments use the web draft field names`() {
        val entry = draft_attachments_json(listOf(payload("a.txt", byteArrayOf(65), "text/plain")))
            .getJSONObject(0)

        listOf("id", "name", "size", "size_bytes", "mime_type", "data_base64").forEach { key ->
            assertTrue("missing $key", entry.has(key))
        }
        assertEquals("a.txt", entry.getString("name"))
        assertEquals("text/plain", entry.getString("mime_type"))
    }

    @Test
    fun `entries without data are skipped and missing fields get defaults`() {
        val envelope = """
            {"attachments":[
              {"id":"1","name":"empty.txt","mime_type":"text/plain"},
              {"id":"2","filename":"legacy.bin","data_base64":"AAECAw=="}
            ]}
        """.trimIndent()

        val restored = parse_draft_attachments(envelope)

        assertEquals(1, restored.size)
        assertEquals("legacy.bin", restored[0].filename)
        assertEquals("application/octet-stream", restored[0].content_type)
        assertEquals(6L, restored[0].size_bytes)
    }

    @Test
    fun `an envelope without attachments or with broken json restores nothing`() {
        assertTrue(parse_draft_attachments("{\"subject\":\"x\"}").isEmpty())
        assertTrue(parse_draft_attachments("not json").isEmpty())
    }

    @Test
    fun `draft size checks respect the backend count and byte limits`() {
        assertTrue(draft_attachments_may_fit(listOf(payload("a", ByteArray(10)))))
        val too_many = (0..DRAFT_MAX_ATTACHMENT_COUNT).map { payload("f$it", ByteArray(1)) }
        assertFalse(draft_attachments_may_fit(too_many))
        val huge = ExternalAttachmentPayload(
            data = "A".repeat(DRAFT_MAX_SIZE_BYTES.toInt()),
            filename = "huge.bin",
            content_type = "application/octet-stream",
            size_bytes = DRAFT_MAX_SIZE_BYTES,
        )
        assertFalse(draft_attachments_may_fit(listOf(huge)))
        assertTrue(draft_envelope_fits("{\"subject\":\"small\"}"))
        assertTrue(encrypted_draft_length(0L) > 0L)
    }

    @Test
    fun `a public key derived from the identity key can encrypt`() {
        val keys = PgpKeyGenerator.generate("Me", "me@astermail.org", "pw".toCharArray())

        val derived = armored_public_key_from_private(keys.armored_private_key)

        assertNotNull(derived)
        assertTrue(derived!!.contains("BEGIN PGP PUBLIC KEY"))
        assertNotNull(PgpEncryptor.encrypt_to_keys("{\"filename\":\"a.txt\"}", listOf(derived)))
    }

    @Test
    fun `non pgp identity material yields no public key`() {
        assertNull(armored_public_key_from_private(null))
        assertNull(armored_public_key_from_private("test_identity_key"))
        assertNull(armored_public_key_from_private("-----BEGIN PGP PRIVATE KEY BLOCK-----\ngarbage\n"))
    }

    @Test
    fun `materialized draft attachments land on disk with their bytes`() {
        val dir = java.nio.file.Files.createTempDirectory("draft_att").toFile()
        val bytes = ByteArray(300) { it.toByte() }

        val files = materialize_draft_attachments(java.io.File(dir, "d1"), listOf(payload("../evil/name.pdf", bytes)))

        assertEquals(1, files.size)
        val file = java.io.File(files[0].path)
        assertEquals(java.io.File(dir, "d1").canonicalPath, file.parentFile!!.canonicalPath)
        assertArrayEquals(bytes, file.readBytes())
        assertEquals("../evil/name.pdf", files[0].name)
        assertEquals(300L, files[0].size_bytes)
        dir.deleteRecursively()
    }
}
