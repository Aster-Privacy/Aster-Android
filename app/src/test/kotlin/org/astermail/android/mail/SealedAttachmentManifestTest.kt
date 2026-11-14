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

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class SealedAttachmentManifestTest {

    private val item = "item-sealed"
    private val sealed_key = ByteArray(32) { (it + 3).toByte() }
    private val nonce = ByteArray(12) { (it + 9).toByte() }
    private val payload = "quarterly figures".toByteArray(Charsets.UTF_8)

    @Before
    fun setup() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        InboundAttachmentKeyStore.clear()
    }

    @After
    fun teardown() {
        InboundAttachmentKeyStore.clear()
        unmockkAll()
    }

    private fun b64(bytes: ByteArray) = java.util.Base64.getEncoder().encodeToString(bytes)

    private fun seal(plaintext: ByteArray, key: ByteArray, aad: ByteArray? = null): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    private fun sha256_hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun manifest_entry(
        seq: Int,
        key: ByteArray = sealed_key,
        content: ByteArray = payload,
        size: Long = content.size.toLong(),
        digest: String = sha256_hex(content),
    ): JSONObject = JSONObject().apply {
        put("seq", seq)
        put("key", b64(key))
        put("size", size)
        put("sha256", digest.uppercase())
        put("filename", "figures.txt")
        put("content_type", "text/plain")
        put("is_inline", false)
    }

    private fun open(ciphertext: ByteArray, seq: Int = 0, session_key: String = ""): ByteArray =
        MailRepository.decrypt_attachment_bytes(b64(ciphertext), b64(nonce), session_key, item, seq)

    @Test
    fun opens_a_listed_attachment_and_ignores_the_envelope_key() {
        assertTrue(InboundAttachmentKeyStore.register_sealed_manifest(item, JSONArray().put(manifest_entry(0))))
        InboundAttachmentKeyStore.register(item, 0, b64(ByteArray(32) { 1 }))

        val entry = InboundAttachmentKeyStore.entry(item, 0)!!
        assertTrue(entry.sealed)
        assertEquals(b64(sealed_key), entry.key)
        assertEquals(sha256_hex(payload), entry.sha256)
        assertEquals("figures.txt", entry.filename)
        assertArrayEquals(payload, open(seal(payload, sealed_key), session_key = b64(ByteArray(32) { 1 })))
    }

    @Test
    fun opens_an_attachment_bound_to_its_position() {
        InboundAttachmentKeyStore.register_sealed_manifest(item, JSONArray().put(manifest_entry(2)))

        val bound = seal(payload, sealed_key, MailRepository.attachment_data_aad(2))

        assertArrayEquals(payload, open(bound, seq = 2))
    }

    @Test
    fun hides_and_refuses_an_unlisted_attachment() {
        InboundAttachmentKeyStore.register_sealed_manifest(item, JSONArray().put(manifest_entry(0)))

        assertTrue(InboundAttachmentKeyStore.is_row_listed(item, 0))
        assertFalse(InboundAttachmentKeyStore.is_row_listed(item, 1))
        assertTrue(InboundAttachmentKeyStore.is_row_listed("other-item", 1))
        try {
            open(seal(payload, sealed_key), seq = 1)
            fail("expected AttachmentKeyUnavailableException")
        } catch (_: AttachmentKeyUnavailableException) {
        }
    }

    @Test
    fun refuses_swapped_content_under_the_same_key() {
        InboundAttachmentKeyStore.register_sealed_manifest(item, JSONArray().put(manifest_entry(0)))

        val swapped = "quarterly figureZ".toByteArray(Charsets.UTF_8)
        try {
            open(seal(swapped, sealed_key))
            fail("expected AttachmentIntegrityException")
        } catch (_: AttachmentIntegrityException) {
        }
    }

    @Test
    fun refuses_content_with_a_different_size() {
        InboundAttachmentKeyStore.register_sealed_manifest(
            item,
            JSONArray().put(manifest_entry(0, size = payload.size.toLong() + 1)),
        )

        try {
            open(seal(payload, sealed_key))
            fail("expected AttachmentIntegrityException")
        } catch (_: AttachmentIntegrityException) {
        }
    }

    @Test
    fun refuses_content_sealed_with_another_key() {
        InboundAttachmentKeyStore.register_sealed_manifest(item, JSONArray().put(manifest_entry(0)))

        try {
            open(seal(payload, ByteArray(32) { 7 }))
            fail("expected AttachmentIntegrityException")
        } catch (_: AttachmentIntegrityException) {
        }
    }

    @Test
    fun ignores_a_malformed_manifest() {
        val bad_seq = JSONArray().put(manifest_entry(0)).put(JSONObject().put("seq", "1").put("key", "abc"))
        val no_key = JSONArray().put(JSONObject().put("seq", 0))
        val fraction = JSONArray().put(JSONObject().put("seq", 0.5).put("key", "abc"))

        assertFalse(InboundAttachmentKeyStore.register_sealed_manifest(item, bad_seq))
        assertFalse(InboundAttachmentKeyStore.register_sealed_manifest(item, no_key))
        assertFalse(InboundAttachmentKeyStore.register_sealed_manifest(item, fraction))
        assertFalse(InboundAttachmentKeyStore.register_sealed_manifest(item, JSONArray()))
        assertFalse(InboundAttachmentKeyStore.register_sealed_manifest(null, JSONArray().put(manifest_entry(0))))
        assertFalse(InboundAttachmentKeyStore.is_sealed(item))

        InboundAttachmentKeyStore.register(item, 0, b64(sealed_key))
        assertArrayEquals(payload, open(seal(payload, sealed_key)))
    }

    @Test
    fun clear_forgets_sealed_manifests() {
        InboundAttachmentKeyStore.register_sealed_manifest(item, JSONArray().put(manifest_entry(0)))
        InboundAttachmentKeyStore.clear()

        assertFalse(InboundAttachmentKeyStore.is_sealed(item))
        assertNull(InboundAttachmentKeyStore.entry(item, 0))
    }

    @Test
    fun bundle_parser_keeps_only_the_outer_manifest() {
        val inner_manifest = JSONArray().put(manifest_entry(5))
        val inner = ASTER_SUBJECT_BUNDLE_PREFIX + JSONObject().apply {
            put("s", "inner")
            put("b", "body")
            put("attachment_manifest", inner_manifest)
        }.toString()
        val outer_manifest = JSONArray().put(manifest_entry(0))
        val outer = ASTER_SUBJECT_BUNDLE_PREFIX + JSONObject().apply {
            put("s", "outer")
            put("b", inner)
            put("attachment_manifest", outer_manifest)
        }.toString()
        val bare_outer = ASTER_SUBJECT_BUNDLE_PREFIX + JSONObject().apply {
            put("s", "outer")
            put("b", inner)
        }.toString()

        val parsed = extract_subject_bundle(outer)
        assertEquals("body", parsed.body)
        assertEquals(1, parsed.attachment_manifest!!.length())
        assertEquals(0, parsed.attachment_manifest!!.getJSONObject(0).getInt("seq"))
        assertNull(extract_subject_bundle(bare_outer).attachment_manifest)
    }
}
